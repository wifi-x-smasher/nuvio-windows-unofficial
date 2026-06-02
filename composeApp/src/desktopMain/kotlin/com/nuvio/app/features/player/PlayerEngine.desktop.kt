package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.diagnostics.AppDiagnostics
import com.sun.jna.Native
import java.awt.Canvas
import java.awt.Color as AwtColor
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.swing.SwingUtilities
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val snapshotIntervalMs = 500L

@Composable
actual fun PlatformPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
    useYoutubeChunkedPlayback: Boolean,
    modifier: Modifier,
    playWhenReady: Boolean,
    resizeMode: PlayerResizeMode,
    useNativeController: Boolean,
    title: String,
    streamTitle: String,
    providerName: String,
    onBack: (() -> Unit)?,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    val latestOnSnapshot = rememberUpdatedState(onSnapshot)
    val latestOnError = rememberUpdatedState(onError)
    val uiScope = rememberCoroutineScope()
    var canvas by remember { mutableStateOf<Canvas?>(null) }
    var controller by remember { mutableStateOf<DesktopMpvPlayerController?>(null) }

    LaunchedEffect(canvas) {
        val activeCanvas = canvas ?: return@LaunchedEffect
        AppDiagnostics.breadcrumb("player.mpv.surface.wait", emptyMap())
        val windowHandle = activeCanvas.awaitNativeHandle()
        AppDiagnostics.breadcrumb(
            event = "player.mpv.surface.ready",
            details = mapOf("windowHandle" to windowHandle.toString()),
        )
        if (windowHandle == 0L) {
            latestOnError.value("Playback failed: MPV video surface was not created.")
            return@LaunchedEffect
        }
        val executable = DesktopMpvRuntime.executablePath()
        if (executable == null) {
            latestOnError.value(InternalPlayerPlatform.unavailableMessage())
            return@LaunchedEffect
        }
        controller = DesktopMpvPlayerController(
            executable = executable,
            windowHandle = windowHandle,
            onSnapshot = { snapshot -> uiScope.launch { latestOnSnapshot.value(snapshot) } },
            onError = { message -> uiScope.launch { latestOnError.value(message) } },
        )
    }

    LaunchedEffect(
        controller,
        sourceUrl,
        sourceAudioUrl,
        sourceHeaders,
        sourceResponseHeaders,
        useYoutubeChunkedPlayback,
        resizeMode,
    ) {
        val activeController = controller ?: return@LaunchedEffect
        onControllerReady(activeController)
        activeController.load(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            title = title,
            streamTitle = streamTitle,
            providerName = providerName,
            playWhenReady = playWhenReady,
            resizeMode = resizeMode,
        )
    }

    LaunchedEffect(controller, playWhenReady) {
        val activeController = controller ?: return@LaunchedEffect
        if (playWhenReady) {
            activeController.play()
        } else {
            activeController.pause()
        }
    }

    LaunchedEffect(controller) {
        val activeController = controller ?: return@LaunchedEffect
        while (isActive) {
            latestOnSnapshot.value(withContext(Dispatchers.IO) { activeController.snapshot() })
            delay(snapshotIntervalMs)
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller?.release()
        }
    }

    SwingPanel(
        modifier = modifier.background(Color.Black),
        factory = {
            Canvas().apply {
                background = AwtColor.BLACK
                isFocusable = false
                canvas = this
            }
        },
        background = Color.Black,
    )

    if (canvas == null) {
        Box(modifier = modifier.background(Color.Black))
    }
}

