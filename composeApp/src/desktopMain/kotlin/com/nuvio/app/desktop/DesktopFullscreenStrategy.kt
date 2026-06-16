package com.nuvio.app.desktop

internal enum class DesktopFullscreenStrategy(val configValue: String) {
    ManualBorderlessBounds("manual-borderless-bounds"),
    WorkAreaMaximized("work-area-maximized"),
    Win32BorderlessSetWindowPos("win32-borderless-set-window-pos"),
    PlayerWindowOnly("player-window-only"),
    ;

    companion object {
        private const val propertyName = "nuvio.fullscreen.strategy"
        private const val environmentName = "NUVIO_FULLSCREEN_STRATEGY"

        fun resolve(): DesktopFullscreenStrategy =
            fromConfigValue(System.getProperty(propertyName))
                ?: fromConfigValue(System.getenv(environmentName))
                ?: defaultForCurrentDesktop()

        internal fun fromConfigValue(value: String?): DesktopFullscreenStrategy? {
            val normalized = value
                ?.trim()
                ?.lowercase()
                ?.replace('_', '-')
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return entries.firstOrNull { strategy ->
                strategy.configValue == normalized || strategy.name.lowercase() == normalized
            }
        }

        private fun defaultForCurrentDesktop(): DesktopFullscreenStrategy =
            if (System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)) {
                Win32BorderlessSetWindowPos
            } else {
                ManualBorderlessBounds
            }
    }
}
