package com.nuvio.app.features.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.nuvio.app.core.diagnostics.AppDiagnostics
import com.nuvio.app.features.player.desktop.DesktopPlayerSurfaceHost
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Image as SkiaImage
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

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
    DesktopPlayerSurfaceHost(
        sourceUrl = sourceUrl,
        sourceAudioUrl = sourceAudioUrl,
        sourceHeaders = sanitizePlaybackHeaders(sourceHeaders),
        sourceResponseHeaders = sanitizePlaybackResponseHeaders(sourceResponseHeaders),
        modifier = modifier,
        playWhenReady = playWhenReady,
        resizeMode = resizeMode,
        onControllerReady = onControllerReady,
        onSnapshot = onSnapshot,
        onError = { message ->
            if (message?.contains("runtime", ignoreCase = true) == true) {
                AppDiagnostics.breadcrumb(
                    event = "player.internal.runtime_unavailable_no_auto_vlc",
                    details = mapOf("reason" to message.take(160)),
                )
            }
            onError(message)
        },
    )
}

@Composable
private fun DesktopVlcCallbackPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
    useYoutubeChunkedPlayback: Boolean,
    modifier: Modifier,
    playWhenReady: Boolean,
    resizeMode: PlayerResizeMode,
    title: String,
    streamTitle: String,
    providerName: String,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    val latestOnSnapshot = rememberUpdatedState(onSnapshot)
    val latestOnError = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    var frame by remember { mutableStateOf<DesktopVlcVideoFrame?>(null) }
    val controller = remember {
        DesktopVlcPlayerController(
            onFrame = { nextFrame -> frame = nextFrame },
            onClearFrame = { frame = null },
            onSnapshot = { snapshot -> scope.launch { latestOnSnapshot.value(snapshot) } },
            onError = { message -> scope.launch { latestOnError.value(message) } },
        )
    }

    LaunchedEffect(controller) {
        onControllerReady(controller)
    }

    LaunchedEffect(
        controller,
        sourceUrl,
        sourceAudioUrl,
        sourceHeaders,
        sourceResponseHeaders,
        useYoutubeChunkedPlayback,
    ) {
        onControllerReady(controller)
        controller.load(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            playWhenReady = playWhenReady,
            title = title,
            streamTitle = streamTitle,
            providerName = providerName,
        )
    }

    LaunchedEffect(controller, playWhenReady) {
        if (playWhenReady) {
            controller.play()
        } else {
            controller.pause()
        }
    }

    LaunchedEffect(controller) {
        while (isActive) {
            val snapshot = withContext(kotlinx.coroutines.Dispatchers.IO) { controller.snapshot() }
            latestOnSnapshot.value(snapshot)
            delay(snapshotIntervalMs)
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val activeFrame = frame
        if (activeFrame != null) {
            Image(
                bitmap = remember(activeFrame.sequence) { activeFrame.toImageBitmap() },
                contentDescription = null,
                contentScale = resizeMode.toContentScale(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal class DesktopVlcPlayerController(
    private val onFrame: (DesktopVlcVideoFrame) -> Unit,
    private val onClearFrame: () -> Unit,
    private val onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    private val onError: (String?) -> Unit,
) : PlayerEngineController {
    private val frameSequence = AtomicLong(0)
    private val lifecycleLock = Any()
    @Volatile
    private var factory: MediaPlayerFactory? = null
    @Volatile
    private var mediaPlayer: EmbeddedMediaPlayer? = null
    @Volatile
    private var width = 0
    @Volatile
    private var height = 0
    @Volatile
    private var sourceUrl: String = ""
    @Volatile
    private var sourceAudioUrl: String? = null
    @Volatile
    private var sourceHeaders: Map<String, String> = emptyMap()
    @Volatile
    private var title: String = ""
    @Volatile
    private var streamTitle: String = ""
    @Volatile
    private var providerName: String = ""
    @Volatile
    private var released = false
    @Volatile
    private var desktopBrightness = 0.5f

    fun load(
        sourceUrl: String,
        sourceAudioUrl: String?,
        sourceHeaders: Map<String, String>,
        playWhenReady: Boolean,
        title: String,
        streamTitle: String,
        providerName: String,
    ) {
        this.sourceUrl = sourceUrl
        this.sourceAudioUrl = sourceAudioUrl
        this.sourceHeaders = sanitizePlaybackHeaders(sourceHeaders)
        this.title = title
        this.streamTitle = streamTitle
        this.providerName = providerName
        onClearFrame()

        if (sourceUrl.isBlank()) {
            onError("Playback failed: stream URL is empty.")
            return
        }

        AppDiagnostics.breadcrumb(
            event = "player.vlc.callback.load.start",
            details = mapOf("sourceHost" to sourceUrl.safeHostForLogs()),
        )

        val runtime = DesktopVlcRuntime.prepare()
        if (runtime.isFailure) {
            onError(InternalPlayerPlatform.unavailableMessage())
            return
        }

        val activePlayer = ensurePlayer()
        runCatching {
            activePlayer.controls().stop()
            activePlayer.media().reset()
            val options = buildVlcOptions(this.sourceHeaders, playWhenReady)
            val started = activePlayer.media().play(sourceUrl, *options.toTypedArray())
            if (!started) {
                onError("Playback failed: VLC could not open this stream.")
                return
            }
            sourceAudioUrl?.takeIf(String::isNotBlank)?.let { audioUrl ->
                runCatching { activePlayer.media().addSlave(MediaSlaveType.AUDIO, audioUrl, true) }
            }
            if (!playWhenReady) activePlayer.controls().setPause(true)
            onError(null)
            onSnapshot(snapshot())
            AppDiagnostics.breadcrumb("player.vlc.callback.load.success", emptyMap())
        }.onFailure { throwable ->
            AppDiagnostics.error(
                event = "player.vlc.callback.load.failure",
                throwable = throwable,
                details = mapOf("sourceHost" to sourceUrl.safeHostForLogs()),
            )
            onError("Playback failed: ${throwable.message ?: "VLC could not start."}")
        }
    }

    override fun play() {
        mediaPlayer?.let { player ->
            player.controls().setPause(false)
            onSnapshot(snapshot())
        }
    }

    override fun pause() {
        mediaPlayer?.controls()?.setPause(true)
        onSnapshot(snapshot())
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.controls()?.setTime(positionMs.coerceAtLeast(0L))
        onSnapshot(snapshot())
    }

    override fun seekBy(offsetMs: Long) {
        mediaPlayer?.controls()?.skipTime(offsetMs)
        onSnapshot(snapshot())
    }

    override fun retry() {
        load(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            playWhenReady = true,
            title = title,
            streamTitle = streamTitle,
            providerName = providerName,
        )
    }

    override fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.controls()?.setRate(speed.coerceIn(0.25f, 3f))
        onSnapshot(snapshot())
    }

    override fun getAudioTracks(): List<AudioTrack> =
        mediaPlayer
            ?.audio()
            ?.trackDescriptions()
            ?.filter { it.id() >= 0 }
            ?.mapIndexed { index, track ->
                AudioTrack(
                    index = index,
                    id = track.id().toString(),
                    label = track.description().takeIf { it.isNotBlank() } ?: "Track ${index + 1}",
                    isSelected = mediaPlayer?.audio()?.track() == track.id(),
                )
            }
            ?: emptyList()

    override fun getSubtitleTracks(): List<SubtitleTrack> =
        mediaPlayer
            ?.subpictures()
            ?.trackDescriptions()
            ?.filter { it.id() >= 0 }
            ?.mapIndexed { index, track ->
                SubtitleTrack(
                    index = index,
                    id = track.id().toString(),
                    label = track.description().takeIf { it.isNotBlank() } ?: "Track ${index + 1}",
                    isSelected = mediaPlayer?.subpictures()?.track() == track.id(),
                )
            }
            ?: emptyList()

    override fun selectAudioTrack(index: Int) {
        val trackId = selectableVlcTrackIdForUiIndex(
            mediaPlayer?.audio()?.trackDescriptions()?.map { it.id() } ?: emptyList(),
            index,
        ) ?: return
        mediaPlayer?.audio()?.setTrack(trackId)
        onSnapshot(snapshot())
    }

    override fun selectSubtitleTrack(index: Int) {
        if (index < 0) {
            mediaPlayer?.subpictures()?.setTrack(-1)
            return
        }
        val trackId = mediaPlayer?.subpictures()?.trackDescriptions()
            ?.filter { it.id() >= 0 }
            ?.getOrNull(index)
            ?.id()
            ?: return
        mediaPlayer?.subpictures()?.setTrack(trackId)
    }

    override fun setSubtitleUri(url: String) {
        mediaPlayer?.subpictures()?.setSubTitleUri(url)
        mediaPlayer?.subpictures()?.trackDescriptions()
            ?.filter { it.id() >= 0 }
            ?.lastOrNull()
            ?.id()
            ?.let { mediaPlayer?.subpictures()?.setTrack(it) }
    }

    override fun clearExternalSubtitle() {
        mediaPlayer?.subpictures()?.setTrack(-1)
    }

    override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        clearExternalSubtitle()
        selectSubtitleTrack(trackIndex)
    }

    fun snapshot(): PlayerPlaybackSnapshot {
        val activePlayer = mediaPlayer ?: return PlayerPlaybackSnapshot(isLoading = !released)
        return runCatching {
            val duration = activePlayer.status().length().coerceAtLeast(0L)
            val position = activePlayer.status().time().coerceAtLeast(0L)
            val state = activePlayer.status().state()
            val isEnded = state.name.equals("ENDED", ignoreCase = true) ||
                (duration > 0L && position >= duration - 500L)
            PlayerPlaybackSnapshot(
                isLoading = !released && duration <= 0L && !isEnded,
                isPlaying = activePlayer.status().isPlaying && !isEnded,
                isEnded = isEnded,
                durationMs = duration,
                positionMs = position,
                bufferedPositionMs = duration,
                playbackSpeed = activePlayer.status().rate().takeIf { it > 0f } ?: 1f,
            )
        }.getOrDefault(PlayerPlaybackSnapshot(isLoading = !released))
    }

    fun release() {
        val activePlayer: EmbeddedMediaPlayer?
        val activeFactory: MediaPlayerFactory?
        synchronized(lifecycleLock) {
            if (released) return
            released = true
            activePlayer = mediaPlayer
            activeFactory = factory
            mediaPlayer = null
            factory = null
        }
        runCatching { activePlayer?.controls()?.stop() }
        runCatching { activePlayer?.release() }
        runCatching { activeFactory?.release() }
        DesktopVlcPlayerBridge.detach(this)
        onSnapshot(PlayerPlaybackSnapshot(isLoading = false))
    }

    private fun ensurePlayer(): EmbeddedMediaPlayer =
        synchronized(lifecycleLock) {
            if (released) {
                released = false
            }
            mediaPlayer?.let { return@synchronized it }

            val nextFactory = MediaPlayerFactory(
                "--no-video-title-show",
                "--no-snapshot-preview",
                "--quiet",
            )
            val nextPlayer = nextFactory.mediaPlayers().newEmbeddedMediaPlayer()
            nextPlayer.events().addMediaPlayerEventListener(object : uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter() {
                override fun error(mediaPlayer: MediaPlayer) {
                    AppDiagnostics.error(
                        event = "player.vlc.callback.error",
                        throwable = null,
                        details = mapOf("sourceHost" to sourceUrl.safeHostForLogs()),
                    )
                    onError("Playback failed: VLC reported a player error.")
                    onSnapshot(snapshot())
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

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    onSnapshot(snapshot())
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    onSnapshot(snapshot())
                }

                override fun elementaryStreamSelected(
                    mediaPlayer: MediaPlayer,
                    type: uk.co.caprica.vlcj.media.TrackType,
                    id: Int,
                ) {
                    onSnapshot(snapshot())
                }
            })
            val bufferFormatCallback = object : BufferFormatCallback {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    width = sourceWidth.coerceAtLeast(1)
                    height = sourceHeight.coerceAtLeast(1)
                    AppDiagnostics.breadcrumb(
                        event = "player.vlc.callback.video_format",
                        details = mapOf("width" to width.toString(), "height" to height.toString()),
                    )
                    return RV32BufferFormat(width, height)
                }

                override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
            }
            val renderCallback = RenderCallback { _: MediaPlayer, nativeBuffers: Array<ByteBuffer>, _: BufferFormat ->
                val activeWidth = width
                val activeHeight = height
                if (activeWidth <= 0 || activeHeight <= 0 || nativeBuffers.isEmpty() || released) {
                    return@RenderCallback
                }
                val expectedSize = activeWidth * activeHeight * 4
                val buffer = nativeBuffers[0].duplicate()
                buffer.rewind()
                val bytes = ByteArray(minOf(expectedSize, buffer.remaining()))
                buffer.get(bytes)
                if (bytes.size != expectedSize) return@RenderCallback
                val nextFrame = DesktopVlcVideoFrame(
                    width = activeWidth,
                    height = activeHeight,
                    bytes = bytes,
                    sequence = frameSequence.incrementAndGet(),
                )
                SwingUtilities.invokeLater {
                    if (!released) onFrame(nextFrame)
                }
            }
            nextPlayer.videoSurface().set(
                nextFactory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true),
            )
            factory = nextFactory
            mediaPlayer = nextPlayer
            DesktopVlcPlayerBridge.attach(this)
            nextPlayer
        }

    fun currentBrightness(): Float = desktopBrightness

    fun setBrightness(level: Float): Float {
        desktopBrightness = level.coerceIn(0f, 1f)
        mediaPlayer?.video()?.setAdjustVideo(true)
        mediaPlayer?.video()?.setBrightness(desktopBrightness * 2f)
        return desktopBrightness
    }

    override fun currentVolume(): PlayerAudioLevel {
        val volume = mediaPlayer?.audio()?.volume()?.coerceIn(0, 100) ?: 100
        return PlayerAudioLevel(
            fraction = volume / 100f,
            isMuted = volume == 0 || mediaPlayer?.audio()?.isMute == true,
        )
    }

    override fun setVolume(level: Float): PlayerAudioLevel {
        val volume = (level.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
        mediaPlayer?.audio()?.setMute(volume == 0)
        mediaPlayer?.audio()?.setVolume(volume)
        return currentVolume()
    }
}

internal fun selectableVlcTrackIdForUiIndex(trackIds: List<Int>, uiIndex: Int): Int? =
    trackIds
        .filter { it >= 0 }
        .getOrNull(uiIndex)

internal object DesktopVlcPlayerBridge {
    @Volatile
    private var activeController: DesktopVlcPlayerController? = null

    fun attach(controller: DesktopVlcPlayerController) {
        activeController = controller
    }

    fun detach(controller: DesktopVlcPlayerController) {
        if (activeController === controller) {
            activeController = null
        }
    }

    fun currentBrightness(): Float? = activeController?.currentBrightness()

    fun setBrightness(level: Float): Float? = activeController?.setBrightness(level)

    fun currentVolume(): PlayerAudioLevel? = activeController?.currentVolume()

    fun setVolume(level: Float): PlayerAudioLevel? = activeController?.setVolume(level)
}

internal data class DesktopVlcVideoFrame(
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
    val sequence: Long,
) {
    fun toImageBitmap() =
        SkiaImage.makeRaster(
            ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
            bytes,
            width * 4,
        ).toComposeImageBitmap()

    override fun equals(other: Any?): Boolean =
        other is DesktopVlcVideoFrame && sequence == other.sequence

    override fun hashCode(): Int = sequence.hashCode()
}

private fun buildVlcOptions(
    headers: Map<String, String>,
    playWhenReady: Boolean,
): List<String> =
    buildList {
        add(":network-caching=1500")
        add(":file-caching=1500")
        add(":live-caching=1500")
        add(":avcodec-hw=none")
        if (!playWhenReady) add(":start-paused")
        headers.findHeader("User-Agent")?.let { add(":http-user-agent=$it") }
        headers.findHeader("Referer", "Referrer")?.let { add(":http-referrer=$it") }
        headers.forEach { (key, value) ->
            if (!key.equals("User-Agent", ignoreCase = true) &&
                !key.equals("Referer", ignoreCase = true) &&
                !key.equals("Referrer", ignoreCase = true)
            ) {
                add(":http-header=$key: $value")
            }
        }
    }

private fun PlayerResizeMode.toContentScale(): ContentScale =
    when (this) {
        PlayerResizeMode.Fit -> ContentScale.Fit
        PlayerResizeMode.Fill -> ContentScale.Crop
        PlayerResizeMode.Zoom -> ContentScale.Crop
    }

private fun String.safeHostForLogs(): String =
    runCatching { URI(this).host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: substringBefore('?').substringBefore('/').take(80)

private fun Map<String, String>.findHeader(vararg names: String): String? =
    entries.firstOrNull { (key, _) ->
        names.any { name -> key.equals(name, ignoreCase = true) }
    }?.value
