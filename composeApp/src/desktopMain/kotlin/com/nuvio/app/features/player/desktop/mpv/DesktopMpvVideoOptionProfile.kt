package com.nuvio.app.features.player.desktop.mpv

internal object DesktopMpvVideoOptionProfile {
    val options: Map<String, String> = linkedMapOf(
        "hwdec" to "auto-safe",
        "fbo-format" to "rgba8",
        "dither-depth" to "no",
        "video-sync" to "audio",
        "video-timing-offset" to "0.0",
    )

    const val canRequestGpuNextRenderBackend: Boolean = false

    const val rendererLimitationNote: String =
        "Bundled MediaMP/libmpv uses the OpenGL/libmpv render path; Windows output options follow the CreepsoOff 0.2.1 MediaMP baseline."
}
