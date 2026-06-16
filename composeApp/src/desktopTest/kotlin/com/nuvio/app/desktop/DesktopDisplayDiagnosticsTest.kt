package com.nuvio.app.desktop

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDisplayDiagnosticsTest {
    @Test
    fun selectsSingleMonitor() {
        val primary = Rectangle(0, 0, 1920, 1080)

        val selected = DesktopDisplayDiagnostics.activeScreenBoundsForWindow(
            windowBounds = Rectangle(100, 100, 1280, 720),
            screenBounds = listOf(primary),
        )

        assertEquals(primary, selected)
    }

    @Test
    fun selectsRightSideMonitorWhenWindowCenterIsOnIt() {
        val primary = Rectangle(0, 0, 1920, 1080)
        val secondary = Rectangle(1920, 0, 3840, 2160)

        val selected = DesktopDisplayDiagnostics.activeScreenBoundsForWindow(
            windowBounds = Rectangle(2600, 200, 1600, 900),
            screenBounds = listOf(primary, secondary),
        )

        assertEquals(secondary, selected)
    }

    @Test
    fun selectsLeftSideNegativeCoordinateMonitor() {
        val secondaryLeft = Rectangle(-1920, 0, 1920, 1080)
        val primary = Rectangle(0, 0, 1920, 1080)

        val selected = DesktopDisplayDiagnostics.activeScreenBoundsForWindow(
            windowBounds = Rectangle(-1700, 120, 1280, 720),
            screenBounds = listOf(secondaryLeft, primary),
        )

        assertEquals(secondaryLeft, selected)
    }

    @Test
    fun selectsAboveNegativeCoordinateMonitor() {
        val secondaryAbove = Rectangle(0, -1440, 2560, 1440)
        val primary = Rectangle(0, 0, 2560, 1440)

        val selected = DesktopDisplayDiagnostics.activeScreenBoundsForWindow(
            windowBounds = Rectangle(400, -1200, 1600, 900),
            screenBounds = listOf(primary, secondaryAbove),
        )

        assertEquals(secondaryAbove, selected)
    }

    @Test
    fun centerPointWinsWhenWindowSpansTwoMonitors() {
        val primary = Rectangle(0, 0, 1920, 1080)
        val secondary = Rectangle(1920, 0, 1920, 1080)

        val selected = DesktopDisplayDiagnostics.activeScreenBoundsForWindow(
            windowBounds = Rectangle(1600, 100, 900, 700),
            screenBounds = listOf(primary, secondary),
        )

        assertEquals(secondary, selected)
    }

    @Test
    fun largestIntersectionWinsWhenCenterIsInGap() {
        val left = Rectangle(0, 0, 800, 600)
        val right = Rectangle(1000, 0, 800, 600)

        val selected = DesktopDisplayDiagnostics.activeScreenBoundsForWindow(
            windowBounds = Rectangle(750, 100, 350, 300),
            screenBounds = listOf(left, right),
        )

        assertEquals(right, selected)
    }

    @Test
    fun formatsBoundsWithoutAssumingPositiveCoordinates() {
        assertEquals(
            "-1920x-1080+1920x1080",
            DesktopDisplayDiagnostics.run {
                Rectangle(-1920, -1080, 1920, 1080).toDisplayLogString()
            },
        )
    }
}
