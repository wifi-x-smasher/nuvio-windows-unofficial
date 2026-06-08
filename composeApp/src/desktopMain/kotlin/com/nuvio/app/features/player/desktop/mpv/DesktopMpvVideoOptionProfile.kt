package com.nuvio.app.features.player.desktop.mpv

import com.nuvio.app.features.experimental.VideoUpscalerPreset

internal object DesktopMpvVideoOptionProfile {
    val options: Map<String, String> = linkedMapOf(
        "hwdec" to "auto-safe",
        "fbo-format" to "rgba8",
        "dither-depth" to "no",
        "video-sync" to "audio",
        "video-timing-offset" to "0.0",
    )

    fun optionsFor(upscalerPreset: VideoUpscalerPreset): Map<String, String> =
        options + DesktopMpvUpscalerOptions.optionsFor(upscalerPreset)

    const val canRequestGpuNextRenderBackend: Boolean = false

    const val rendererLimitationNote: String =
        "Bundled MediaMP/libmpv uses the OpenGL/libmpv render path; Windows output options follow the CreepsoOff 0.2.1 MediaMP baseline."
}
