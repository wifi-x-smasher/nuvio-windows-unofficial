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
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color as AwtColor
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingUtilities
import javax.swing.Timer
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
    val latestOnBack = rememberUpdatedState(onBack)
    val uiScope = rememberCoroutineScope()
    var canvas by remember { mutableStateOf<Canvas?>(null) }
    var controller by remember { mutableStateOf<DesktopMpvPlayerController?>(null) }
    var overlayPanel by remember { mutableStateOf<DesktopPlayerOverlayPanel?>(null) }

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
        runOnSwingThread { overlayPanel?.controller = controller }
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
            val snapshot = withContext(Dispatchers.IO) { activeController.snapshot() }
            latestOnSnapshot.value(snapshot)
            overlayPanel?.updateSnapshot(snapshot)
            delay(snapshotIntervalMs)
        }
    }

    DisposableEffect(controller) {
        onDispose {
            overlayPanel?.dispose()
            controller?.release()
        }
    }

    SwingPanel(
        modifier = modifier.background(Color.Black),
        factory = {
            val videoCanvas = Canvas().apply {
                background = AwtColor.BLACK
                isFocusable = false
            }
            val overlay = DesktopPlayerOverlayPanel(
                title = title,
                streamTitle = streamTitle,
                providerName = providerName,
                onBack = { latestOnBack.value?.invoke() },
            )
            val layeredPane = JLayeredPane().apply {
                background = AwtColor.BLACK
                isOpaque = true
                layout = null
                preferredSize = Dimension(1280, 720)
                add(videoCanvas, JLayeredPane.DEFAULT_LAYER)
                add(overlay, JLayeredPane.PALETTE_LAYER)
                addComponentListener(object : ComponentAdapter() {
                    override fun componentResized(event: ComponentEvent) {
                        videoCanvas.setBounds(0, 0, width, height)
                        overlay.setBounds(0, 0, width, height)
                    }
                })
            }
            canvas = videoCanvas
            overlayPanel = overlay
            layeredPane
        },
        update = { component ->
            val overlay = overlayPanel
            if (overlay != null) {
                overlay.updateMetadata(
                    title = title,
                    streamTitle = streamTitle,
                    providerName = providerName,
                )
            }
            component.revalidate()
            component.repaint()
        },
        background = Color.Black,
    )

    if (canvas == null) {
        Box(modifier = modifier.background(Color.Black))
    }
}

