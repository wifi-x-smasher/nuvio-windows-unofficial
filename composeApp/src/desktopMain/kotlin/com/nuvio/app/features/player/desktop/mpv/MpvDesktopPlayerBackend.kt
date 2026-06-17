package com.nuvio.app.features.player.desktop.mpv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.desktop.DesktopPlayerRegistry
import com.nuvio.app.desktop.DesktopRuntimeLog
import com.nuvio.app.features.experimental.ExperimentalFeatureSettings
import com.nuvio.app.features.player.AudioTrack
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerAudioLevel
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.player.PlayerSettingsRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.source.UriMediaData
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext

private const val ExternalSubtitleCodepage = "+utf-8"
private const val EmbeddedSubtitleCodepage = "auto"
private const val AppControlledSubtitleAssOverride = "force"
private const val NativeSubtitleAssOverride = "no"
private val VideoDiagnosticProbeDelaysMs = listOf(750L, 2500L, 6000L)

@OptIn(InternalMediampApi::class)
internal class MpvDesktopPlayerBackend private constructor(
    private val runtime: MpvRuntimeResolution,
    private val player: MpvMediampPlayer,
) : DesktopPlayerBackend {
    override val id: String = "windows-mpv-${System.identityHashCode(player)}"
    override val backendName: String = "windows-mediamp-mpv"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateFlow = MutableStateFlow(
        DesktopPlayerState(
            phase = DesktopPlayerPhase.Idle,
            backendName = backendName,
            diagnostics = runtime.diagnostics,
        ),
    )

    @Volatile private var stopped = false
    @Volatile private var nativeClosed = false
    @Volatile private var currentRequest: DesktopPlayerRequest? = null
    @Volatile private var externalSubtitleActive = false
    @Volatile private var latestSubtitleStyle = SubtitleStyleState.DEFAULT
    @Volatile private var playbackSpeed = 1.0f
    private val externalSubtitleRequestCounter = AtomicInteger(0)
    private val externalSubtitleTempFiles = mutableSetOf<Path>()
    private val subtitleHttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override val state: StateFlow<DesktopPlayerState> = stateFlow
    override val controller: PlayerEngineController = MpvController()

    init {
        applyDesktopVideoOutputProfile("init")
        observePlayerState()
        DesktopRuntimeLog.info("MPV backend created id=$id runtime=${runtime.directory?.safePath() ?: "none"}")
    }

    override suspend fun load(request: DesktopPlayerRequest) {
        if (nativeClosed) return
        if (request.sourceUrl.isBlank()) {
            fail(DesktopPlayerError.InvalidSource(backendName, "Blank source URL"))
            return
        }
        currentRequest = request
        stopped = false
        stateFlow.value = stateFlow.value.copy(phase = DesktopPlayerPhase.Preparing, error = null)
        runCatching {
            val headers = request.sourceHeaders.toMutableMap()
            DesktopRuntimeLog.info(
                "MPV load start session=${request.sessionKey} source=${request.sourceUrl.redactedMediaUrl()} " +
                    "audio=${request.sourceAudioUrl?.redactedMediaUrl() ?: "none"} " +
                    "streamType=${request.streamType ?: "none"} headersPresent=${headers.isNotEmpty()}",
            )
            resetExternalSubtitleState("load")
            applyDesktopVideoOutputProfile("load")
            applyDeclaredStreamType(request)
            player.setMediaData(UriMediaData(request.sourceUrl, headers))
            setResizeMode(request.resizeMode)
            if (request.playWhenReady) {
                player.resume()
                runCatching { player.impl.setPropertyBoolean("pause", false) }
                    .onFailure { DesktopRuntimeLog.error("MPV unpause after load failed", it) }
            } else {
                player.pause()
            }
            // Attach split trailer audio after MPV has loaded the main file; audio-add is rejected
            // while the core is still idle. This stays after play/pause so video start is not delayed.
            request.sourceAudioUrl?.takeIf { it.isNotBlank() }?.let { audioUrl ->
                runCatching {
                    player.impl.attachExternalAudio(audioUrl, reason = "load")
                }.onFailure {
                    DesktopRuntimeLog.error("MPV audio-add failed audio=${audioUrl.redactedMediaUrl()}", it)
                }
            }
            DesktopRuntimeLog.info("MPV load success session=${request.sessionKey}")
            logVideoOutputSnapshot("load-success")
            scheduleVideoOutputDiagnostics(request.sessionKey)
        }.onFailure { throwable ->
            DesktopRuntimeLog.error("MPV load failed source=${request.sourceUrl.redactedMediaUrl()}", throwable)
            fail(DesktopPlayerError.MediaLoadFailed(backendName, "MPV media load failed", throwable))
        }
    }

    override fun setResizeMode(resizeMode: PlayerResizeMode) {
        if (!canReceiveCommands()) return
        runCatching { player.impl.applyResizeMode(resizeMode) }
            .onSuccess { DesktopRuntimeLog.info("MPV resizeMode=$resizeMode applied") }
            .onFailure { DesktopRuntimeLog.error("MPV resizeMode=$resizeMode failed", it) }
    }

    override fun releaseSoft() {
        if (stopped) return
        stopped = true
        DesktopRuntimeLog.info("MPV releaseSoft id=$id")
        resetExternalSubtitleState("releaseSoft")
        runCatching { player.impl.setPropertyBoolean("mute", true) }
        runCatching { player.impl.command("stop") }
            .onFailure { DesktopRuntimeLog.error("MPV stop failed id=$id", it) }
        stateFlow.value = stateFlow.value.copy(phase = DesktopPlayerPhase.Closed)
    }

    override fun close() {
        if (nativeClosed) return
        resetExternalSubtitleState("close")
        nativeClosed = true
        scope.cancel()
        DesktopRuntimeLog.info("MPV close async id=$id")
        val thread = Thread({
            val startMs = System.currentTimeMillis()
            runCatching { player.close() }
                .onSuccess {
                    DesktopRuntimeLog.info("MPV native close done id=$id elapsedMs=${System.currentTimeMillis() - startMs}")
                }
                .onFailure { DesktopRuntimeLog.error("MPV native close failed id=$id", it) }
        }, "mpv-close-$id").apply { isDaemon = true }
        DesktopPlayerRegistry.trackCloseThread(thread)
        thread.start()
    }

    @Composable
    override fun Surface(modifier: Modifier) {
        MpvDesktopPlayerSurface(player = player, modifier = modifier)
    }

    private fun observePlayerState() {
        combine(
            player.playbackState,
            player.currentPositionMillis,
            player.mediaProperties,
        ) { playbackState, position, props ->
            val phase = playbackState.toDesktopPhase()
            DesktopPlayerState(
                phase = phase,
                positionMs = position,
                durationMs = props?.durationMillis?.takeIf { it > 0 } ?: 0L,
                bufferedPositionMs = 0L,
                playbackSpeed = currentPlaybackSpeed(),
                backendName = backendName,
                diagnostics = runtime.diagnostics,
                error = if (playbackState == PlaybackState.ERROR) {
                    DesktopPlayerError.PlaybackFailed(backendName, "MPV playback state is ERROR")
                } else {
                    null
                },
            )
        }.onEach { mapped ->
            if (!nativeClosed) {
                stateFlow.value = mapped
            }
        }.launchIn(scope)
    }

    private fun fail(error: DesktopPlayerError) {
        stateFlow.value = stateFlow.value.copy(
            phase = DesktopPlayerPhase.Error,
            error = error,
            diagnostics = error.technicalMessage,
        )
    }

    private fun canReceiveCommands(): Boolean =
        !stopped && !nativeClosed && player.getCurrentPlaybackState() != PlaybackState.FINISHED

    private fun durationMs(): Long? =
        player.mediaProperties.value?.durationMillis?.takeIf { it > 0L }

    private fun snapshotForLog(): String =
        "state=${player.getCurrentPlaybackState()} posMs=${player.currentPositionMillis.value} durationMs=${durationMs() ?: -1}"

    private fun currentPlaybackSpeed(): Float =
        player.impl.getMpvDoubleProperty("speed")
            ?.toFloat()
            ?.takeIf { it > 0f }
            ?: playbackSpeed

    private fun resetExternalSubtitleState(reason: String) {
        if (nativeClosed) return
        externalSubtitleRequestCounter.incrementAndGet()
        externalSubtitleActive = false
        clearExternalSubtitleTempFiles(reason)
        runCatching {
            player.impl.setMpvRuntimeOption("sub-codepage", EmbeddedSubtitleCodepage)
            player.impl.setMpvRuntimeOption("embeddedfonts", "yes")
            player.impl.setMpvRuntimeOption("sub-ass-override", NativeSubtitleAssOverride)
        }.onFailure { DesktopRuntimeLog.warn("MPV reset external subtitle state failed reason=$reason message=${it.message}") }
    }

    private fun applyDesktopVideoOutputProfile(reason: String) {
        if (nativeClosed) return
        runCatching {
            val upscalerPreset = ExperimentalFeatureSettings.videoUpscalerPreset.value
            val decoderBackend = ExperimentalFeatureSettings.videoDecoderBackend.value
            val displaySyncEnabled = ExperimentalFeatureSettings.displaySyncEnabled.value
            PlayerSettingsRepository.ensureLoaded()
            val decoderPriority = PlayerSettingsRepository.uiState.value.decoderPriority
            val applied = DesktopMpvVideoOptionProfile.optionsFor(
                upscalerPreset = upscalerPreset,
                decoderPriority = decoderPriority,
                decoderBackend = decoderBackend,
                displaySyncEnabled = displaySyncEnabled,
            ).mapValues { (name, value) ->
                player.impl.setMpvRuntimeOption(name, value)
            }
            DesktopRuntimeLog.info(
                "MPV video profile reason=$reason upscaler=${upscalerPreset.id} " +
                    "decoder=${DesktopMpvDecoderOptions.labelForBackend(decoderBackend, decoderPriority)} " +
                    "decoderBackend=${decoderBackend.id} decoderPriority=$decoderPriority " +
                    "displaySync=$displaySyncEnabled applied=$applied " +
                    "gpuNextAvailable=${DesktopMpvVideoOptionProfile.canRequestGpuNextRenderBackend} " +
                    "note=${DesktopMpvVideoOptionProfile.rendererLimitationNote}",
            )
        }.onFailure {
            DesktopRuntimeLog.warn("MPV video profile failed reason=$reason message=${it.message}")
        }
    }

    private fun applyDeclaredStreamType(request: DesktopPlayerRequest) {
        if (nativeClosed) return
        val normalizedType = request.streamType?.trim()?.lowercase()
        val forcedDemuxer = when (normalizedType) {
            "hls", "m3u8" -> "hls"
            else -> ""
        }
        runCatching {
            player.impl.setMpvRuntimeOption("demuxer-lavf-format", forcedDemuxer)
        }.onSuccess { applied ->
            if (forcedDemuxer.isNotBlank()) {
                DesktopRuntimeLog.info(
                    "MPV declared stream type session=${request.sessionKey} " +
                        "streamType=$normalizedType forcedDemuxer=$forcedDemuxer applied=$applied",
                )
            }
        }.onFailure {
            DesktopRuntimeLog.warn(
                "MPV declared stream type failed session=${request.sessionKey} " +
                    "streamType=${normalizedType ?: "none"} message=${it.message}",
            )
        }
    }

    private fun logVideoOutputSnapshot(reason: String) {
        if (nativeClosed) return
        runCatching {
            val handle = player.impl
            val props = linkedMapOf(
                "current-vo" to handle.getMpvStringProperty("current-vo"),
                "hwdec" to handle.getMpvStringProperty("hwdec"),
                "hwdec-current" to handle.getMpvStringProperty("hwdec-current"),
                "hwdec-codecs" to handle.getMpvStringProperty("hwdec-codecs"),
                "vd-lavc-software-fallback" to handle.getMpvStringProperty("vd-lavc-software-fallback"),
                "file-format" to handle.getMpvStringProperty("file-format"),
                "video-format" to handle.getMpvStringProperty("video-format"),
                "video-codec" to handle.getMpvStringProperty("video-codec"),
                "video-codec-name" to handle.getMpvStringProperty("video-codec-name"),
                "video-params/w" to handle.getMpvStringProperty("video-params/w"),
                "video-params/h" to handle.getMpvStringProperty("video-params/h"),
                "video-params/pixelformat" to handle.getMpvStringProperty("video-params/pixelformat"),
                "video-params/hw-pixelformat" to handle.getMpvStringProperty("video-params/hw-pixelformat"),
                "video-dec-params/pixelformat" to handle.getMpvStringProperty("video-dec-params/pixelformat"),
                "video-dec-params/hw-pixelformat" to handle.getMpvStringProperty("video-dec-params/hw-pixelformat"),
                "video-out-params/pixelformat" to handle.getMpvStringProperty("video-out-params/pixelformat"),
                "video-out-params/hw-pixelformat" to handle.getMpvStringProperty("video-out-params/hw-pixelformat"),
                "video-params/colormatrix" to handle.getMpvStringProperty("video-params/colormatrix"),
                "video-params/gamma" to handle.getMpvStringProperty("video-params/gamma"),
                "video-params/primaries" to handle.getMpvStringProperty("video-params/primaries"),
                "video-params/sig-peak" to handle.getMpvStringProperty("video-params/sig-peak"),
                "video-params/colorlevels" to handle.getMpvStringProperty("video-params/colorlevels"),
            )
            DesktopRuntimeLog.info("MPV video snapshot reason=$reason props=$props")
        }.onFailure {
            DesktopRuntimeLog.warn("MPV video snapshot failed reason=$reason message=${it.message}")
        }
    }

    private fun scheduleVideoOutputDiagnostics(sessionKey: String) {
        VideoDiagnosticProbeDelaysMs.forEach { delayMs ->
            scope.launch {
                delay(delayMs)
                if (nativeClosed || currentRequest?.sessionKey != sessionKey) return@launch
                logVideoOutputSnapshot("post-load-${delayMs}ms")
            }
        }
    }

    private inner class MpvController : PlayerEngineController {
        override fun play() {
            if (!canReceiveCommands()) return
            val before = snapshotForLog()
            val result = runCatching {
                player.resume()
                player.impl.setPropertyBoolean("pause", false)
            }
            DesktopRuntimeLog.info("MPV controller play before=$before result=${result.getOrNull()} after=${snapshotForLog()}")
            result.onFailure { DesktopRuntimeLog.error("MPV controller play failed", it) }
        }

        override fun pause() {
            if (!canReceiveCommands()) return
            val before = snapshotForLog()
            val result = runCatching { player.pause() }
            DesktopRuntimeLog.info("MPV controller pause before=$before result=${result.getOrNull()} after=${snapshotForLog()}")
            result.onFailure { DesktopRuntimeLog.error("MPV controller pause failed", it) }
        }

        override fun seekTo(positionMs: Long) {
            if (!canReceiveCommands()) return
            val durationMs = durationMs()
            val targetMs = positionMs.coerceAtLeast(0L).let { target -> durationMs?.let(target::coerceAtMost) ?: target }
            val before = snapshotForLog()
            val result = runCatching { player.impl.command("seek", (targetMs / 1000.0).toString(), "absolute+exact") }
            if (result.getOrNull() == true) player.currentPositionMillis.value = targetMs
            DesktopRuntimeLog.info(
                "MPV controller seekTo targetMs=$targetMs durationMs=${durationMs ?: -1} " +
                    "before=$before result=${result.getOrNull()} after=${snapshotForLog()}",
            )
            result.onFailure { DesktopRuntimeLog.error("MPV controller seekTo failed targetMs=$targetMs", it) }
        }

        override fun seekBy(offsetMs: Long) {
            if (!canReceiveCommands()) return
            seekTo(player.currentPositionMillis.value.coerceAtLeast(0L) + offsetMs)
        }

        override fun requestRedraw() {
            if (!canReceiveCommands()) return
            scope.launch {
                runCatching {
                    val handle = player.impl
                    // The surface logs vo-configured=true after a resize, but MediaMP renders on demand,
                    // so the freshly reallocated texture stays black until mpv presents a frame into it.
                    // A geometry-only property nudge (video-zoom) wasn't enough. Briefly toggling pause is:
                    // pausing makes mpv redraw the current frame (sets the render-update flag), which lands
                    // it in the new texture and clears the black surface on the first fullscreen/resize
                    // after playback starts. The original pause state is restored (~one frame, no seek).
                    val wasPaused = handle.getMpvBooleanProperty("pause") ?: false
                    handle.setPropertyBoolean("pause", !wasPaused)
                    delay(32)
                    handle.setPropertyBoolean("pause", wasPaused)
                    DesktopRuntimeLog.info("MPV controller requestRedraw pauseToggle wasPaused=$wasPaused")
                }.onFailure {
                    DesktopRuntimeLog.error("MPV controller requestRedraw failed", it)
                }
            }
        }

        override fun retry() = play()

        override fun setPlaybackSpeed(speed: Float) {
            if (!canReceiveCommands()) return
            val targetSpeed = speed.coerceIn(0.25f, 4.0f)
            val before = snapshotForLog()
            val result = runCatching {
                player.impl.setMpvProperty("speed", targetSpeed.toDouble()) ||
                    player.impl.command("set", "speed", targetSpeed.toString())
            }
            result
                .onSuccess { applied ->
                    if (applied) {
                        playbackSpeed = targetSpeed
                        stateFlow.value = stateFlow.value.copy(playbackSpeed = targetSpeed)
                    }
                    DesktopRuntimeLog.info(
                        "MPV controller setPlaybackSpeed target=$targetSpeed applied=$applied " +
                            "before=$before after=${snapshotForLog()}",
                    )
                }
                .onFailure { DesktopRuntimeLog.error("MPV controller setPlaybackSpeed failed target=$targetSpeed", it) }
        }

        override fun currentVolume(): PlayerAudioLevel? {
            if (!canReceiveCommands()) return null
            val volume = player.impl.getMpvDoubleProperty("volume")
                ?.div(100.0)
                ?.toFloat()
                ?.coerceIn(0f, 1f)
                ?: 1f
            val muted = player.impl.getMpvBooleanProperty("mute")
            return PlayerAudioLevel(
                fraction = volume,
                isMuted = muted || volume <= 0.01f,
            )
        }

        override fun setVolume(level: Float): PlayerAudioLevel? {
            if (!canReceiveCommands()) return null
            val volume = level.coerceIn(0f, 1f)
            val result = runCatching {
                player.impl.setMpvProperty("volume", volume * 100.0)
                player.impl.setMpvProperty("mute", volume <= 0.01f)
            }
            result.onFailure { DesktopRuntimeLog.error("MPV setVolume failed level=$level", it) }
            return currentVolume()
        }

        override fun getAudioTracks(): List<AudioTrack> =
            if (canReceiveCommands()) runCatching { player.impl.audioTracks() }.getOrDefault(emptyList()) else emptyList()

        override fun getSubtitleTracks(): List<SubtitleTrack> =
            if (canReceiveCommands()) runCatching { player.impl.subtitleTracks() }.getOrDefault(emptyList()) else emptyList()

        override fun selectAudioTrack(index: Int) {
            if (!canReceiveCommands()) return
            val tracks = getAudioTracks()
            if (index in tracks.indices) {
                runCatching { player.impl.setMpvProperty("aid", tracks[index].id) }
                    .onFailure { DesktopRuntimeLog.error("MPV selectAudioTrack failed index=$index", it) }
            }
        }

        override fun selectSubtitleTrack(index: Int) {
            if (!canReceiveCommands()) return
            if (index < 0) {
                runCatching {
                    externalSubtitleActive = false
                    player.impl.setMpvProperty("sid", "no")
                    applySubtitleStyleToCurrentTrack(latestSubtitleStyle, reason = "select-none")
                }
                return
            }
            val tracks = getSubtitleTracks()
            if (index in tracks.indices) {
                runCatching {
                    externalSubtitleActive = false
                    player.impl.setMpvProperty("sid", tracks[index].id)
                    applySubtitleStyleToCurrentTrack(latestSubtitleStyle, reason = "select-built-in")
                }
                    .onFailure { DesktopRuntimeLog.error("MPV selectSubtitleTrack failed index=$index", it) }
            }
        }

        override fun setSubtitleUri(url: String) {
            if (!canReceiveCommands()) return
            val requestId = externalSubtitleRequestCounter.incrementAndGet()
            removeExternalSubtitleTracks(cancelPendingRequest = false, reason = "replace-external")
            runCatching {
                externalSubtitleActive = true
                player.impl.setMpvRuntimeOption("sub-codepage", ExternalSubtitleCodepage)
                player.impl.setMpvRuntimeOption("sub-visibility", "yes")
                applySubtitleStyleToCurrentTrack(latestSubtitleStyle, reason = "set-external-preload")
            }
                .onFailure { DesktopRuntimeLog.error("MPV setSubtitleUri failed url=${url.redactedMediaUrl()}", it) }
            scope.launch(Dispatchers.IO) {
                val subtitleRef = runCatching { prepareExternalSubtitleReference(url) }
                    .onFailure {
                        DesktopRuntimeLog.warn(
                            "MPV external subtitle normalization failed url=${url.redactedMediaUrl()} message=${it.message}; using original URL",
                        )
                    }
                    .getOrDefault(url)
                if (requestId != externalSubtitleRequestCounter.get() || !canReceiveCommands()) {
                    DesktopRuntimeLog.info("MPV external subtitle add skipped stale request url=${url.redactedMediaUrl()}")
                    return@launch
                }
                runCatching {
                    player.impl.setMpvRuntimeOption("sub-codepage", ExternalSubtitleCodepage)
                    player.impl.setMpvRuntimeOption("sub-visibility", "yes")
                    player.impl.command("sub-add", subtitleRef, "select")
                    selectNewestExternalSubtitle()
                    applySubtitleStyleToCurrentTrack(latestSubtitleStyle, reason = "set-external")
                    reapplySubtitleStyleAfterTrackSettle(requestId, reason = "set-external")
                }.onFailure {
                    DesktopRuntimeLog.error("MPV setSubtitleUri failed url=${url.redactedMediaUrl()}", it)
                }
            }
        }

        override fun clearExternalSubtitle() {
            removeExternalSubtitleTracks(cancelPendingRequest = true, reason = "clear-external")
        }

        private fun removeExternalSubtitleTracks(cancelPendingRequest: Boolean, reason: String) {
            if (!canReceiveCommands()) return
            if (cancelPendingRequest) {
                externalSubtitleRequestCounter.incrementAndGet()
            }
            val handle = player.impl
            val hadExternalSubtitle = externalSubtitleActive
            val count = handle.getMpvIntProperty("track-list/count")
            if (count == null) {
                if (hadExternalSubtitle) {
                    externalSubtitleActive = false
                    applySubtitleStyleToCurrentTrack(latestSubtitleStyle, reason = "$reason-missing-track-list")
                }
                clearExternalSubtitleTempFiles("$reason-missing-track-list")
                return
            }
            for (i in count - 1 downTo 0) {
                val type = handle.getMpvStringProperty("track-list/$i/type")
                val external = handle.getMpvBooleanProperty("track-list/$i/external")
                if (type == "sub" && external) {
                    val id = handle.getMpvIntProperty("track-list/$i/id") ?: continue
                    runCatching { handle.command("sub-remove", id.toString()) }
                }
            }
            if (hadExternalSubtitle) {
                externalSubtitleActive = false
                applySubtitleStyleToCurrentTrack(latestSubtitleStyle, reason = reason)
            }
            clearExternalSubtitleTempFiles(reason)
        }

        private fun selectNewestExternalSubtitle() {
            val handle = player.impl
            val count = handle.getMpvIntProperty("track-list/count") ?: return
            var newestExternalSubtitleId: Int? = null
            for (i in 0 until count) {
                val type = handle.getMpvStringProperty("track-list/$i/type")
                val external = handle.getMpvBooleanProperty("track-list/$i/external")
                if (type == "sub" && external) {
                    newestExternalSubtitleId = handle.getMpvIntProperty("track-list/$i/id") ?: newestExternalSubtitleId
                }
            }
            newestExternalSubtitleId?.let { id ->
                handle.setMpvProperty("sid", id.toString())
            }
        }

        override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
            clearExternalSubtitle()
            selectSubtitleTrack(trackIndex)
        }

        override fun applySubtitleStyle(style: SubtitleStyleState) {
            latestSubtitleStyle = style
            if (!canReceiveCommands()) return
            applySubtitleStyleToCurrentTrack(style, reason = "settings")
        }

        override fun setSubtitleDelayMillis(delayMillis: Int) {
            if (!canReceiveCommands()) return
            val clamped = com.nuvio.app.features.player.clampSubtitleDelayMillis(delayMillis)
            val seconds = com.nuvio.app.features.player.subtitleDelayMillisToMpvSeconds(clamped)
            runCatching {
                player.impl.setMpvProperty("sub-delay", seconds)
            }.onSuccess {
                DesktopRuntimeLog.info("MPV subtitle delay applied delayMs=$clamped seconds=$seconds")
            }.onFailure {
                DesktopRuntimeLog.error("MPV subtitle delay failed delayMs=$clamped", it)
            }
        }

        private fun applySubtitleStyleToCurrentTrack(style: SubtitleStyleState, reason: String) {
            if (!canReceiveCommands()) return
            val handle = player.impl
            val colorHex = style.textColor.toMpvSubtitleColorString()
            val outline = if (style.outlineEnabled) 2.0 else 0.0
            val subPos = 100 - style.bottomOffset
            runCatching {
                val selectedTrack = handle.selectedSubtitleTrackDetails()
                val styleMode = MpvSubtitleStyleMode(
                    trackCodec = selectedTrack?.codec,
                    externalSubtitleActive = externalSubtitleActive,
                )
                val useAppSubtitleStyle = styleMode == MpvSubtitleStyleMode.AppControlled
                val assOverrideMode = styleMode.assOverride
                val codepage = if (externalSubtitleActive || selectedTrack?.external == true) {
                    ExternalSubtitleCodepage
                } else {
                    EmbeddedSubtitleCodepage
                }

                // Text subtitles should follow the visible Nuvio style controls. Bitmap/image
                // subtitle formats such as PGS cannot be recolored/resized by mpv, so keep their
                // native rendering and make that visible in diagnostics instead of pretending.
                handle.setMpvRuntimeOption("sub-codepage", codepage)
                handle.setMpvRuntimeOption("embeddedfonts", "yes")
                handle.setMpvRuntimeOption("sub-ass-override", assOverrideMode)
                val appliedStyleProperties = if (useAppSubtitleStyle) {
                    handle.applyLiveSubtitleStyle(
                        colorHex = colorHex,
                        outline = outline,
                        fontSizeSp = style.fontSizeSp,
                        subPos = subPos,
                    )
                } else {
                    emptyMap()
                }

                DesktopRuntimeLog.info(
                    "MPV applySubtitleStyle selected=${selectedTrack?.toLogString() ?: "none"} " +
                        "reason=$reason assOverride=$assOverrideMode codepage=$codepage " +
                        "embeddedfonts=yes externalActive=$externalSubtitleActive " +
                        "appStyleTarget=${styleMode.logLabel} styleApplied=$appliedStyleProperties " +
                        "styleReadback=${if (useAppSubtitleStyle) handle.subtitleStyleReadback() else "native"}",
                )
            }.onFailure { DesktopRuntimeLog.error("MPV applySubtitleStyle failed", it) }
        }

        private fun reapplySubtitleStyleAfterTrackSettle(requestId: Int, reason: String) {
            scope.launch {
                repeat(3) { attempt ->
                    delay(250L * (attempt + 1))
                    if (requestId != externalSubtitleRequestCounter.get() || !canReceiveCommands()) {
                        return@launch
                    }
                    applySubtitleStyleToCurrentTrack(
                        style = latestSubtitleStyle,
                        reason = "$reason-settled-${attempt + 1}",
                    )
                }
            }
        }

        fun switchSource(url: String, audioUrl: String?, headersJson: String?) {
            if (!canReceiveCommands()) return
            val previous = currentRequest ?: return
            val headers = parseHeadersJson(headersJson).ifEmpty { previous.sourceHeaders }
            DesktopRuntimeLog.info(
                "MPV switchSource reloadInPlace url=${url.redactedMediaUrl()} " +
                    "audio=${audioUrl?.redactedMediaUrl() ?: "none"} headersPresent=${headers.isNotEmpty()}",
            )
            scope.launch {
                load(
                    previous.copy(
                        sourceUrl = url,
                        sourceAudioUrl = audioUrl,
                        sourceHeaders = headers,
                        playWhenReady = true,
                    ),
                )
            }
        }
    }

    private fun prepareExternalSubtitleReference(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val scheme = uri.scheme?.lowercase() ?: return url
        if (scheme != "http" && scheme != "https") return url
        val request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "NuvioDesktop/1.0")
            .build()
        val response = subtitleHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()}")
        }
        val text = response.body().decodeExternalSubtitleText()
        val extension = subtitleExtension(uri, text)
        val file = Files.createTempFile("nuvio-external-subtitle-", extension)
        Files.write(file, text.toByteArray(StandardCharsets.UTF_8))
        synchronized(externalSubtitleTempFiles) {
            externalSubtitleTempFiles.add(file)
        }
        DesktopRuntimeLog.info(
            "MPV external subtitle normalized url=${url.redactedMediaUrl()} temp=${file.toSafeLogPath()} extension=$extension",
        )
        return file.toUri().toString()
    }

    private fun ByteArray.decodeExternalSubtitleText(): String {
        val bytes = dropUtf8Bom()
        val decoded = runCatching { bytes.decodeStrictUtf8() }.getOrElse {
            String(bytes, Charset.forName("windows-1252"))
        }
        return decoded.repairCommonMojibake()
    }

    private fun ByteArray.dropUtf8Bom(): ByteArray =
        if (size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()) {
            copyOfRange(3, size)
        } else {
            this
        }

    private fun ByteArray.decodeStrictUtf8(): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(this))
            .toString()

    private fun String.repairCommonMojibake(): String {
        if ('Ã' !in this && 'Â' !in this && '�' !in this) return this
        if (any { it.code > 255 }) return this
        val repaired = runCatching {
            String(toByteArray(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
        }.getOrNull() ?: return this
        return if (repaired.mojibakeScore() < mojibakeScore()) repaired else this
    }

    private fun String.mojibakeScore(): Int =
        count { it == 'Ã' || it == 'Â' || it == '�' }

    private fun subtitleExtension(uri: URI, text: String): String {
        val pathExtension = uri.path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when {
            pathExtension in setOf("ass", "ssa", "srt", "vtt") -> ".$pathExtension"
            text.trimStart().startsWith("[Script Info]", ignoreCase = true) -> ".ass"
            text.trimStart().startsWith("WEBVTT", ignoreCase = true) -> ".vtt"
            else -> ".srt"
        }
    }

    private fun clearExternalSubtitleTempFiles(reason: String) {
        val files = synchronized(externalSubtitleTempFiles) {
            externalSubtitleTempFiles.toList().also { externalSubtitleTempFiles.clear() }
        }
        files.forEach { file ->
            runCatching { Files.deleteIfExists(file) }
                .onFailure {
                    DesktopRuntimeLog.warn(
                        "MPV external subtitle temp cleanup failed reason=$reason file=${file.toSafeLogPath()} message=${it.message}",
                    )
                }
        }
    }

    private fun MPVHandle.setMpvRuntimeOption(name: String, value: Any): Boolean {
        val stringValue = value.toString()
        return command("set", name, stringValue) || option(name, stringValue) || setMpvProperty(name, value)
    }

    private fun MPVHandle.applyLiveSubtitleStyle(
        colorHex: String,
        outline: Double,
        fontSizeSp: Int,
        subPos: Int,
    ): Map<String, Boolean> = linkedMapOf(
        "sub-color" to setMpvProperty("sub-color", colorHex),
        "sub-border-size" to setMpvProperty("sub-border-size", outline),
        "sub-font-size" to setMpvProperty("sub-font-size", fontSizeSp.toDouble()),
        "sub-pos" to setMpvProperty("sub-pos", subPos),
        "sub-align-y" to setMpvProperty("sub-align-y", "bottom"),
    )

    private fun MPVHandle.subtitleStyleReadback(): String =
        listOf(
            "sub-color=${getMpvStringProperty("sub-color")}",
            "sub-border-size=${getMpvStringProperty("sub-border-size")}",
            "sub-font-size=${getMpvStringProperty("sub-font-size")}",
            "sub-pos=${getMpvStringProperty("sub-pos")}",
            "sub-align-y=${getMpvStringProperty("sub-align-y")}",
        ).joinToString(prefix = "[", postfix = "]")

    /**
     * Waits until MPV has actually loaded the primary file (its track-list becomes non-empty).
     * MPV's `audio-add` is a runtime command that is rejected while the core is still idle, so the
     * external audio stream must only be attached after the main file is loaded. Bounded by a
     * timeout so a stuck/invalid source can never block playback indefinitely.
     */
    private suspend fun MPVHandle.awaitFileLoaded(reason: String, timeoutMs: Long = 6000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if ((getMpvIntProperty("track-list/count") ?: 0) > 0) return true
            delay(100L)
        }
        DesktopRuntimeLog.warn("MPV file load wait timed out reason=$reason timeoutMs=$timeoutMs")
        return false
    }

    /**
     * Attaches a separate audio stream by waiting for the main file, issuing `audio-add`, then
     * selecting the newest external audio track.
     */
    private suspend fun MPVHandle.attachExternalAudio(audioUrl: String, reason: String) {
        val loaded = awaitFileLoaded(reason)
        for (attempt in 0 until 4) {
            if (newestExternalAudioTrackDetails() != null) break
            val select = runCatching { command("audio-add", audioUrl, "select") }.getOrDefault(false)
            val auto = !select && runCatching { command("audio-add", audioUrl, "auto") }.getOrDefault(false)
            DesktopRuntimeLog.info(
                "MPV audio-add attempt=${attempt + 1} reason=$reason loaded=$loaded " +
                    "audio=${audioUrl.redactedMediaUrl()} select=$select autoFallback=$auto",
            )
            if (select || auto) break
            delay(150L * (attempt + 1))
        }
        ensureExternalAudioSelected(audioUrl, reason)
    }

    private suspend fun MPVHandle.ensureExternalAudioSelected(audioUrl: String, reason: String) {
        repeat(5) { attempt ->
            val selected = selectedAudioTrackDetails()
            val external = newestExternalAudioTrackDetails()
            if (selected?.external == true) {
                DesktopRuntimeLog.info(
                    "MPV external audio selected reason=$reason attempt=${attempt + 1} " +
                        "selected=${selected.toLogString()} audio=${audioUrl.redactedMediaUrl()}",
                )
                return
            }
            if (external != null) {
                val selectedAid = setMpvProperty("aid", external.id)
                DesktopRuntimeLog.info(
                    "MPV external audio select reason=$reason attempt=${attempt + 1} " +
                        "selectedAid=$selectedAid track=${external.toLogString()} audio=${audioUrl.redactedMediaUrl()}",
                )
                return
            }
            delay(120L * (attempt + 1))
        }
        DesktopRuntimeLog.warn(
            "MPV external audio not found reason=$reason audio=${audioUrl.redactedMediaUrl()} " +
                "aid=${getMpvStringProperty("aid")} tracks=${audioTrackDiagnostics()}",
        )
    }

    private fun Path.toSafeLogPath(): String =
        runCatching { toAbsolutePath().toString() }.getOrDefault(toString())

    private data class MpvAudioTrackDetails(
        val id: Int,
        val external: Boolean,
        val title: String,
        val language: String,
    ) {
        fun toLogString(): String =
            "id=$id external=$external title=${title.ifBlank { "none" }} lang=${language.ifBlank { "none" }}"
    }

    private fun org.openani.mediamp.mpv.MPVHandle.selectedAudioTrackDetails(): MpvAudioTrackDetails? {
        val count = getMpvIntProperty("track-list/count") ?: return null
        for (i in 0 until count) {
            if (getMpvStringProperty("track-list/$i/type") != "audio") continue
            if (!getMpvBooleanProperty("track-list/$i/selected")) continue
            val id = getMpvIntProperty("track-list/$i/id") ?: continue
            return MpvAudioTrackDetails(
                id = id,
                external = getMpvBooleanProperty("track-list/$i/external"),
                title = getMpvStringProperty("track-list/$i/title"),
                language = getMpvStringProperty("track-list/$i/lang"),
            )
        }
        return null
    }

    private fun org.openani.mediamp.mpv.MPVHandle.newestExternalAudioTrackDetails(): MpvAudioTrackDetails? {
        val count = getMpvIntProperty("track-list/count") ?: return null
        var newest: MpvAudioTrackDetails? = null
        for (i in 0 until count) {
            if (getMpvStringProperty("track-list/$i/type") != "audio") continue
            if (!getMpvBooleanProperty("track-list/$i/external")) continue
            val id = getMpvIntProperty("track-list/$i/id") ?: continue
            newest = MpvAudioTrackDetails(
                id = id,
                external = true,
                title = getMpvStringProperty("track-list/$i/title"),
                language = getMpvStringProperty("track-list/$i/lang"),
            )
        }
        return newest
    }

    private fun org.openani.mediamp.mpv.MPVHandle.audioTrackDiagnostics(): String {
        val count = getMpvIntProperty("track-list/count") ?: return "unavailable"
        val tracks = buildList {
            for (i in 0 until count) {
                if (getMpvStringProperty("track-list/$i/type") != "audio") continue
                val id = getMpvIntProperty("track-list/$i/id") ?: continue
                add(
                    "id=$id external=${getMpvBooleanProperty("track-list/$i/external")} " +
                        "selected=${getMpvBooleanProperty("track-list/$i/selected")} " +
                        "lang=${getMpvStringProperty("track-list/$i/lang").ifBlank { "none" }}",
                )
            }
        }
        return tracks.joinToString(prefix = "[", postfix = "]")
    }

    private data class MpvSubtitleTrackDetails(
        val id: Int,
        val codec: String,
        val external: Boolean,
        val title: String,
        val language: String,
    ) {
        fun toLogString(): String =
            "id=$id codec=${codec.ifBlank { "unknown" }} external=$external " +
                "title=${title.ifBlank { "none" }} lang=${language.ifBlank { "none" }}"
    }

    private fun org.openani.mediamp.mpv.MPVHandle.selectedSubtitleTrackDetails(): MpvSubtitleTrackDetails? {
        val count = getMpvIntProperty("track-list/count") ?: return null
        for (i in 0 until count) {
            if (getMpvStringProperty("track-list/$i/type") != "sub") continue
            if (!getMpvBooleanProperty("track-list/$i/selected")) continue
            val id = getMpvIntProperty("track-list/$i/id") ?: continue
            return MpvSubtitleTrackDetails(
                id = id,
                codec = getMpvStringProperty("track-list/$i/codec"),
                external = getMpvBooleanProperty("track-list/$i/external"),
                title = getMpvStringProperty("track-list/$i/title"),
                language = getMpvStringProperty("track-list/$i/lang"),
            )
        }
        return null
    }

    companion object {
        fun create(runtime: MpvRuntimeResolution): Result<MpvDesktopPlayerBackend> =
            runCatching {
                MpvDesktopPlayerBackend(
                    runtime = runtime,
                    player = MpvMediampPlayer(Unit, EmptyCoroutineContext),
                )
            }
    }
}

