package com.nuvio.app.desktop

import java.awt.DisplayMode
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal data class DesktopDisplaySnapshot(
    val screenCount: Int,
    val activeBounds: String,
    val activeScaleX: Double,
    val activeScaleY: Double,
    val activeRefreshRateHz: Int?,
    val windowBounds: String,
    val fullscreen: Boolean,
    val renderer: String?,
)

internal object DesktopDisplayDiagnostics {
    private val latestSnapshot = AtomicReference<DesktopDisplaySnapshot?>(null)

    fun snapshot(
        window: Window?,
        fullscreen: Boolean,
        renderer: String? = System.getProperty("skiko.renderApi"),
    ): DesktopDisplaySnapshot {
        val configurations = screenConfigurations()
        val windowBounds = window?.bounds ?: Rectangle(0, 0, 0, 0)
        val active = activeConfigurationForWindow(windowBounds, configurations)
            ?: window?.graphicsConfiguration
            ?: configurations.firstOrNull()
        val transform = active?.defaultTransform
        val displayMode = active?.device?.displayMode
        return DesktopDisplaySnapshot(
            screenCount = configurations.size,
            activeBounds = active?.bounds?.toDisplayLogString() ?: "unknown",
            activeScaleX = transform?.scaleX ?: 1.0,
            activeScaleY = transform?.scaleY ?: 1.0,
            activeRefreshRateHz = displayMode?.refreshRateOrNull(),
            windowBounds = window?.bounds?.toDisplayLogString() ?: "unknown",
            fullscreen = fullscreen,
            renderer = renderer,
        ).also(latestSnapshot::set)
    }

    fun latest(): DesktopDisplaySnapshot? = latestSnapshot.get()

    fun log(
        event: String,
        window: Window?,
        fullscreen: Boolean,
        renderer: String? = System.getProperty("skiko.renderApi"),
    ) {
        val snapshot = snapshot(window, fullscreen, renderer)
        DesktopRuntimeLog.info(
            "desktop.display event=$event " +
                "screenCount=${snapshot.screenCount} " +
                "activeBounds=${snapshot.activeBounds} " +
                "scale=${snapshot.activeScaleX.formatScale()}x${snapshot.activeScaleY.formatScale()} " +
                "refresh=${snapshot.activeRefreshRateHz ?: "unknown"} " +
                "windowBounds=${snapshot.windowBounds} " +
                "fullscreen=${snapshot.fullscreen} " +
                "renderer=${snapshot.renderer ?: "unknown"}",
        )
    }

    internal fun activeScreenBoundsForWindow(
        windowBounds: Rectangle,
        screenBounds: List<Rectangle>,
    ): Rectangle? =
        DesktopWindowChrome.targetScreenBoundsForWindow(windowBounds, screenBounds)

    private fun activeConfigurationForWindow(
        windowBounds: Rectangle,
        configurations: List<GraphicsConfiguration>,
    ): GraphicsConfiguration? {
        val targetBounds = activeScreenBoundsForWindow(
            windowBounds = windowBounds,
            screenBounds = configurations.map { it.bounds },
        ) ?: return null
        return configurations.firstOrNull { it.bounds == targetBounds }
    }

    private fun screenConfigurations(): List<GraphicsConfiguration> =
        runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .screenDevices
                .map { it.defaultConfiguration }
        }.getOrDefault(emptyList())

    internal fun Rectangle.toDisplayLogString(): String =
        "${x}x${y}+${width}x$height"

    private fun DisplayMode.refreshRateOrNull(): Int? =
        refreshRate.takeIf { it != DisplayMode.REFRESH_RATE_UNKNOWN && it > 0 }

    private fun Double.formatScale(): String =
        String.format(Locale.US, "%.2f", this)
}
