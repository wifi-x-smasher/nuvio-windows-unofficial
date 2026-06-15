package com.nuvio.app.features.player.desktop.nativempv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.desktop.DesktopRuntimeLog
import com.nuvio.app.features.player.AudioTrack
import com.nuvio.app.features.player.PlayerAudioLevel
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.player.SubtitleStyleState
import com.nuvio.app.features.player.SubtitleTrack
import com.nuvio.app.features.player.desktop.DesktopPlayerBackend
import com.nuvio.app.features.player.desktop.DesktopPlayerError
import com.nuvio.app.features.player.desktop.DesktopPlayerPhase
import com.nuvio.app.features.player.desktop.DesktopPlayerRequest
import com.nuvio.app.features.player.desktop.DesktopPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong

internal class NativeMpvDesktopPlayerBackend private constructor(
    private val runtime: NativeMpvRuntimeResolution,
) : DesktopPlayerBackend {
    override val id: String = "native-mpv-${System.identityHashCode(this)}"
    override val backendName: String = "windows-native-mpv"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateFlow = MutableStateFlow(DesktopPlayerState(backendName = backendName))
    override val state: StateFlow<DesktopPlayerState> = stateFlow

    private val windowId = AtomicReference<ULong?>(null)
    private var pendingRequest: DesktopPlayerRequest? = null
    private var activeProcess: Process? = null
    private var activeIpc: NativeMpvIpcConnection? = null
    private var volumeFraction: Float = 1f
    private var muted = false

    override val controller: PlayerEngineController = object : PlayerEngineController {
        override fun play() {
            sendAsync(listOf("set_property", "pause", false))
            updatePhase(DesktopPlayerPhase.Playing)
        }

        override fun pause() {
            sendAsync(listOf("set_property", "pause", true))
            updatePhase(DesktopPlayerPhase.Paused)
        }

        override fun seekTo(positionMs: Long) {
            sendAsync(listOf("seek", positionMs / 1000.0, "absolute"))
            refreshPositionAsync()
        }

        override fun seekBy(offsetMs: Long) {
            sendAsync(listOf("seek", offsetMs / 1000.0, "relative"))
            refreshPositionAsync()
        }

        override fun retry() {
            pendingRequest?.let { request ->
                scope.launch { startPlayback(request) }
            }
        }

        override fun setPlaybackSpeed(speed: Float) {
            val safeSpeed = speed.coerceIn(0.25f, 4f)
            sendAsync(listOf("set_property", "speed", safeSpeed))
            stateFlow.value = stateFlow.value.copy(playbackSpeed = safeSpeed)
        }

        override fun currentVolume(): PlayerAudioLevel =
            PlayerAudioLevel(fraction = volumeFraction, isMuted = muted)

        override fun setVolume(level: Float): PlayerAudioLevel {
            val safeLevel = level.coerceIn(0f, 1f)
            volumeFraction = safeLevel
            muted = safeLevel <= 0.001f
            sendAsync(listOf("set_property", "volume", (safeLevel * 100f).roundToLong()))
            sendAsync(listOf("set_property", "mute", muted))
            return currentVolume()
        }

        override fun getAudioTracks(): List<AudioTrack> = emptyList()
        override fun getSubtitleTracks(): List<SubtitleTrack> = emptyList()
        override fun selectAudioTrack(index: Int) = sendAsync(listOf("set_property", "aid", index))
        override fun selectSubtitleTrack(index: Int) =
            sendAsync(listOf("set_property", "sid", if (index < 0) "no" else index))

        override fun setSubtitleUri(url: String) {
            sendAsync(listOf("sub-add", url, "select"))
        }

        override fun clearExternalSubtitle() {
            sendAsync(listOf("set_property", "sid", "no"))
        }

        override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
            selectSubtitleTrack(trackIndex)
        }

        override fun applySubtitleStyle(style: SubtitleStyleState) {
            sendAsync(listOf("set_property", "sub-font-size", style.fontSizeSp))
            sendAsync(listOf("set_property", "sub-color", style.textColor.toMpvAssColor()))
            sendAsync(listOf("set_property", "sub-border-size", if (style.outlineEnabled) 2 else 0))
        }

        override fun setSubtitleDelayMillis(delayMillis: Int) {
            sendAsync(listOf("set_property", "sub-delay", delayMillis / 1000.0))
        }

        override fun requestRedraw() {
            sendAsync(listOf("frame-step"))
            sendAsync(listOf("frame-back-step"))
        }
    }

    override suspend fun load(request: DesktopPlayerRequest) {
        pendingRequest = request
        stateFlow.value = DesktopPlayerState(
            phase = DesktopPlayerPhase.Preparing,
            backendName = backendName,
            diagnostics = "Starting native MPV process",
        )
        startPlayback(request)
    }

    override fun setResizeMode(resizeMode: PlayerResizeMode) {
        val mode = when (resizeMode) {
            PlayerResizeMode.Fit -> "no"
            PlayerResizeMode.Fill -> "yes"
            PlayerResizeMode.Zoom -> "yes"
        }
        sendAsync(listOf("set_property", "video-unscaled", "no"))
        sendAsync(listOf("set_property", "panscan", if (resizeMode == PlayerResizeMode.Zoom) 1.0 else 0.0))
        sendAsync(listOf("set_property", "keepaspect", mode))
    }

    override fun releaseSoft() {
        sendAsync(listOf("stop"))
        stateFlow.value = stateFlow.value.copy(phase = DesktopPlayerPhase.Idle)
    }

    override fun close() {
        scope.launch {
            stopProcess()
            scope.cancel()
        }
    }

    @Composable
    override fun Surface(modifier: Modifier) {
        NativeMpvVideoSurface(
            modifier = modifier,
            onWindowIdAvailable = { hwnd ->
                if (windowId.getAndSet(hwnd) == hwnd) return@NativeMpvVideoSurface
                pendingRequest?.let { request ->
                    scope.launch { startPlayback(request) }
                }
            },
        )
    }

    private suspend fun startPlayback(request: DesktopPlayerRequest) = withContext(Dispatchers.IO) {
        val hwnd = windowId.get()
        if (hwnd == null) {
            DesktopRuntimeLog.info("native-mpv waiting for surface session=${request.sessionKey}")
            return@withContext
        }
        val executable = runtime.executable
        if (executable == null) {
            setError("Native MPV executable is unavailable.", runtime.diagnostics)
            return@withContext
        }

        runCatching {
            stopProcess()
            val ipcPath = NativeMpvProcess.createIpcPath()
            val process = NativeMpvProcess.start(
                NativeMpvLaunchConfig(
                    mpvExecutable = executable,
                    ipcPath = ipcPath,
                    windowId = hwnd,
                    options = buildMpvOptions(request),
                ),
            )
            activeProcess = process
            val ipc = connectIpc(ipcPath)
            activeIpc = ipc
            ipc.client.sendCommand(listOf("loadfile", request.sourceUrl, "replace"))
            request.sourceAudioUrl?.takeIf { it.isNotBlank() }?.let { audioUrl ->
                ipc.client.sendCommand(listOf("audio-add", audioUrl, "select"))
            }
            if (!request.playWhenReady) {
                ipc.client.sendCommand(listOf("set_property", "pause", true))
            }
            refreshDuration(ipc.client)
            stateFlow.value = stateFlow.value.copy(
                phase = if (request.playWhenReady) DesktopPlayerPhase.Playing else DesktopPlayerPhase.Paused,
                diagnostics = "Native MPV process active",
                error = null,
            )
            DesktopRuntimeLog.info("native-mpv playback started session=${request.sessionKey} hwnd=$hwnd")
        }.onFailure { error ->
            DesktopRuntimeLog.error("native-mpv playback failed", error)
            setError(
                uiMessage = "Native MPV playback failed.",
                technicalMessage = error.message ?: error::class.simpleName.orEmpty(),
            )
            stopProcess()
        }
    }

    private fun buildMpvOptions(request: DesktopPlayerRequest): List<String> =
        buildList {
            add("--vo=gpu-next")
            add("--gpu-api=d3d11")
            add("--gpu-context=d3d11")
            add("--hwdec=auto-safe")
            add("--target-colorspace-hint=yes")
            add("--tone-mapping=auto")
            add("--video-sync=display-resample")
            add("--interpolation=no")
            add("--keep-open=no")
            add("--osd-level=0")
            add("--input-default-bindings=no")
            add("--input-vo-keyboard=no")
            request.sourceHeaders["User-Agent"]?.takeIf { it.isNotBlank() }?.let { add("--user-agent=$it") }
            request.sourceHeaders["Referer"]?.takeIf { it.isNotBlank() }?.let { add("--referrer=$it") }
            request.sourceHeaders.toMpvHeaderFields()?.let { add("--http-header-fields=$it") }
        }

    private fun connectIpc(ipcPath: String): NativeMpvIpcConnection {
        val deadline = System.currentTimeMillis() + 5_000L
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            runCatching {
                val file = RandomAccessFile(ipcPath, "rw")
                val reader = BufferedReader(InputStreamReader(FileInputStream(file.fd), StandardCharsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file.fd), StandardCharsets.UTF_8))
                return NativeMpvIpcConnection(file, NativeMpvIpcClient(reader, writer))
            }.onFailure { error ->
                lastError = error
                Thread.sleep(50)
            }
        }
        throw IllegalStateException("Timed out connecting to MPV IPC pipe $ipcPath", lastError)
    }

    private suspend fun stopProcess() = withContext(Dispatchers.IO) {
        runCatching { activeIpc?.client?.sendCommand(listOf("quit")) }
        runCatching { activeIpc?.close() }
        activeIpc = null
        activeProcess?.destroy()
        runCatching { activeProcess?.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
        activeProcess?.destroyForcibly()
        activeProcess = null
    }

    private fun sendAsync(command: List<Any?>) {
        val ipc = activeIpc ?: return
        scope.launch {
            runCatching { ipc.client.sendCommand(command) }
                .onFailure { DesktopRuntimeLog.warn("native-mpv command failed command=${command.firstOrNull()} message=${it.message}") }
        }
    }

    private fun refreshPositionAsync() {
        val ipc = activeIpc ?: return
        scope.launch {
            val result = ipc.client.sendCommand(listOf("get_property", "time-pos"))
            val seconds = (result as? NativeMpvIpcResult.Success)?.data?.jsonPrimitive?.doubleOrNull
            if (seconds != null) {
                stateFlow.value = stateFlow.value.copy(positionMs = (seconds * 1000.0).roundToLong())
            }
        }
    }

    private fun refreshDuration(client: NativeMpvIpcClient) {
        val result = client.sendCommand(listOf("get_property", "duration"))
        val seconds = (result as? NativeMpvIpcResult.Success)?.data?.jsonPrimitive?.doubleOrNull
        if (seconds != null) {
            stateFlow.value = stateFlow.value.copy(durationMs = (seconds * 1000.0).roundToLong())
        }
    }

    private fun updatePhase(phase: DesktopPlayerPhase) {
        stateFlow.value = stateFlow.value.copy(phase = phase)
    }

    private fun setError(uiMessage: String, technicalMessage: String) {
        stateFlow.value = DesktopPlayerState(
            phase = DesktopPlayerPhase.Error,
            backendName = backendName,
            diagnostics = technicalMessage,
            error = DesktopPlayerError.RuntimeUnavailable(
                backendName = backendName,
                technicalMessage = technicalMessage,
                suggestedAction = uiMessage,
            ),
        )
    }

    private class NativeMpvIpcConnection(
        private val file: RandomAccessFile,
        val client: NativeMpvIpcClient,
    ) {
        fun close() {
            file.close()
        }
    }

    companion object {
        fun create(runtime: NativeMpvRuntimeResolution): Result<NativeMpvDesktopPlayerBackend> =
            runCatching {
                check(runtime.available) { "Native MPV executable is unavailable. ${runtime.diagnostics}" }
                NativeMpvDesktopPlayerBackend(runtime)
            }
    }
}

private fun Map<String, String>.toMpvHeaderFields(): String? {
    val fields = entries
        .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
        .filterNot { (key, _) -> key.equals("Range", ignoreCase = true) }
        .joinToString(",") { (key, value) -> "${key.trim()}: ${value.trim()}" }
    return fields.takeIf { it.isNotBlank() }
}

private fun androidx.compose.ui.graphics.Color.toMpvAssColor(): String {
    fun component(value: Float): String =
        (value * 255f).roundToLong().coerceIn(0, 255).toString(16).padStart(2, '0')
    return "${component(alpha)}${component(blue)}${component(green)}${component(red)}"
}
