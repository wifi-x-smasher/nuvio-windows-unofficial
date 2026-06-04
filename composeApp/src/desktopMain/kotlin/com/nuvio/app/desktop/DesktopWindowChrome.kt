package com.nuvio.app.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.W32APIOptions
import java.awt.Window
import javax.swing.JFrame

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

    internal fun rgbToColorRef(red: Int, green: Int, blue: Int): Int =
        (red and 0xff) or ((green and 0xff) shl 8) or ((blue and 0xff) shl 16)

    private fun setDwmInt(api: DwmApi, hwnd: Pointer, attribute: Int, value: Int): Int {
        val memory = Memory(4)
        memory.setInt(0, value)
        return api.DwmSetWindowAttribute(hwnd, attribute, memory, 4)
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

    private interface DwmApi : Library {
        @Suppress("FunctionName")
        fun DwmSetWindowAttribute(
            hwnd: Pointer,
            dwAttribute: Int,
            pvAttribute: Pointer,
            cbAttribute: Int,
        ): Int
    }
}
