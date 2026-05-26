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
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

fun main() = application {
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
    ) {
        App()
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
