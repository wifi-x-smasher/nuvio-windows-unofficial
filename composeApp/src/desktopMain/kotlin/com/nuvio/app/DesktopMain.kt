package com.nuvio.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nuvio.app.core.diagnostics.AppDiagnostics
import com.nuvio.app.core.deeplink.DesktopDeepLinkBridge
import java.awt.Color
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.Window as AwtWindow
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_mark
import org.jetbrains.compose.resources.painterResource

fun main(args: Array<String>) {
    System.setProperty("compose.interop.blending", "true")
    System.setProperty("compose.swing.render.on.graphics", "true")
    System.setProperty("compose.layers.type", "COMPONENT")
    AppDiagnostics.install()
    if (DesktopDeepLinkBridge.forwardToPrimaryInstanceIfNeeded(args)) {
        return
    }
    DesktopDeepLinkBridge.install(args)

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 720.dp)
        var previousNonFullscreenPlacement by remember { mutableStateOf(WindowPlacement.Floating) }

        val toggleFullscreen = {
            if (windowState.placement != WindowPlacement.Fullscreen) {
                previousNonFullscreenPlacement = windowState.placement
            }
            windowState.placement = nextDesktopWindowPlacement(
                current = windowState.placement,
                previousNonFullscreen = previousNonFullscreenPlacement,
            )
        }
        val currentToggleFullscreen by rememberUpdatedState(toggleFullscreen)

        DisposableEffect(Unit) {
            val dispatcher = java.awt.KeyEventDispatcher { event ->
                if (
                    event.id == KeyEvent.KEY_PRESSED &&
                    isDesktopFullscreenShortcut(event.keyCode, event.isAltDown)
                ) {
                    currentToggleFullscreen()
                    true
                } else {
                    false
                }
            }
            val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            keyboardFocusManager.addKeyEventDispatcher(dispatcher)
            onDispose {
                keyboardFocusManager.removeKeyEventDispatcher(dispatcher)
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Nuvio",
            state = windowState,
            icon = painterResource(Res.drawable.app_logo_mark),
        ) {
            val awtWindow = window
            DisposableEffect(awtWindow) {
                awtWindow.background = Color.BLACK
                (awtWindow as? JFrame)?.contentPane?.background = Color.BLACK

                val recoveryListener = object : WindowAdapter() {
                    private fun recover(event: WindowEvent) {
                        if (isDesktopWindowRecoveryEvent(event.id)) {
                            repaintDesktopWindow(awtWindow)
                        }
                    }

                    override fun windowActivated(event: WindowEvent) = recover(event)
                    override fun windowDeiconified(event: WindowEvent) = recover(event)
                    override fun windowGainedFocus(event: WindowEvent) = recover(event)
                }

                awtWindow.addWindowListener(recoveryListener)
                awtWindow.addWindowFocusListener(recoveryListener)
                onDispose {
                    awtWindow.removeWindowListener(recoveryListener)
                    awtWindow.removeWindowFocusListener(recoveryListener)
                }
            }

            App()
        }
    }
}

internal fun nextDesktopWindowPlacement(
    current: WindowPlacement,
    previousNonFullscreen: WindowPlacement,
): WindowPlacement =
    if (current == WindowPlacement.Fullscreen) {
        previousNonFullscreen.takeUnless { it == WindowPlacement.Fullscreen } ?: WindowPlacement.Floating
    } else {
        WindowPlacement.Fullscreen
    }

internal fun isDesktopFullscreenShortcut(keyCode: Int, isAltDown: Boolean): Boolean =
    keyCode == KeyEvent.VK_F11 || (isAltDown && keyCode == KeyEvent.VK_ENTER)

internal fun isDesktopWindowRecoveryEvent(eventId: Int): Boolean =
    eventId == WindowEvent.WINDOW_ACTIVATED ||
        eventId == WindowEvent.WINDOW_DEICONIFIED ||
        eventId == WindowEvent.WINDOW_GAINED_FOCUS

private fun repaintDesktopWindow(window: AwtWindow) {
    EventQueue.invokeLater {
        window.background = Color.BLACK
        (window as? JFrame)?.contentPane?.let { pane ->
            pane.background = Color.BLACK
            pane.invalidate()
            pane.validate()
            pane.repaint()
        }
        window.invalidate()
        window.validate()
        window.repaint()
    }
}
