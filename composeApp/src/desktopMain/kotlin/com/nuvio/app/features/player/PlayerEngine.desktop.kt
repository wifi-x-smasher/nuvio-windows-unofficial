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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.JWindow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

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
    var componentError by remember { mutableStateOf<String?>(null) }
    var mediaComponent by remember { mutableStateOf<EmbeddedMediaPlayerComponent?>(null) }

    LaunchedEffect(Unit) {
        AppDiagnostics.breadcrumb("player.vlc.component.prepare.start", emptyMap())
        val componentResult = withContext(Dispatchers.IO) {
            DesktopVlcRuntime.prepare()
        }.mapCatching { runtimeDirectory ->
            onSwingThread {
                EmbeddedMediaPlayerComponent(
                    "--no-video-title-show",
                    "--quiet",
                    "--plugin-path=${runtimeDirectory.resolve("plugins")}",
                )
            }
        }
        componentResult
            .onSuccess { component ->
                AppDiagnostics.breadcrumb("player.vlc.component.prepare.success", emptyMap())
                componentError = null
                mediaComponent = component
            }
            .onFailure { throwable ->
                AppDiagnostics.error(
                    event = "player.vlc.component.prepare.failure",
                    throwable = throwable,
                    details = emptyMap(),
                )
                componentError = throwable.message ?: throwable::class.simpleName ?: "VLCJ initialization failed"
            }
    }
    val controller = remember(mediaComponent) {
        mediaComponent?.let {
            DesktopVlcPlayerController(
                component = it,
                onSnapshot = { snapshot ->
                    uiScope.launch { latestOnSnapshot.value(snapshot) }
                },
                onError = { message ->
                    uiScope.launch { latestOnError.value(message) }
                },
            )
        }
    }

    LaunchedEffect(componentError) {
        componentError?.let { latestOnError.value("Playback failed: $it") }
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
        activeController.updateOverlay(
            title = title,
            streamTitle = streamTitle,
            providerName = providerName,
            onBack = { latestOnBack.value?.invoke() },
        )
        activeController.load(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            playWhenReady = playWhenReady,
            resizeMode = resizeMode,
        )
    }

    LaunchedEffect(controller, playWhenReady) {
        val activeController = controller ?: return@LaunchedEffect
        activeController.updateOverlay(
            title = title,
            streamTitle = streamTitle,
            providerName = providerName,
            onBack = { latestOnBack.value?.invoke() },
        )
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

    DisposableEffect(mediaComponent) {
        onDispose {
            uiScope.launch {
                controller?.release()
            }
        }
    }

    val activeMediaComponent = mediaComponent
    if (activeMediaComponent != null) {
        SwingPanel(
            modifier = modifier.background(Color.Black),
            factory = { activeMediaComponent },
            update = {
                controller?.updateOverlay(
                    title = title,
                    streamTitle = streamTitle,
                    providerName = providerName,
                    onBack = { latestOnBack.value?.invoke() },
                )
            },
            background = Color.Black,
        )
    } else {
        Box(modifier = modifier.background(Color.Black))
    }
}

