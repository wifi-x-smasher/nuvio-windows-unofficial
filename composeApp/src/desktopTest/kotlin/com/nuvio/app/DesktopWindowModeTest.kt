package com.nuvio.app

import androidx.compose.ui.window.WindowPlacement
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowModeTest {
    @Test
    fun togglesIntoDesktopFullscreenFromNormalPlacements() {
        assertEquals(
            WindowPlacement.Fullscreen,
            nextDesktopWindowPlacement(
                isFullscreen = true,
                previousNonFullscreen = WindowPlacement.Floating,
            ),
        )
        assertEquals(
            WindowPlacement.Fullscreen,
            nextDesktopWindowPlacement(
                isFullscreen = true,
                previousNonFullscreen = WindowPlacement.Floating,
            ),
        )
    }

    @Test
    fun restoresThePreviousNonFullscreenPlacement() {
        assertEquals(
            WindowPlacement.Maximized,
            nextDesktopWindowPlacement(
                isFullscreen = false,
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
