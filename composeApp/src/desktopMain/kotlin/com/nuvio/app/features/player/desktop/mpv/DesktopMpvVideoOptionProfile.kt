package com.nuvio.app.features.player.desktop.mpv

import com.nuvio.app.features.experimental.VideoDecoderBackend
import com.nuvio.app.features.experimental.VideoUpscalerPreset

internal object DesktopMpvVideoOptionProfile {
    private val baseOptions: Map<String, String> = linkedMapOf(
        "fbo-format" to "rgba8",
        "dither-depth" to "no",
        "video-sync" to "audio",
        "video-timing-offset" to "0.0",
    )

    val options: Map<String, String> = optionsFor(
        upscalerPreset = VideoUpscalerPreset.OFF,
        decoderPriority = 1,
    )

    fun optionsFor(
        upscalerPreset: VideoUpscalerPreset,
        decoderPriority: Int,
        decoderBackend: VideoDecoderBackend = VideoDecoderBackend.AUTO,
        displaySyncEnabled: Boolean = false,
    ): Map<String, String> {
        val base = baseOptions +
            DesktopMpvDecoderOptions.optionsForBackend(
                backend = decoderBackend,
                decoderPriority = decoderPriority,
            ) +
            DesktopMpvUpscalerOptions.optionsFor(upscalerPreset)
        return if (displaySyncEnabled) {
            base + linkedMapOf(
                "video-sync" to "display-resample",
                "interpolation" to "yes",
                "tscale" to "oversample",
            )
        } else {
            base
        }
    }

    const val canRequestGpuNextRenderBackend: Boolean = false

    const val rendererLimitationNote: String =
        "Bundled MediaMP/libmpv uses the OpenGL/libmpv render path; Windows output options follow the CreepsoOff 0.2.1 MediaMP baseline."
}
