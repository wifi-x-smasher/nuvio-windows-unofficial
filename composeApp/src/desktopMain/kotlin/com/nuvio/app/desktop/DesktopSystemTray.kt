package com.nuvio.app.desktop

import com.nuvio.app.features.player.NowPlayingBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.awt.Frame
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Window as AwtWindow

/**
 * System-tray integration for the desktop build.
 *
 * Adds a tray icon whose tooltip reflects what's currently playing (via [NowPlayingBridge]) and a
 * Show/Quit menu; double-clicking restores the window. Best-effort and self-contained: it silently
 * no-ops if the platform has no system tray, and it reuses the window's own icon image so there's
 * no extra asset to ship. Touches nothing in the MPV/HDR/window-chrome path.
 */
internal object DesktopSystemTray {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var trayIcon: TrayIcon? = null
    private var installed = false

    fun install(window: AwtWindow, onQuit: () -> Unit) {
        if (installed || !SystemTray.isSupported()) return
        val image = (window as? Frame)?.iconImages?.firstOrNull() ?: return
        installed = true

        val popup = PopupMenu().apply {
            add(MenuItem("Show Nuvio").apply { addActionListener { restore(window) } })
            addSeparator()
            add(MenuItem("Quit").apply { addActionListener { onQuit() } })
        }
        val icon = TrayIcon(image, "Nuvio", popup).apply {
            isImageAutoSize = true
            addActionListener { restore(window) } // double-click / activate
        }

        val added = runCatching { SystemTray.getSystemTray().add(icon) }.isSuccess
        if (!added) {
            installed = false
            return
        }
        trayIcon = icon
        DesktopRuntimeLog.info("desktop.tray.installed")

        scope.launch {
            NowPlayingBridge.state.collect { info ->
                trayIcon?.toolTip = info?.title?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { "Nuvio — $it" }
                    ?: "Nuvio"
            }
        }
    }

    private fun restore(window: AwtWindow) {
        EventQueue.invokeLater {
            window.isVisible = true
            (window as? Frame)?.extendedState = Frame.NORMAL
            window.toFront()
            window.requestFocus()
        }
    }
}
