package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    val latestOnSnapshot = rememberUpdatedState(onSnapshot)
    val latestOnError = rememberUpdatedState(onError)
    var componentError by remember { mutableStateOf<String?>(null) }
    val mediaComponent = remember {
        runCatching {
            EmbeddedMediaPlayerComponent(
                "--no-video-title-show",
                "--quiet",
            )
        }.onFailure { throwable ->
            componentError = throwable.message ?: throwable::class.simpleName ?: "VLCJ initialization failed"
        }.getOrNull()
    }
    val controller = remember(mediaComponent) {
        mediaComponent?.let {
            DesktopVlcPlayerController(
                component = it,
                onSnapshot = { latestOnSnapshot.value(it) },
                onError = { latestOnError.value(it) },
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
        playWhenReady,
        resizeMode,
    ) {
        val activeController = controller ?: return@LaunchedEffect
        onControllerReady(activeController)
        activeController.load(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            playWhenReady = playWhenReady,
            resizeMode = resizeMode,
        )
    }

    LaunchedEffect(controller) {
        val activeController = controller ?: return@LaunchedEffect
        while (isActive) {
            latestOnSnapshot.value(activeController.snapshot())
            delay(snapshotIntervalMs)
        }
    }

    DisposableEffect(mediaComponent) {
        onDispose {
            controller?.release()
            mediaComponent?.release()
        }
    }

    if (mediaComponent != null) {
        SwingPanel(
            modifier = modifier.background(Color.Black),
            factory = { mediaComponent },
            update = { it.mediaPlayer().controls().setPause(!playWhenReady) },
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
) : PlayerEngineController {
    private val player = component.mediaPlayer()
    private var sourceUrl: String = ""
    private var sourceAudioUrl: String? = null
    private var sourceHeaders: Map<String, String> = emptyMap()
    private var playWhenReady: Boolean = true
    private var released = false
    private val listener = object : MediaPlayerEventAdapter() {
        override fun opening(mediaPlayer: MediaPlayer) {
            onSnapshot(snapshot())
        }

        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            onSnapshot(snapshot(isLoadingOverride = newCache < 100f))
        }

        override fun playing(mediaPlayer: MediaPlayer) {
            onError(null)
            onSnapshot(snapshot())
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            onSnapshot(snapshot())
        }

        override fun stopped(mediaPlayer: MediaPlayer) {
            onSnapshot(snapshot())
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            onSnapshot(snapshot())
        }

        override fun error(mediaPlayer: MediaPlayer) {
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

        if (sourceUrl.isBlank()) {
            onSnapshot(PlayerPlaybackSnapshot(isLoading = false))
            onError("Missing playback source")
            return
        }

        runCatching {
            applyResizeMode(resizeMode)
            val options = mediaOptions(this.sourceHeaders)
            val started = if (playWhenReady) {
                player.media().play(sourceUrl, *options)
            } else {
                player.media().startPaused(sourceUrl, *options)
            }
            if (!started) {
                onError("Playback failed: ${player.status().state()}")
            } else {
                sourceAudioUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let { player.media().addSlave(MediaSlaveType.AUDIO, it, true) }
                onError(null)
                onSnapshot(snapshot())
            }
        }.onFailure { throwable ->
            onError("Playback failed: ${throwable.message ?: throwable::class.simpleName}")
        }
    }

    override fun play() {
        playWhenReady = true
        runCatching { player.controls().play() }
    }

    override fun pause() {
        playWhenReady = false
        runCatching { player.controls().pause() }
    }

    override fun seekTo(positionMs: Long) {
        runCatching { player.controls().setTime(positionMs.coerceAtLeast(0L)) }
    }

    override fun seekBy(offsetMs: Long) {
        val nextPosition = (player.status().time() + offsetMs).coerceAtLeast(0L)
        runCatching { player.controls().setTime(nextPosition) }
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
        runCatching { player.controls().setRate(speed.coerceIn(0.25f, 4f)) }
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
        runCatching { player.audio().setTrack(trackId) }
    }

    override fun selectSubtitleTrack(index: Int) {
        val trackId = if (index < 0) -1 else subtitleTrackDescriptions().getOrNull(index)?.id() ?: return
        runCatching { player.subpictures().setTrack(trackId) }
    }

    override fun setSubtitleUri(url: String) {
        runCatching { player.subpictures().setSubTitleUri(url) }
            .onFailure { onError("Playback failed: ${it.message ?: it::class.simpleName}") }
    }

    override fun clearExternalSubtitle() {
        runCatching { player.subpictures().setTrack(-1) }
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
        runCatching { player.events().removeMediaPlayerEventListener(listener) }
        runCatching { player.controls().stop() }
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