private class DesktopVlcPlayerController(
    private val component: EmbeddedMediaPlayerComponent,
    private val onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    private val onError: (String?) -> Unit,
    private val operationDispatcher: DesktopPlayerOperationDispatcher =
        SwingDesktopPlayerOperationDispatcher(),
) : PlayerEngineController {
    private val player = component.mediaPlayer()
    @Volatile
    private var sourceUrl: String = ""
    @Volatile
    private var sourceAudioUrl: String? = null
    @Volatile
    private var sourceHeaders: Map<String, String> = emptyMap()
    @Volatile
    private var playWhenReady: Boolean = true
    @Volatile
    private var released = false
    private var overlay: DesktopVlcControlsOverlay? = null
    private val listener = object : MediaPlayerEventAdapter() {
        override fun opening(mediaPlayer: MediaPlayer) {
            AppDiagnostics.breadcrumb(
                event = "player.vlc.event.opening",
                details = playbackDetails(),
            )
            onSnapshot(snapshot())
        }

        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            onSnapshot(snapshot(isLoadingOverride = newCache < 100f))
        }

        override fun playing(mediaPlayer: MediaPlayer) {
            AppDiagnostics.breadcrumb(
                event = "player.vlc.event.playing",
                details = playbackDetails(),
            )
            onError(null)
            onSnapshot(snapshot())
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            onSnapshot(snapshot())
        }

        override fun stopped(mediaPlayer: MediaPlayer) {
            AppDiagnostics.breadcrumb(
                event = "player.vlc.event.stopped",
                details = playbackDetails(),
            )
            onSnapshot(snapshot())
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            AppDiagnostics.breadcrumb(
                event = "player.vlc.event.finished",
                details = playbackDetails(),
            )
            onSnapshot(snapshot())
        }

        override fun error(mediaPlayer: MediaPlayer) {
            AppDiagnostics.error(
                event = "player.vlc.event.error",
                details = playbackDetails() + mapOf("state" to mediaPlayer.status().state().toString()),
            )
            onError("Playback failed: ${mediaPlayer.status().state()}")
            onSnapshot(snapshot())
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            onSnapshot(snapshot())
        }

        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            onSnapshot(snapshot())
        }
    }

    init {
        player.events().addMediaPlayerEventListener(listener)
    }

    fun updateOverlay(
        title: String,
        streamTitle: String,
        providerName: String,
        onBack: () -> Unit,
    ) {
        operationDispatcher.dispatch {
            if (released) return@dispatch
            val existingOverlay = overlay
            if (existingOverlay != null) {
                existingOverlay.updateMetadata(title, streamTitle, providerName, onBack)
                return@dispatch
            }

            val owner = SwingUtilities.getWindowAncestor(component) ?: return@dispatch
            val newOverlay = DesktopVlcControlsOverlay(
                owner = owner,
                controller = this,
                title = title,
                streamTitle = streamTitle,
                providerName = providerName,
                onBack = onBack,
            )
            overlay = newOverlay
            runCatching {
                player.overlay().set(newOverlay)
                player.overlay().enable(true)
            }.onFailure { throwable ->
                AppDiagnostics.error(
                    event = "player.vlc.overlay.failure",
                    throwable = throwable,
                    details = playbackDetails(),
                )
            }
        }
    }

    fun load(
        sourceUrl: String,
        sourceAudioUrl: String?,
        sourceHeaders: Map<String, String>,
        playWhenReady: Boolean,
        resizeMode: PlayerResizeMode,
    ) {
        this.sourceUrl = sourceUrl
        this.sourceAudioUrl = sourceAudioUrl
        this.sourceHeaders = sanitizePlaybackHeaders(sourceHeaders)
        this.playWhenReady = playWhenReady

        AppDiagnostics.breadcrumb(
            event = "player.vlc.load.start",
            details = playbackDetails() + mapOf(
                "sourceKind" to sourceUrl.diagnosticSourceKind(),
                "hasSourceAudio" to (!sourceAudioUrl.isNullOrBlank()).toString(),
                "headerCount" to this.sourceHeaders.size.toString(),
                "playWhenReady" to playWhenReady.toString(),
                "resizeMode" to resizeMode.name,
            ),
        )
        if (sourceUrl.isBlank()) {
            AppDiagnostics.error(
                event = "player.vlc.load.missing_source",
                details = playbackDetails(),
            )
            onSnapshot(PlayerPlaybackSnapshot(isLoading = false))
            onError("Missing playback source")
            return
        }

        onSnapshot(PlayerPlaybackSnapshot(isLoading = true))
        operationDispatcher.dispatch {
            if (released) return@dispatch
            runCatching {
                applyResizeMode(resizeMode)
                val options = mediaOptions(this.sourceHeaders)
                val started = if (playWhenReady) {
                    player.media().play(sourceUrl, *options)
                } else {
                    player.media().startPaused(sourceUrl, *options)
                }
                if (!started) {
                    AppDiagnostics.error(
                        event = "player.vlc.load.not_started",
                        details = playbackDetails() + mapOf("state" to player.status().state().toString()),
                    )
                    onError("Playback failed: ${player.status().state()}")
                } else {
                    sourceAudioUrl
                        ?.takeIf(String::isNotBlank)
                        ?.let { player.media().addSlave(MediaSlaveType.AUDIO, it, true) }
                    AppDiagnostics.breadcrumb(
                        event = "player.vlc.load.started",
                        details = playbackDetails() + mapOf("state" to player.status().state().toString()),
                    )
                    onError(null)
                    onSnapshot(snapshot())
                }
            }.onFailure { throwable ->
                AppDiagnostics.error(
                    event = "player.vlc.load.failure",
                    throwable = throwable,
                    details = playbackDetails(),
                )
                onError("Playback failed: ${throwable.message ?: throwable::class.simpleName}")
            }
        }
    }

    override fun play() {
        playWhenReady = true
        operationDispatcher.dispatch {
            if (!released) runCatching { player.controls().play() }
        }
    }

    override fun pause() {
        playWhenReady = false
        operationDispatcher.dispatch {
            if (!released) runCatching { player.controls().pause() }
        }
    }

    override fun seekTo(positionMs: Long) {
        operationDispatcher.dispatch {
            if (!released) runCatching { player.controls().setTime(positionMs.coerceAtLeast(0L)) }
        }
    }

    override fun seekBy(offsetMs: Long) {
        operationDispatcher.dispatch {
            if (!released) {
                val nextPosition = (player.status().time() + offsetMs).coerceAtLeast(0L)
                runCatching { player.controls().setTime(nextPosition) }
            }
        }
    }

    override fun retry() {
        load(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            playWhenReady = true,
            resizeMode = PlayerResizeMode.Fit,
        )
    }

    override fun setPlaybackSpeed(speed: Float) {
        operationDispatcher.dispatch {
            if (!released) runCatching { player.controls().setRate(speed.coerceIn(0.25f, 4f)) }
        }
    }

    override fun getAudioTracks(): List<AudioTrack> =
        audioTrackDescriptions().mapIndexed { index, track ->
            AudioTrack(
                index = index,
                id = track.id().toString(),
                label = track.description().orTrackLabel(index),
                isSelected = track.id() == player.audio().track(),
            )
        }

    override fun getSubtitleTracks(): List<SubtitleTrack> =
        subtitleTrackDescriptions().mapIndexed { index, track ->
            SubtitleTrack(
                index = index,
                id = track.id().toString(),
                label = track.description().orTrackLabel(index),
                isSelected = track.id() == player.subpictures().track(),
                isForced = inferForcedSubtitleTrack(
                    label = track.description(),
                    language = null,
                    trackId = track.id().toString(),
                    hasForcedSelectionFlag = false,
                ),
            )
        }

    override fun selectAudioTrack(index: Int) {
        val trackId = audioTrackDescriptions().getOrNull(index)?.id() ?: return
        operationDispatcher.dispatch {
            if (!released) runCatching { player.audio().setTrack(trackId) }
        }
    }

    override fun selectSubtitleTrack(index: Int) {
        val trackId = if (index < 0) -1 else subtitleTrackDescriptions().getOrNull(index)?.id() ?: return
        operationDispatcher.dispatch {
            if (!released) runCatching { player.subpictures().setTrack(trackId) }
        }
    }

    override fun setSubtitleUri(url: String) {
        operationDispatcher.dispatch {
            if (!released) {
                runCatching { player.subpictures().setSubTitleUri(url) }
                    .onFailure { onError("Playback failed: ${it.message ?: it::class.simpleName}") }
            }
        }
    }

    override fun clearExternalSubtitle() {
        operationDispatcher.dispatch {
            if (!released) runCatching { player.subpictures().setTrack(-1) }
        }
    }

    override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        clearExternalSubtitle()
        selectSubtitleTrack(trackIndex)
    }

    override fun applySubtitleStyle(style: SubtitleStyleState) {
        // VLC subtitle styling is renderer-dependent. The common state is still persisted.
    }

    fun snapshot(isLoadingOverride: Boolean? = null): PlayerPlaybackSnapshot {
        val status = player.status()
        val state = runCatching { status.state() }.getOrDefault(State.NOTHING_SPECIAL)
        val durationMs = runCatching { status.length().coerceAtLeast(0L) }.getOrDefault(0L)
        val positionMs = runCatching { status.time().coerceAtLeast(0L) }.getOrDefault(0L)
        val playbackSpeed = runCatching { status.rate().takeIf { it > 0f } ?: 1f }.getOrDefault(1f)
        return PlayerPlaybackSnapshot(
            isLoading = isLoadingOverride ?: (state == State.OPENING || state == State.BUFFERING),
            isPlaying = runCatching { status.isPlaying }.getOrDefault(false),
            isEnded = state == State.ENDED,
            durationMs = durationMs,
            positionMs = positionMs,
            bufferedPositionMs = positionMs,
            playbackSpeed = playbackSpeed,
        )
    }

    fun release() {
        if (released) return
        released = true
        AppDiagnostics.breadcrumb(
            event = "player.vlc.release",
            details = playbackDetails(),
        )
        operationDispatcher.dispatch {
            overlay?.dispose()
            overlay = null
            runCatching { player.events().removeMediaPlayerEventListener(listener) }
            runCatching { player.controls().stop() }
            runCatching { component.release() }
            operationDispatcher.close()
        }
    }

    private fun audioTrackDescriptions(): List<TrackDescription> =
        runCatching { player.audio().trackDescriptions().filter { it.id() >= 0 } }
            .getOrDefault(emptyList())

    private fun subtitleTrackDescriptions(): List<TrackDescription> =
        runCatching { player.subpictures().trackDescriptions().filter { it.id() >= 0 } }
            .getOrDefault(emptyList())

    private fun applyResizeMode(resizeMode: PlayerResizeMode) {
        runCatching {
            when (resizeMode) {
                PlayerResizeMode.Fit -> {
                    player.video().setScale(0f)
                    player.video().setAspectRatio(null)
                }
                PlayerResizeMode.Fill -> {
                    player.video().setScale(0f)
                    player.video().setAspectRatio(null)
                }
                PlayerResizeMode.Zoom -> player.video().setScale(1.25f)
            }
        }
    }

    private fun playbackDetails(): Map<String, String?> =
        mapOf(
            "sourceKind" to sourceUrl.diagnosticSourceKind(),
            "hasSourceAudio" to (!sourceAudioUrl.isNullOrBlank()).toString(),
            "headerCount" to sourceHeaders.size.toString(),
            "released" to released.toString(),
        )
}