private class DesktopPlayerOverlayPanel(
    title: String,
    streamTitle: String,
    providerName: String,
    private val onBack: () -> Unit,
) : JPanel(null) {
    @Volatile
    var controller: PlayerEngineController? = null

    private val titleLabel = JLabel(title)
    private val subtitleLabel = JLabel(streamSubtitle(streamTitle, providerName))
    private val positionLabel = JLabel("00:00")
    private val durationLabel = JLabel("00:00")
    private val playPauseButton = playerButton("Pause") { togglePlayback() }
    private val seekBackButton = playerButton("-10") { controller?.seekBy(-10_000L) }
    private val seekForwardButton = playerButton("+10") { controller?.seekBy(10_000L) }
    private val backButton = playerButton("Back") { onBack() }
    private val progressSlider = JSlider(0, 1000, 0)
    private val hideTimer = Timer(4_000) { setControlsVisible(false) }
    private var latestSnapshot = PlayerPlaybackSnapshot()
    private var scrubbing = false
    private var updatingSlider = false
    private val topBar = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(34, 40, 0, 40)
        val titleBlock = JPanel().apply {
            isOpaque = false
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(titleLabel)
            add(subtitleLabel)
        }
        add(titleBlock, BorderLayout.WEST)
        add(backButton, BorderLayout.EAST)
    }

    private val centerControls = JPanel(FlowLayout(FlowLayout.CENTER, 24, 0)).apply {
        isOpaque = false
        add(seekBackButton)
        add(playPauseButton)
        add(seekForwardButton)
    }

    private val bottomBar = JPanel(GridBagLayout()).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 44, 42, 44)
        val constraints = GridBagConstraints().apply {
            gridy = 0
            insets = Insets(0, 0, 0, 0)
            fill = GridBagConstraints.HORIZONTAL
        }
        constraints.gridx = 0
        constraints.weightx = 0.0
        add(positionLabel, constraints)
        constraints.gridx = 1
        constraints.weightx = 1.0
        add(progressSlider, constraints)
        constraints.gridx = 2
        constraints.weightx = 0.0
        add(durationLabel, constraints)
    }

    init {
        isOpaque = false
        cursor = Cursor.getDefaultCursor()
        hideTimer.isRepeats = false

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 1) {
                    setControlsVisible(!topBar.isVisible)
                }
            }

            override fun mouseEntered(event: MouseEvent) {
                setControlsVisible(true)
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                setControlsVisible(true)
            }
        })

        configureLabel(titleLabel, 26, Font.BOLD, AwtColor.WHITE)
        configureLabel(subtitleLabel, 15, Font.PLAIN, AwtColor(230, 230, 240, 220))
        configureLabel(positionLabel, 12, Font.BOLD, AwtColor.WHITE)
        configureLabel(durationLabel, 12, Font.BOLD, AwtColor.WHITE)

        progressSlider.apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 8, 0, 8)
            addChangeListener {
                if (updatingSlider) return@addChangeListener
                scrubbing = valueIsAdjusting
                if (!valueIsAdjusting) {
                    val duration = latestSnapshot.durationMs
                    if (duration > 0L) {
                        controller?.seekTo((duration * (value / 1000.0)).toLong())
                    }
                }
            }
        }

        add(topBar)
        add(centerControls)
        add(bottomBar)
        setControlsVisible(true)
    }

    fun updateMetadata(title: String, streamTitle: String, providerName: String) {
        runOnSwingThread {
            titleLabel.text = title
            subtitleLabel.text = streamSubtitle(streamTitle, providerName)
            repaint()
        }
    }

    fun updateSnapshot(snapshot: PlayerPlaybackSnapshot) {
        if (!isDisplayable) return
        runOnSwingThread {
            latestSnapshot = snapshot
            playPauseButton.text = if (snapshot.isPlaying) "Pause" else "Play"
            positionLabel.text = snapshot.positionMs.formatDuration()
            durationLabel.text = snapshot.durationMs.formatDuration()
            if (!scrubbing && snapshot.durationMs > 0L) {
                updatingSlider = true
                progressSlider.value =
                    ((snapshot.positionMs.toDouble() / snapshot.durationMs.toDouble()) * 1000)
                        .toInt()
                        .coerceIn(0, 1000)
                updatingSlider = false
            }
        }
    }

    fun dispose() {
        runOnSwingThread {
            hideTimer.stop()
        }
    }

    override fun doLayout() {
        topBar.setBounds(0, 0, width, 132)
        centerControls.preferredSize = Dimension(width, 70)
        centerControls.setBounds(0, (height / 2) - 35, width, 70)
        bottomBar.setBounds(0, height - 128, width, 128)
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        if (!topBar.isVisible) return
        val g2 = graphics.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.paint = GradientPaint(0f, 0f, AwtColor(0, 0, 0, 190), 0f, 170f, AwtColor(0, 0, 0, 0))
        g2.fillRect(0, 0, width, 190)
        g2.paint = GradientPaint(0f, height.toFloat(), AwtColor(0, 0, 0, 215), 0f, (height - 180).toFloat(), AwtColor(0, 0, 0, 0))
        g2.fillRect(0, height - 220, width, 220)
        g2.dispose()
    }

    private fun setControlsVisible(visible: Boolean) {
        topBar.isVisible = visible
        centerControls.isVisible = visible
        bottomBar.isVisible = visible
        repaint()
        if (visible) {
            hideTimer.restart()
        } else {
            hideTimer.stop()
        }
    }

    private fun togglePlayback() {
        val activeController = controller ?: return
        if (latestSnapshot.isPlaying) {
            activeController.pause()
        } else {
            activeController.play()
        }
        setControlsVisible(true)
    }
}

private fun playerButton(text: String, action: () -> Unit): JButton =
    JButton(text).apply {
        isOpaque = true
        isFocusPainted = false
        foreground = AwtColor.WHITE
        background = AwtColor(16, 16, 24, 210)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AwtColor(255, 255, 255, 70), 1, true),
            BorderFactory.createEmptyBorder(10, 18, 10, 18),
        )
        font = Font("Inter", Font.BOLD, 14)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addActionListener { action() }
    }

private fun configureLabel(label: JLabel, size: Int, style: Int, color: AwtColor) {
    label.font = Font("Inter", style, size)
    label.foreground = color
}

private fun streamSubtitle(streamTitle: String, providerName: String): String =
    listOf(streamTitle, providerName)
        .filter { it.isNotBlank() }
        .joinToString(" / ")

private fun String.safeHostForLogs(): String =
    runCatching { URI(this).host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: substringBefore('?').substringBefore('/').take(80)

private fun Long.formatDuration(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
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
                    details = mapOf("sourceHost" to sourceUrl.safeHostForLogs()),
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
        if (released || ipcPath.isBlank()) return PlayerPlaybackSnapshot(isLoading = true)
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
            ipcPath = ""
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
                "sourceHost" to url.safeHostForLogs(),
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
        if (released || ipcPath.isBlank()) return null
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
            if (!released) {
                AppDiagnostics.error(
                    event = "player.mpv.ipc.failure",
                    throwable = throwable,
                    details = mapOf("command" to command),
                )
            }
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
