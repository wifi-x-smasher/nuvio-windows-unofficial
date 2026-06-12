package com.nuvio.app.features.player.desktop.mpv

import com.nuvio.app.features.experimental.VideoDecoderBackend

internal object DesktopMpvDecoderOptions {
    private const val DeviceOnly = 0
    private const val PreferDevice = 1
    private const val PreferApp = 2

    // IMPORTANT (issue #20): the desktop player renders via libmpv's OpenGL render API into a native
    // WGL GL context (vendor mpv_handle_t.cpp uses MPV_RENDER_API_TYPE_OPENGL + opengl32.dll). On
    // Windows the default hardware decoder is zero-copy d3d11va, whose D3D11 decode surfaces CANNOT be
    // shared into a native-GL texture (that needs ANGLE/D3D interop, which we don't use). With a
    // zero-copy mode mpv can't map the frame, rejects the hwdec, and falls back to software decoding
    // (hwdec-current=no) — which murders performance on 4K. So we use a *copy-back* mode: the GPU
    // decodes, the frame is copied to system memory, then uploaded to the GL texture. No interop
    // required; still hugely faster than CPU software decoding.
    //
    // We use auto-copy-safe (not a pinned decoder): it lets mpv choose the best copy-back decoder per
    // platform/GPU — nvdec-copy on NVIDIA, d3d11va-copy on Intel/AMD. Logs confirmed that on an NVIDIA
    // dGPU this engages nvdec-copy for H.264 and d3d11va-copy for HEVC. The earlier "4K HEVC software
    // decodes" symptom was the process running on the integrated GPU, not a decoder-selection issue;
    // once the app runs on the discrete GPU, auto-copy-safe hardware-decodes correctly. (Dolby Vision
    // still software-decodes by design — mpv handles the DV RPU on the CPU.)

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

    fun optionsForBackend(
        backend: VideoDecoderBackend,
        decoderPriority: Int = PreferDevice,
    ): Map<String, String> = when (backend) {
        VideoDecoderBackend.AUTO -> optionsFor(decoderPriority)
        VideoDecoderBackend.D3D11VA_COPY -> copyBackOptions("d3d11va-copy")
        VideoDecoderBackend.NVDEC_COPY -> copyBackOptions("nvdec-copy")
        VideoDecoderBackend.SOFTWARE -> linkedMapOf(
            "hwdec" to "no",
            "vd-lavc-software-fallback" to "yes",
        )
    }

    fun labelFor(priority: Int): String = when (priority) {
        DeviceOnly -> "device-only"
        PreferApp -> "prefer-app"
        PreferDevice -> "prefer-device"
        else -> "prefer-device"
    }

    fun labelForBackend(
        backend: VideoDecoderBackend,
        decoderPriority: Int = PreferDevice,
    ): String = when (backend) {
        VideoDecoderBackend.AUTO -> "auto/${labelFor(decoderPriority)}"
        VideoDecoderBackend.D3D11VA_COPY -> "d3d11va-copy"
        VideoDecoderBackend.NVDEC_COPY -> "nvdec-copy"
        VideoDecoderBackend.SOFTWARE -> "software"
    }

    private fun preferDeviceOptions(): Map<String, String> = copyBackOptions("auto-copy-safe")

    private fun copyBackOptions(hwdec: String): Map<String, String> = linkedMapOf(
        "hwdec" to hwdec,
        "vd-lavc-software-fallback" to "yes",
    )
}