internal interface DesktopPlayerOperationDispatcher {
    fun dispatch(operation: () -> Unit)
    fun close()
}

internal class SwingDesktopPlayerOperationDispatcher : DesktopPlayerOperationDispatcher {
    private val closed = AtomicBoolean(false)

    override fun dispatch(operation: () -> Unit) {
        if (closed.get()) return
        if (SwingUtilities.isEventDispatchThread()) {
            if (!closed.get()) operation()
        } else {
            SwingUtilities.invokeLater {
                if (!closed.get()) operation()
            }
        }
    }

    override fun close() {
        closed.set(true)
    }
}

private class DesktopVlcControlsOverlay(
    owner: Window,
    private val controller: DesktopVlcPlayerController,
    title: String,
    streamTitle: String,
    providerName: String,
    onBack: () -> Unit,
) : JWindow(owner) {
    private val titleLabel = JLabel()
    private val subtitleLabel = JLabel()
    private val playPauseButton = JButton("Pause")
    private val positionLabel = JLabel("00:00")
    private val durationLabel = JLabel("00:00")
    private val slider = JSlider(0, 1000, 0)
    private var latestOnBack: () -> Unit = onBack
    private var sliderChanging = false
    private var lastInteractionMs = System.currentTimeMillis()
    private val controlsPanel = GradientControlsPanel()

    private val refreshTimer = Timer(500) {
        refreshState()
        val idleMs = System.currentTimeMillis() - lastInteractionMs
        setControlsVisible(idleMs < 4_000L || !controller.snapshot().isPlaying)
    }

    init {
        background = java.awt.Color(0, 0, 0, 0)
        contentPane = controlsPanel
        controlsPanel.layout = BorderLayout()
        controlsPanel.isOpaque = false
        controlsPanel.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)

        val topPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = javax.swing.BorderFactory.createEmptyBorder(28, 32, 0, 32)
        }
        val backButton = overlayButton("Back").apply {
            addActionListener { latestOnBack() }
        }
        val titlePanel = JPanel(GridBagLayout()).apply { isOpaque = false }
        val titleConstraints = GridBagConstraints().apply {
            gridx = 0
            anchor = GridBagConstraints.WEST
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
        }
        titleLabel.font = Font("SansSerif", Font.BOLD, 24)
        titleLabel.foreground = java.awt.Color.WHITE
        subtitleLabel.font = Font("SansSerif", Font.PLAIN, 14)
        subtitleLabel.foreground = java.awt.Color(220, 220, 230)
        titlePanel.add(titleLabel, titleConstraints)
        titlePanel.add(subtitleLabel, titleConstraints.apply { gridy = 1 })
        topPanel.add(titlePanel, BorderLayout.WEST)
        topPanel.add(backButton, BorderLayout.EAST)

        val centerPanel = JPanel(FlowLayout(FlowLayout.CENTER, 20, 0)).apply {
            isOpaque = false
        }
        centerPanel.add(overlayButton("-10").apply { addActionListener { controller.seekBy(-10_000L) } })
        centerPanel.add(playPauseButton.apply {
            font = Font("SansSerif", Font.BOLD, 20)
            isFocusPainted = false
            addActionListener {
                val snapshot = controller.snapshot()
                if (snapshot.isPlaying) controller.pause() else controller.play()
                markInteraction()
            }
        })
        centerPanel.add(overlayButton("+10").apply { addActionListener { controller.seekBy(10_000L) } })

        val bottomPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = javax.swing.BorderFactory.createEmptyBorder(0, 32, 28, 32)
        }
        positionLabel.foreground = java.awt.Color.WHITE
        durationLabel.foreground = java.awt.Color.WHITE
        positionLabel.horizontalAlignment = SwingConstants.LEFT
        durationLabel.horizontalAlignment = SwingConstants.RIGHT
        slider.isOpaque = false
        slider.addChangeListener {
            markInteraction()
            if (slider.valueIsAdjusting) {
                sliderChanging = true
            } else if (sliderChanging) {
                sliderChanging = false
                val duration = controller.snapshot().durationMs
                if (duration > 0L) {
                    controller.seekTo((duration * (slider.value / 1000f)).toLong())
                }
            }
        }
        val constraints = GridBagConstraints().apply {
            gridy = 0
            insets = Insets(0, 8, 0, 8)
            fill = GridBagConstraints.HORIZONTAL
        }
        bottomPanel.add(positionLabel, constraints.apply { gridx = 0; weightx = 0.0 })
        bottomPanel.add(slider, constraints.apply { gridx = 1; weightx = 1.0 })
        bottomPanel.add(durationLabel, constraints.apply { gridx = 2; weightx = 0.0 })

        controlsPanel.add(topPanel, BorderLayout.NORTH)
        controlsPanel.add(centerPanel, BorderLayout.CENTER)
        controlsPanel.add(bottomPanel, BorderLayout.SOUTH)
        controlsPanel.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) = markInteraction()
            override fun mouseDragged(e: MouseEvent) = markInteraction()
        })
        controlsPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = markInteraction()
            override fun mousePressed(e: MouseEvent) = markInteraction()
        })

        updateMetadata(title, streamTitle, providerName, onBack)
        refreshTimer.start()
    }

    fun updateMetadata(
        title: String,
        streamTitle: String,
        providerName: String,
        onBack: () -> Unit,
    ) {
        latestOnBack = onBack
        titleLabel.text = title.takeIf(String::isNotBlank) ?: "Nuvio"
        subtitleLabel.text = listOf(streamTitle, providerName)
            .filter(String::isNotBlank)
            .joinToString("  /  ")
    }

    override fun dispose() {
        refreshTimer.stop()
        super.dispose()
    }

    private fun refreshState() {
        val snapshot = controller.snapshot()
        playPauseButton.text = if (snapshot.isPlaying) "Pause" else "Play"
        positionLabel.text = formatPlayerTime(snapshot.positionMs)
        durationLabel.text = formatPlayerTime(snapshot.durationMs)
        if (!sliderChanging && snapshot.durationMs > 0L) {
            slider.value = ((snapshot.positionMs.toFloat() / snapshot.durationMs.toFloat()) * 1000f)
                .toInt()
                .coerceIn(0, 1000)
        }
    }

    private fun markInteraction() {
        lastInteractionMs = System.currentTimeMillis()
        setControlsVisible(true)
    }

    private fun setControlsVisible(visible: Boolean) {
        if (controlsPanel.isControlsVisible == visible) return
        controlsPanel.isControlsVisible = visible
        controlsPanel.components.forEach { it.isVisible = visible }
        controlsPanel.repaint()
    }

    private fun overlayButton(text: String): JButton =
        JButton(text).apply {
            font = Font("SansSerif", Font.BOLD, 14)
            foreground = java.awt.Color.WHITE
            background = java.awt.Color(24, 24, 30, 210)
            isFocusPainted = false
            margin = Insets(8, 14, 8, 14)
            addActionListener { markInteraction() }
        }
}

