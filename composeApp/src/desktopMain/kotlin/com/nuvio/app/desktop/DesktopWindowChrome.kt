package com.nuvio.app.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.W32APIOptions
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import javax.swing.JFrame
import kotlin.math.roundToInt

internal object DesktopWindowChrome {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private val dwmApi: DwmApi? by lazy {
        runCatching {
            Native.load("dwmapi", DwmApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }.getOrNull()
    }

    private val user32Api: User32Api? by lazy {
        runCatching {
            Native.load("user32", User32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }.getOrNull()
    }

    fun applyNuvioChrome(window: Window) {
        window.background = java.awt.Color.BLACK
        (window as? JFrame)?.contentPane?.background = java.awt.Color.BLACK

        if (!isWindows()) return

        runCatching {
            val hwnd = Native.getWindowPointer(window)
            val api = dwmApi ?: return
            setDwmInt(api, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, 1)
            setDwmInt(api, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, 1)
            setDwmInt(api, hwnd, DWMWA_CAPTION_COLOR, rgbToColorRef(11, 10, 18))
            setDwmInt(api, hwnd, DWMWA_BORDER_COLOR, rgbToColorRef(31, 28, 42))
            setDwmInt(api, hwnd, DWMWA_TEXT_COLOR, rgbToColorRef(245, 245, 248))
        }.onFailure { error ->
            DesktopRuntimeLog.warn(
                "desktop.window.chrome.failure message=${error.message ?: error::class.simpleName.orEmpty()}",
            )
        }
    }

    /**
     * Sizes the (undecorated) window to the current monitor's full bounds, including the area
     * behind the taskbar, for immersive fullscreen. Must be called on the AWT event thread.
     */
    fun applyFullscreenBounds(
        window: Window,
        strategy: DesktopFullscreenStrategy = DesktopFullscreenStrategy.resolve(),
    ) {
        DesktopRuntimeLog.info("desktop.fullscreen.strategy selected=${strategy.configValue}")
        when (strategy) {
            DesktopFullscreenStrategy.ManualBorderlessBounds -> {
                applyBounds(window, fullScreenBounds(window), label = "fullscreen", fullscreen = true)
            }
            DesktopFullscreenStrategy.WorkAreaMaximized -> {
                applyBounds(window, workAreaBounds(window), label = "fullscreen-work-area", fullscreen = true)
            }
            DesktopFullscreenStrategy.Win32BorderlessSetWindowPos -> {
                applyWin32FullscreenBounds(window)
            }
            DesktopFullscreenStrategy.PlayerWindowOnly -> {
                DesktopRuntimeLog.warn(
                    "desktop.fullscreen.strategy player-window-only unavailable fallback=manual-borderless",
                )
                applyBounds(window, fullScreenBounds(window), label = "fullscreen", fullscreen = true)
            }
        }
    }

    /**
     * Sizes the (undecorated) window to the current monitor's work area (excluding the taskbar)
     * so it reads as a maximized browsing window. Must be called on the AWT event thread.
     */
    fun applyNormalBounds(window: Window) {
        applyBounds(window, workAreaBounds(window), label = "normal", fullscreen = false)
    }

    private fun applyBounds(
        window: Window,
        bounds: Rectangle,
        label: String,
        fullscreen: Boolean,
    ) {
        val frame = window as? JFrame
        frame?.extendedState = JFrame.NORMAL
        window.bounds = bounds
        window.background = java.awt.Color.BLACK
        frame?.contentPane?.background = java.awt.Color.BLACK
        window.validate()
        window.repaint()
        DesktopRuntimeLog.info("desktop.window.$label bounds=${bounds.toLogString()}")
        DesktopDisplayDiagnostics.log(
            event = "window-$label-bounds-applied",
            window = window,
            fullscreen = fullscreen,
        )
    }

    private fun applyWin32FullscreenBounds(window: Window) {
        if (!isWindows()) {
            DesktopRuntimeLog.warn(
                "desktop.fullscreen.strategy win32-borderless unavailable reason=non-windows fallback=manual-borderless",
            )
            applyBounds(window, fullScreenBounds(window), label = "fullscreen", fullscreen = true)
            return
        }
        val targetConfiguration = targetConfiguration(window)
        val logicalBounds = targetConfiguration.bounds
        val transform = targetConfiguration.defaultTransform
        val nativeBounds = toNativePixelBoundsForSetWindowPos(
            logicalBounds = logicalBounds,
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
        )
        val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull()
        val user32 = user32Api
        if (hwnd == null || user32 == null) {
            DesktopRuntimeLog.warn(
                "desktop.fullscreen.strategy win32-borderless unavailable reason=user32 fallback=manual-borderless",
            )
            applyBounds(window, logicalBounds, label = "fullscreen", fullscreen = true)
            return
        }

        val frame = window as? JFrame
        frame?.extendedState = JFrame.NORMAL
        window.background = java.awt.Color.BLACK
        frame?.contentPane?.background = java.awt.Color.BLACK
        DesktopRuntimeLog.info(
            "desktop.window.fullscreen-win32.request " +
                "logical=${logicalBounds.toLogString()} " +
                "native=${nativeBounds.toLogString()} " +
                "scale=${transform.scaleX}x${transform.scaleY}",
        )
        val ok = runCatching {
            user32.SetWindowPos(
                hwnd,
                null,
                nativeBounds.x,
                nativeBounds.y,
                nativeBounds.width,
                nativeBounds.height,
                SWP_NOZORDER or SWP_NOOWNERZORDER or SWP_FRAMECHANGED or SWP_SHOWWINDOW,
            )
        }.getOrDefault(false)
        window.validate()
        window.repaint()
        DesktopRuntimeLog.info(
            "desktop.window.fullscreen-win32 " +
                "logical=${logicalBounds.toLogString()} " +
                "native=${nativeBounds.toLogString()} " +
                "result=$ok",
        )
        DesktopDisplayDiagnostics.log(
            event = "window-fullscreen-win32-bounds-applied",
            window = window,
            fullscreen = true,
        )
    }

    internal fun toNativePixelBoundsForSetWindowPos(
        logicalBounds: Rectangle,
        scaleX: Double,
        scaleY: Double,
    ): Rectangle {
        val safeScaleX = scaleX.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val safeScaleY = scaleY.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        return Rectangle(
            (logicalBounds.x * safeScaleX).roundToInt(),
            (logicalBounds.y * safeScaleY).roundToInt(),
            (logicalBounds.width * safeScaleX).roundToInt().coerceAtLeast(1),
            (logicalBounds.height * safeScaleY).roundToInt().coerceAtLeast(1),
        )
    }

    private fun fullScreenBounds(window: Window): Rectangle =
        targetConfiguration(window).bounds

    private fun workAreaBounds(window: Window): Rectangle =
        workAreaBounds(targetConfiguration(window))

    private fun workAreaBounds(gc: GraphicsConfiguration): Rectangle {
        val bounds = gc.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        return Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            (bounds.width - insets.left - insets.right).coerceAtLeast(1),
            (bounds.height - insets.top - insets.bottom).coerceAtLeast(1),
        )
    }

