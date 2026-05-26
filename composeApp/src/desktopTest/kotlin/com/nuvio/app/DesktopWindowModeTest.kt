package com.nuvio.app

import androidx.compose.ui.window.WindowPlacement
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowModeTest {
    @Test
    fun togglesIntoFullscreenFromNormalPlacements() {
        assertEquals(
            WindowPlacement.Fullscreen,
            nextDesktopWindowPlacement(
                current = WindowPlacement.Floating,
                previousNonFullscreen = WindowPlacement.Floating,
            ),
        )
        assertEquals(
            WindowPlacement.Fullscreen,
            nextDesktopWindowPlacement(
                current = WindowPlacement.Maximized,
                previousNonFullscreen = WindowPlacement.Floating,
            ),
        )
    }

    @Test
    fun restoresThePreviousNonFullscreenPlacement() {
        assertEquals(
            WindowPlacement.Maximized,
            nextDesktopWindowPlacement(
                current = WindowPlacement.Fullscreen,
                previousNonFullscreen = WindowPlacement.Maximized,
            ),
        )
    }

    @Test
    fun recognizesFullscreenShortcuts() {
        assertTrue(isDesktopFullscreenShortcut(KeyEvent.VK_F11, isAltDown = false))
        assertTrue(isDesktopFullscreenShortcut(KeyEvent.VK_ENTER, isAltDown = true))
        assertFalse(isDesktopFullscreenShortcut(KeyEvent.VK_ENTER, isAltDown = false))
        assertFalse(isDesktopFullscreenShortcut(KeyEvent.VK_F5, isAltDown = true))
    }
}