private fun parseHeadersJson(headersJson: String?): Map<String, String> {
    if (headersJson.isNullOrBlank()) return emptyMap()
    return runCatching {
        Json.parseToJsonElement(headersJson).jsonObject.mapNotNull { (key, value) ->
            val primitive = value as? JsonPrimitive ?: return@mapNotNull null
            val content = primitive.jsonPrimitive.content.trim()
            if (key.isBlank() || content.isBlank()) null else key.trim() to content
        }.toMap()
    }.getOrDefault(emptyMap())
}

internal enum class MpvSubtitleStyleMode(
    val assOverride: String,
    val logLabel: String,
) {
    AppControlled(AppControlledSubtitleAssOverride, "app-controlled-text"),
    Native(NativeSubtitleAssOverride, "native-bitmap-or-image"),
}

internal fun MpvSubtitleStyleMode(trackCodec: String?, externalSubtitleActive: Boolean): MpvSubtitleStyleMode {
    if (externalSubtitleActive) return MpvSubtitleStyleMode.AppControlled
    val normalized = trackCodec
        ?.lowercase()
        ?.replace('-', '_')
        ?.trim()
        ?: return MpvSubtitleStyleMode.AppControlled

    return if (normalized in imageSubtitleCodecs) {
        MpvSubtitleStyleMode.Native
    } else {
        MpvSubtitleStyleMode.AppControlled
    }
}

private val imageSubtitleCodecs = setOf(
    "hdmv_pgs_subtitle",
    "pgs",
    "dvd_subtitle",
    "dvb_subtitle",
    "xsub",
)