    private fun targetConfiguration(window: Window): GraphicsConfiguration {
        val configurations = screenConfigurations()
        val targetBounds = targetScreenBoundsForWindow(
            windowBounds = window.bounds,
            screenBounds = configurations.map { it.bounds },
        ) ?: return window.graphicsConfiguration ?: defaultConfiguration()
        return configurations.firstOrNull { it.bounds == targetBounds }
            ?: window.graphicsConfiguration
            ?: defaultConfiguration()
    }

    private fun screenConfigurations(): List<GraphicsConfiguration> =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .screenDevices
            .map { it.defaultConfiguration }

    internal fun targetScreenBoundsForWindow(
        windowBounds: Rectangle,
        screenBounds: List<Rectangle>,
    ): Rectangle? {
        if (screenBounds.isEmpty()) return null
        val centerX = windowBounds.x + windowBounds.width / 2
        val centerY = windowBounds.y + windowBounds.height / 2
        screenBounds.firstOrNull { it.contains(centerX, centerY) }?.let { return Rectangle(it) }
        return screenBounds
            .maxByOrNull { intersectionArea(windowBounds, it) }
            ?.let { Rectangle(it) }
    }

    private fun intersectionArea(first: Rectangle, second: Rectangle): Long {
        val left = maxOf(first.x, second.x)
        val top = maxOf(first.y, second.y)
        val right = minOf(first.x + first.width, second.x + second.width)
        val bottom = minOf(first.y + first.height, second.y + second.height)
        val width = (right - left).coerceAtLeast(0)
        val height = (bottom - top).coerceAtLeast(0)
        return width.toLong() * height.toLong()
    }

    private fun defaultConfiguration() =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration

    internal fun rgbToColorRef(red: Int, green: Int, blue: Int): Int =
        (red and 0xff) or ((green and 0xff) shl 8) or ((blue and 0xff) shl 16)

    private fun setDwmInt(api: DwmApi, hwnd: Pointer, attribute: Int, value: Int): Int {
        val memory = Memory(4)
        memory.setInt(0, value)
        return api.DwmSetWindowAttribute(hwnd, attribute, memory, 4)
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

    private fun Rectangle.toLogString(): String =
        "${x}x${y}+${width}x$height"

    private interface DwmApi : Library {
        @Suppress("FunctionName")
        fun DwmSetWindowAttribute(
            hwnd: Pointer,
            dwAttribute: Int,
            pvAttribute: Pointer,
            cbAttribute: Int,
        ): Int
    }

    private interface User32Api : Library {
        @Suppress("FunctionName")
        fun SetWindowPos(
            hWnd: Pointer,
            hWndInsertAfter: Pointer?,
            x: Int,
            y: Int,
            cx: Int,
            cy: Int,
            flags: Int,
        ): Boolean
    }

    private const val SWP_NOZORDER = 0x0004
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_SHOWWINDOW = 0x0040
    private const val SWP_NOOWNERZORDER = 0x0200
}
