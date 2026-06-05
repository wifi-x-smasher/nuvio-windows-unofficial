package com.nuvio.app

import com.nuvio.app.desktop.DesktopWindowChrome
import java.awt.event.KeyEvent
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowModeTest {
    @Test
    fun togglesBetweenNormalAndFullscreenModes() {
        assertEquals(DesktopWindowMode.Fullscreen, nextWindowMode(DesktopWindowMode.Normal))
        assertEquals(DesktopWindowMode.Normal, nextWindowMode(DesktopWindowMode.Fullscreen))
    }

    @Test
    fun escapeExitsFullscreenOnlyWhileBrowsing() {
        assertTrue(
            shouldExitFullscreenOnEscape(DesktopWindowMode.Fullscreen, handledByPlayer = false),
        )
        // The player owns Esc during playback (close/back), so fullscreen must not steal it.
        assertFalse(
            shouldExitFullscreenOnEscape(DesktopWindowMode.Fullscreen, handledByPlayer = true),
        )
        // Esc is a no-op when we are not in fullscreen.
        assertFalse(
            shouldExitFullscreenOnEscape(DesktopWindowMode.Normal, handledByPlayer = false),
        )
    }

    @Test
    fun debouncesRapidToggleEvents() {
        // First toggle (no prior timestamp) is always allowed.
        assertFalse(shouldDebounceToggle(nowNanos = 1_000_000_000L, lastToggleNanos = 0L))
        // A second toggle 50ms later (key auto-repeat) is swallowed.
        assertTrue(
            shouldDebounceToggle(nowNanos = 1_050_000_000L, lastToggleNanos = 1_000_000_000L),
        )
        // A deliberate toggle 400ms later is allowed through.
        assertFalse(
            shouldDebounceToggle(nowNanos = 1_400_000_000L, lastToggleNanos = 1_000_000_000L),
        )
    }

    @Test
    fun clampsDraggedWindowSoAGripRemainsVisible() {
        val virtualDesktop = Rectangle(0, 0, 1920, 1080)
        val window = Rectangle(-1500, -900, 1280, 720)

        val clamped = clampedDesktopWindowBounds(window, virtualDesktop, minVisiblePx = 120)

        assertEquals(Rectangle(-1160, -600, 1280, 720), clamped)
    }

    @Test
    fun clampsDraggedWindowInsideNegativeCoordinateDesktop() {
        val virtualDesktop = Rectangle(-1920, 0, 3840, 1080)
        val window = Rectangle(2400, 990, 1280, 720)

        val clamped = clampedDesktopWindowBounds(window, virtualDesktop, minVisiblePx = 120)

        assertEquals(Rectangle(1800, 960, 1280, 720), clamped)
    }

    @Test
    fun leavesVisibleDraggedWindowUnchanged() {
        val virtualDesktop = Rectangle(-1920, 0, 3840, 1080)
        val window = Rectangle(100, 80, 1280, 720)

        val clamped = clampedDesktopWindowBounds(window, virtualDesktop, minVisiblePx = 120)

        assertEquals(window, clamped)
    }

    @Test
    fun selectsSecondMonitorWhenDraggedWindowCenterIsOnSecondMonitor() {
        val primary = Rectangle(0, 0, 1920, 1080)
        val secondary = Rectangle(1920, 0, 1920, 1080)
        val draggedWindow = Rectangle(2100, 80, 1280, 720)

        val target = DesktopWindowChrome.targetScreenBoundsForWindow(
            windowBounds = draggedWindow,
            screenBounds = listOf(primary, secondary),
        )

        assertEquals(secondary, target)
    }

    @Test
    fun selectsNegativeCoordinateMonitorWhenWindowCenterIsLeftOfPrimary() {
        val secondaryLeft = Rectangle(-1920, 0, 1920, 1080)
        val primary = Rectangle(0, 0, 1920, 1080)
        val draggedWindow = Rectangle(-1500, 80, 1280, 720)

        val target = DesktopWindowChrome.targetScreenBoundsForWindow(
            windowBounds = draggedWindow,
            screenBounds = listOf(secondaryLeft, primary),
        )

        assertEquals(secondaryLeft, target)
    }

    @Test
    fun fallsBackToLargestIntersectionWhenWindowCenterIsBetweenScreens() {
        val left = Rectangle(0, 0, 800, 600)
        val right = Rectangle(1000, 0, 800, 600)
        val draggedWindow = Rectangle(750, 100, 350, 300)

        val target = DesktopWindowChrome.targetScreenBoundsForWindow(
            windowBounds = draggedWindow,
            screenBounds = listOf(left, right),
        )

        assertEquals(right, target)
    }

    @Test
    fun recognizesFullscreenShortcuts() {
        assertTrue(isDesktopFullscreenShortcut(KeyEvent.VK_F11, isAltDown = false))
        assertTrue(isDesktopFullscreenShortcut(KeyEvent.VK_ENTER, isAltDown = true))
        assertFalse(isDesktopFullscreenShortcut(KeyEvent.VK_ENTER, isAltDown = false))
        assertFalse(isDesktopFullscreenShortcut(KeyEvent.VK_F5, isAltDown = true))
    }

    @Test
    fun escapeAndBackspaceBelongToPlayerShortcuts() {
        assertEquals(
            com.nuvio.app.features.player.PlayerKeyboardShortcut.CloseOrBack,
            desktopPlayerKeyboardShortcutFor(KeyEvent.VK_ESCAPE),
        )
        assertEquals(
            com.nuvio.app.features.player.PlayerKeyboardShortcut.CloseOrBack,
            desktopPlayerKeyboardShortcutFor(KeyEvent.VK_BACK_SPACE),
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