private fun formatPlayerTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private class GradientControlsPanel : JPanel() {
    var isControlsVisible: Boolean = true

    override fun paintComponent(g: Graphics) {
        if (!isControlsVisible) return
        val g2 = g.create() as Graphics2D
        try {
            g2.paint = GradientPaint(
                0f,
                0f,
                java.awt.Color(0, 0, 0, 185),
                0f,
                (height * 0.35f),
                java.awt.Color(0, 0, 0, 0),
            )
            g2.fillRect(0, 0, width, (height * 0.35f).toInt())
            g2.paint = GradientPaint(
                0f,
                (height * 0.62f),
                java.awt.Color(0, 0, 0, 0),
                0f,
                height.toFloat(),
                java.awt.Color(0, 0, 0, 200),
            )
            g2.fillRect(0, (height * 0.62f).toInt(), width, height)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}

internal class ExecutorDesktopPlayerOperationDispatcher(
    threadName: String = "Nuvio-VLC-Player",
) : DesktopPlayerOperationDispatcher {
    private val closed = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, threadName).apply { isDaemon = true }
    }

    override fun dispatch(operation: () -> Unit) {
        if (closed.get()) return
        try {
            executor.execute {
                if (!closed.get()) {
                    operation()
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow()
        }
    }
}

private fun String?.orTrackLabel(index: Int): String =
    this?.takeIf(String::isNotBlank) ?: "Track ${index + 1}"

private fun mediaOptions(headers: Map<String, String>): Array<String> {
    val options = mutableListOf(
        ":network-caching=1500",
        ":file-caching=1000",
    )
    headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
        ?.value
        ?.let { options += ":http-user-agent=$it" }
    headers.entries.firstOrNull {
        it.key.equals("Referer", ignoreCase = true) || it.key.equals("Referrer", ignoreCase = true)
    }?.value?.let { options += ":http-referrer=$it" }
    headers.forEach { (key, value) ->
        if (!key.equals("User-Agent", ignoreCase = true) && !key.equals("Referer", ignoreCase = true)) {
            options += ":http-header=$key: $value"
        }
    }
    return options.toTypedArray()
}

private fun <T> onSwingThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var result: Result<T>? = null
    SwingUtilities.invokeAndWait {
        result = runCatching(block)
    }
    return result!!.getOrThrow()
}

private fun String?.diagnosticSourceKind(): String =
    when {
        isNullOrBlank() -> "none"
        startsWith("magnet:", ignoreCase = true) -> "magnet"
        startsWith("file:", ignoreCase = true) -> "file"
        startsWith("http://", ignoreCase = true) -> "http"
        startsWith("https://", ignoreCase = true) -> "https"
        contains(':') -> substringBefore(':').take(24)
        else -> "unknown"
    }
