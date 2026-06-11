package com.nuvio.app.features.player.desktop.mpv

internal object DesktopMpvDecoderOptions {
    private const val DeviceOnly = 0
    private const val PreferDevice = 1
    private const val PreferApp = 2

    // IMPORTANT (issue #20): the desktop player renders via libmpv's OpenGL render API into a native
    // WGL GL context (vendor mpv_handle_t.cpp uses MPV_RENDER_API_TYPE_OPENGL + opengl32.dll). On
    // Windows the default hardware decoder is zero-copy d3d11va, whose D3D11 decode surfaces CANNOT be
    // shared into a native-GL texture (that needs ANGLE/D3D interop, which we don't use). With a
    // zero-copy mode mpv can't map the frame, rejects the hwdec, and falls back to software decoding
    // (hwdec-current=no) — which murders performance on 4K. So we force *copy-back* modes (auto-copy*):
    // the GPU still decodes, the frame is copied to system memory, then uploaded to the GL texture.
    // No interop required; works with any renderer; still hugely faster than CPU software decoding.

    fun optionsFor(priority: Int): Map<String, String> = when (priority) {
        DeviceOnly -> linkedMapOf(
            "hwdec" to "auto-copy",
            "vd-lavc-software-fallback" to "yes",
        )
        PreferApp -> linkedMapOf(
            "hwdec" to "no",
            "vd-lavc-software-fallback" to "yes",
        )
        PreferDevice -> preferDeviceOptions()
        else -> preferDeviceOptions()
    }

    fun labelFor(priority: Int): String = when (priority) {
        DeviceOnly -> "device-only"
        PreferApp -> "prefer-app"
        PreferDevice -> "prefer-device"
        else -> "prefer-device"
    }

    private fun preferDeviceOptions(): Map<String, String> = linkedMapOf(
        "hwdec" to "auto-copy-safe",
        "vd-lavc-software-fallback" to "yes",
    )
}
