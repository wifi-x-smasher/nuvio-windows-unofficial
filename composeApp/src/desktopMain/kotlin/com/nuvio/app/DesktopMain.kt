package com.nuvio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.zIndex
import com.nuvio.app.desktop.DesktopPlayerRegistry
import com.nuvio.app.desktop.DesktopRuntimeLog
import com.nuvio.app.desktop.DesktopWindowChrome
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
    System.setProperty("skiko.renderApi", "OPENGL")
    AppDiagnostics.install()
    DesktopRuntimeLog.initialize()
    DesktopRuntimeLog.installGlobalExceptionHandlers()
    if (DesktopDeepLinkBridge.forwardToPrimaryInstanceIfNeeded(args)) {
        return
    }
    DesktopDeepLinkBridge.install(args)

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 720.dp)
        var previousNonFullscreenPlacement by remember { mutableStateOf(WindowPlacement.Floating) }
        var desktopFullscreen by remember { mutableStateOf(false) }

        val toggleFullscreen = {
            if (!desktopFullscreen) {
                previousNonFullscreenPlacement = windowState.placement
            }
            desktopFullscreen = !desktopFullscreen
            windowState.placement = nextDesktopWindowPlacement(
                isFullscreen = desktopFullscreen,
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
            onCloseRequest = {
                DesktopPlayerRegistry.closeAll("windowClose")
                DesktopPlayerRegistry.awaitAllCloses(timeoutMs = 1500)
                exitApplication()
            },
            title = "Nuvio",
            state = windowState,
            icon = painterResource(Res.drawable.app_logo_mark),
        ) {
            val awtWindow = window
            DisposableEffect(awtWindow) {
                DesktopWindowChrome.applyNuvioChrome(awtWindow)

                val recoveryListener = object : WindowAdapter() {
                    private fun recover(event: WindowEvent) {
                        if (isDesktopWindowRecoveryEvent(event.id)) {
                            DesktopWindowChrome.applyNuvioChrome(awtWindow)
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

            Box(modifier = Modifier.fillMaxSize()) {
                App()

                if (!desktopFullscreen) {
                    DesktopFullscreenButton(
                        onClick = currentToggleFullscreen,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 14.dp, end = 18.dp)
                            .zIndex(20f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopFullscreenButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Fullscreen,
            contentDescription = "Enter fullscreen (F11)",
            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(24.dp),
        )
    }
}

internal fun nextDesktopWindowPlacement(
    isFullscreen: Boolean,
    previousNonFullscreen: WindowPlacement,
): WindowPlacement =
    if (isFullscreen) {
        WindowPlacement.Fullscreen
    } else {
        previousNonFullscreen.takeUnless { it == WindowPlacement.Fullscreen } ?: WindowPlacement.Floating
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
