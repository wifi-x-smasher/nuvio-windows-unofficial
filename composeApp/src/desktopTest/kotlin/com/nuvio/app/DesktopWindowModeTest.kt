package com.nuvio.app

import androidx.compose.ui.window.WindowPlacement
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowModeTest {
    @Test
    fun togglesIntoBorderlessFullscreenFromNormalPlacements() {
        assertEquals(
            WindowPlacement.Floating,
            nextDesktopWindowPlacement(
                isFullscreen = true,
                previousNonFullscreen = WindowPlacement.Floating,
            ),
        )
        assertEquals(
            WindowPlacement.Floating,
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

    @Test
    fun recognizesEscapeAsFullscreenExitOnlyWithoutModifiers() {
        assertTrue(
            isDesktopFullscreenExitShortcut(
                keyCode = KeyEvent.VK_ESCAPE,
                isAltDown = false,
                isControlDown = false,
                isMetaDown = false,
            ),
        )
        assertFalse(
            isDesktopFullscreenExitShortcut(
                keyCode = KeyEvent.VK_ESCAPE,
                isAltDown = true,
                isControlDown = false,
                isMetaDown = false,
            ),
        )
        assertFalse(
            isDesktopFullscreenExitShortcut(
                keyCode = KeyEvent.VK_BACK_SPACE,
                isAltDown = false,
                isControlDown = false,
                isMetaDown = false,
            ),
        )
    }

    @Test
    fun rendererConfigurationDefaultsToOpenGlAndAllowsSoftwareOverride() {
        val previousNuvioRenderApi = System.getProperty("nuvio.renderApi")
        val previousSkikoRenderApi = System.getProperty("skiko.renderApi")
        try {
            System.clearProperty("nuvio.renderApi")
            System.clearProperty("skiko.renderApi")
            assertEquals("OPENGL", configureDesktopRenderer())
            assertEquals("OPENGL", System.getProperty("skiko.renderApi"))

            System.setProperty("nuvio.renderApi", "SOFTWARE")
            assertEquals("SOFTWARE", configureDesktopRenderer())
            assertEquals("SOFTWARE", System.getProperty("skiko.renderApi"))

            System.setProperty("nuvio.renderApi", "DIRECT3D")
            assertEquals("OPENGL", configureDesktopRenderer())
            assertEquals("OPENGL", System.getProperty("skiko.renderApi"))
        } finally {
            restoreProperty("nuvio.renderApi", previousNuvioRenderApi)
            restoreProperty("skiko.renderApi", previousSkikoRenderApi)
        }
    }
}

private fun restoreProperty(name: String, value: String?) {
    if (value == null) {
        System.clearProperty(name)
    } else {
        System.setProperty(name, value)
    }
}