private class DesktopMpvPlayerController(
    private val executable: Path,
    private val windowHandle: Long,
    private val onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    private val onError: (String?) -> Unit,
    private val operationDispatcher: DesktopPlayerOperationDispatcher =
        ExecutorDesktopPlayerOperationDispatcher(),
) : PlayerEngineController {
    private val requestIds = AtomicLong(1)
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile
    private var process: Process? = null
    @Volatile
    private var ipcPath: String = ""
    @Volatile
    private var sourceUrl: String = ""
    @Volatile
    private var sourceHeaders: Map<String, String> = emptyMap()
    @Volatile
    private var title: String = ""
    @Volatile
    private var streamTitle: String = ""
    @Volatile
    private var providerName: String = ""
    @Volatile
    private var resizeMode: PlayerResizeMode = PlayerResizeMode.Fit
    @Volatile
    private var released = false

    fun load(
        sourceUrl: String,
        sourceAudioUrl: String?,
        sourceHeaders: Map<String, String>,
        title: String,
        streamTitle: String,
        providerName: String,
        playWhenReady: Boolean,
        resizeMode: PlayerResizeMode,
    ) {
        this.sourceUrl = sourceUrl
        this.sourceHeaders = sourceHeaders
        this.title = title
        this.streamTitle = streamTitle
        this.providerName = providerName
        this.resizeMode = resizeMode
        operationDispatcher.dispatch {
            if (released) return@dispatch
            runCatching {
                startProcess(sourceUrl, sourceHeaders, playWhenReady)
                sourceAudioUrl?.takeIf(String::isNotBlank)?.let {
                    runCommand("audio-add", JsonPrimitive(it), JsonPrimitive("auto"))
                }
                applyResizeMode(resizeMode)
            }.onFailure { throwable ->
                AppDiagnostics.error(
                    event = "player.mpv.load.failure",
                    throwable = throwable,
                    details = mapOf("sourceHost" to sourceUrl.substringBefore('?').take(120)),
                )
                onError("Playback failed: ${throwable.message ?: "MPV could not start."}")
            }
        }
    }

    override fun play() {
        operationDispatcher.dispatch { runCommand("set_property", JsonPrimitive("pause"), JsonPrimitive(false)) }
    }

    override fun pause() {
        operationDispatcher.dispatch { runCommand("set_property", JsonPrimitive("pause"), JsonPrimitive(true)) }
    }

    override fun seekTo(positionMs: Long) {
        operationDispatcher.dispatch {
            runCommand(
                "seek",
                JsonPrimitive((positionMs.coerceAtLeast(0L) / 1000.0)),
                JsonPrimitive("absolute"),
            )
        }
    }

    override fun seekBy(offsetMs: Long) {
        operationDispatcher.dispatch {
            runCommand(
                "seek",
                JsonPrimitive(offsetMs / 1000.0),
                JsonPrimitive("relative"),
            )
        }
    }

    override fun retry() {
        operationDispatcher.dispatch {
            startProcess(sourceUrl, sourceHeaders, playWhenReady = true)
            applyResizeMode(resizeMode)
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        operationDispatcher.dispatch {
            runCommand("set_property", JsonPrimitive("speed"), JsonPrimitive(speed.coerceIn(0.25f, 3f).toDouble()))
        }
    }

    override fun getAudioTracks(): List<AudioTrack> =
        trackList("audio").mapIndexed { index, track ->
            AudioTrack(
                index = index,
                id = track.id,
                label = track.title ?: track.language ?: "Track ${index + 1}",
                language = track.language,
                isSelected = track.selected,
            )
        }

    override fun getSubtitleTracks(): List<SubtitleTrack> =
        trackList("sub").mapIndexed { index, track ->
            SubtitleTrack(
                index = index,
                id = track.id,
                label = track.title ?: track.language ?: "Track ${index + 1}",
                language = track.language,
                isSelected = track.selected,
                isForced = track.forced,
            )
        }

    override fun selectAudioTrack(index: Int) {
        operationDispatcher.dispatch {
            getAudioTracks().getOrNull(index)?.let { runCommand("set_property", JsonPrimitive("aid"), JsonPrimitive(it.id)) }
        }
    }

    override fun selectSubtitleTrack(index: Int) {
        operationDispatcher.dispatch {
            getSubtitleTracks().getOrNull(index)?.let { runCommand("set_property", JsonPrimitive("sid"), JsonPrimitive(it.id)) }
        }
    }

    override fun setSubtitleUri(url: String) {
        operationDispatcher.dispatch {
            runCommand("sub-add", JsonPrimitive(url), JsonPrimitive("select"))
        }
    }

    override fun clearExternalSubtitle() {
        operationDispatcher.dispatch {
            runCommand("set_property", JsonPrimitive("sid"), JsonPrimitive("no"))
        }
    }

    override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        clearExternalSubtitle()
        selectSubtitleTrack(trackIndex)
    }

    fun snapshot(): PlayerPlaybackSnapshot {
        val durationMs = propertyDouble("duration").secondsToMs()
        val positionMs = propertyDouble("time-pos").secondsToMs()
        val paused = propertyBoolean("pause") ?: true
        val speed = propertyDouble("speed").takeIf { it > 0.0 }?.toFloat() ?: 1f
        val eof = propertyBoolean("eof-reached") ?: false
        return PlayerPlaybackSnapshot(
            isLoading = durationMs <= 0L && !eof,
            isPlaying = !paused && !eof,
            isEnded = eof || (durationMs > 0L && positionMs >= durationMs - 500L),
            durationMs = durationMs,
            positionMs = positionMs.coerceAtLeast(0L),
            bufferedPositionMs = durationMs,
            playbackSpeed = speed,
        )
    }

    fun release() {
        released = true
        operationDispatcher.dispatch {
            runCommand("quit")
            process?.destroy()
            process = null
        }
        operationDispatcher.close()
    }

    private fun startProcess(
        url: String,
        headers: Map<String, String>,
        playWhenReady: Boolean,
    ) {
        process?.destroy()
        val pipeName = "\\\\.\\pipe\\nuvio-mpv-${ProcessHandle.current().pid()}-${requestIds.getAndIncrement()}"
        ipcPath = pipeName
        val sanitizedHeaders = sanitizePlaybackHeaders(headers)
        val args = buildMpvArgs(
            url = url,
            headers = sanitizedHeaders,
            pipeName = pipeName,
            playWhenReady = playWhenReady,
        )
        AppDiagnostics.breadcrumb(
            event = "player.mpv.launch.start",
            details = mapOf(
                "executable" to executable.toString(),
                "windowHandle" to windowHandle.toString(),
                "sourceHost" to url.substringBefore('?').take(120),
            ),
        )
        process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()
        startProcessLogPump(process)
        if (!waitForIpc()) {
            val exit = process?.takeIf { !it.isAlive }?.exitValue()
            throw IllegalStateException(
                if (exit != null) {
                    "MPV exited before IPC became available (exit code $exit)."
                } else {
                    "MPV IPC did not become available."
                },
            )
        }
        onError(null)
        onSnapshot(snapshot())
        AppDiagnostics.breadcrumb("player.mpv.launch.success", emptyMap())
    }

    private fun buildMpvArgs(
        url: String,
        headers: Map<String, String>,
        pipeName: String,
        playWhenReady: Boolean,
    ): List<String> =
        buildList {
            add(executable.toString())
            add("--wid=$windowHandle")
            add("--input-ipc-server=$pipeName")
            add("--force-window=yes")
            add("--keep-open=yes")
            add("--vo=gpu")
            add("--gpu-context=auto")
            add("--no-terminal")
            add("--no-border")
            add("--no-window-dragging")
            add("--no-osc")
            add("--no-input-default-bindings")
            add("--no-osd-bar")
            add("--geometry=100%x100%")
            add("--autofit=100%x100%")
            add("--cache=yes")
            add("--cache-secs=120")
            add("--demuxer-max-bytes=150M")
            add("--demuxer-readahead-secs=10")
            add("--network-timeout=30")
            add("--hwdec=no")
            add("--pause=${if (playWhenReady) "no" else "yes"}")
            mediaTitle()?.let { add("--force-media-title=$it") }
            headers.findHeader("User-Agent")?.let { add("--user-agent=$it") }
            headers.findHeader("Referer", "Referrer")?.let { add("--referrer=$it") }
            headers.forEach { (key, value) ->
                if (!key.equals("User-Agent", ignoreCase = true) &&
                    !key.equals("Referer", ignoreCase = true) &&
                    !key.equals("Referrer", ignoreCase = true)
                ) {
                    add("--http-header-fields=$key: $value")
                }
            }
            if (!url.startsWith("magnet:", ignoreCase = true)) {
                add("--ytdl=no")
            }
            add(url)
        }

    private fun applyResizeMode(mode: PlayerResizeMode) {
        when (mode) {
            PlayerResizeMode.Fit -> {
                runCommand("set_property", JsonPrimitive("panscan"), JsonPrimitive(0))
                runCommand("set_property", JsonPrimitive("video-zoom"), JsonPrimitive(0))
            }

            PlayerResizeMode.Fill -> {
                runCommand("set_property", JsonPrimitive("panscan"), JsonPrimitive(1))
                runCommand("set_property", JsonPrimitive("video-zoom"), JsonPrimitive(0))
            }

            PlayerResizeMode.Zoom -> {
                runCommand("set_property", JsonPrimitive("panscan"), JsonPrimitive(0))
                runCommand("set_property", JsonPrimitive("video-zoom"), JsonPrimitive(0.25))
            }
        }
    }

    private fun trackList(type: String): List<MpvTrack> =
        property("track-list")
            ?.jsonArrayOrNull()
            ?.mapNotNull { element ->
                val obj = element.jsonObjectOrNull() ?: return@mapNotNull null
                val itemType = obj.stringValue("type")
                if (itemType != type) return@mapNotNull null
                MpvTrack(
                    id = obj["id"]?.jsonPrimitiveOrNull()?.contentOrNull ?: return@mapNotNull null,
                    title = obj.stringValue("title"),
                    language = obj.stringValue("lang"),
                    selected = obj.booleanValue("selected") ?: false,
                    forced = obj.booleanValue("forced") ?: false,
                )
            }
            ?: emptyList()

    private fun propertyDouble(name: String): Double =
        property(name)?.jsonPrimitiveOrNull()?.doubleOrNull ?: 0.0

    private fun propertyBoolean(name: String): Boolean? =
        property(name)?.jsonPrimitiveOrNull()?.booleanOrNull

    private fun property(name: String): JsonElement? =
        runCommand("get_property", JsonPrimitive(name))?.jsonObjectOrNull()?.get("data")

    private fun runCommand(command: String, vararg args: JsonElement): JsonObject? {
        if (ipcPath.isBlank()) return null
        return runCatching {
            val payload = buildJsonObject {
                put("command", buildJsonArray {
                    add(JsonPrimitive(command))
                    args.forEach(::add)
                })
                put("request_id", requestIds.getAndIncrement())
            }
            RandomAccessFile(File(ipcPath), "rw").use { pipe ->
                pipe.write((payload.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
                pipe.readLine()
                    ?.let { json.parseToJsonElement(it).jsonObjectOrNull() }
            }
        }.onFailure { throwable ->
            AppDiagnostics.error(
                event = "player.mpv.ipc.failure",
                throwable = throwable,
                details = mapOf("command" to command),
            )
        }.getOrNull()
    }

    private fun waitForIpc(): Boolean {
        repeat(50) {
            if (runCatching { RandomAccessFile(File(ipcPath), "rw").use { } }.isSuccess) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun startProcessLogPump(process: Process?) {
        val activeProcess = process ?: return
        Thread {
            runCatching {
                activeProcess.inputStream.bufferedReader().useLines { lines ->
                    lines.take(80).forEach { line ->
                        AppDiagnostics.breadcrumb(
                            event = "player.mpv.output",
                            details = mapOf("line" to line.take(220)),
                        )
                    }
                }
            }
        }.apply {
            name = "nuvio-mpv-output"
            isDaemon = true
            start()
        }
    }

    private fun mediaTitle(): String? =
        listOf(title, streamTitle, providerName)
            .mapNotNull { it.takeIf(String::isNotBlank) }
            .joinToString(" - ")
            .takeIf(String::isNotBlank)
}

private data class MpvTrack(
    val id: String,
    val title: String?,
    val language: String?,
    val selected: Boolean,
    val forced: Boolean,
)

private fun Canvas.awaitNativeHandle(): Long {
    repeat(40) {
        if (!isDisplayable) runOnSwingThread { addNotify() }
        val pointer = runOnSwingThread { runCatching { Native.getComponentPointer(this@awaitNativeHandle) }.getOrNull() }
        val value = pointer?.let { com.sun.jna.Pointer.nativeValue(it) } ?: 0L
        if (value != 0L) return value
        Thread.sleep(50)
    }
    return 0L
}

private fun Double.secondsToMs(): Long =
    if (this <= 0.0) 0L else (this * 1000.0).toLong()

private fun <T> runOnSwingThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var result: Result<T>? = null
    SwingUtilities.invokeAndWait {
        result = runCatching(block)
    }
    return result?.getOrThrow() ?: error("Swing operation did not complete")
}

private fun Map<String, String>.findHeader(vararg names: String): String? =
    entries.firstOrNull { (key, _) ->
        names.any { name -> key.equals(name, ignoreCase = true) }
    }?.value

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

private fun JsonObject.stringValue(key: String): String? =
    this[key]?.jsonPrimitiveOrNull()?.contentOrNull

private fun JsonObject.booleanValue(key: String): Boolean? =
    this[key]?.jsonPrimitiveOrNull()?.booleanOrNull
