package com.nuvio.app.features.player.desktop.mpv

internal object DesktopMpvDecoderOptions {
    private const val DeviceOnly = 0
    private const val PreferDevice = 1
    private const val PreferApp = 2

    fun optionsFor(priority: Int): Map<String, String> = when (priority) {
        DeviceOnly -> linkedMapOf(
            "hwdec" to "auto",
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
        "hwdec" to "auto-safe",
        "vd-lavc-software-fallback" to "yes",
    )
}
