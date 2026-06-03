package com.nuvio.app.core.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NuvioDesktopTvLayoutTest {
    @Test
    fun desktopMetricsUseTvRailAndBoundedContent() {
        val metrics = nuvioDesktopTvMetrics(1920.dp)

        assertTrue(metrics.useDesktopSidebar)
        assertTrue(metrics.useExpandedSidebar)
        assertEquals(260.dp, metrics.sidebarWidth)
        assertEquals(1040.dp, metrics.contentMaxWidth)
        assertEquals(680.dp, metrics.streamPanelWidth)
        assertEquals(560.dp, metrics.dialogMaxWidth)
        assertEquals(1f, metrics.uiScale)
    }

    @Test
    fun mediumDesktopKeepsCollapsedRailAndReadablePanels() {
        val metrics = nuvioDesktopTvMetrics(1000.dp)

        assertTrue(metrics.useDesktopSidebar)
        assertFalse(metrics.useExpandedSidebar)
        assertEquals(96.dp, metrics.sidebarWidth)
        assertEquals(520.dp, metrics.streamPanelWidth)
        assertTrue(metrics.contentMaxWidth <= 920.dp)
    }

    @Test
    fun compactWidthDoesNotEnableDesktopSidebar() {
        val metrics = nuvioDesktopTvMetrics(760.dp)

        assertFalse(metrics.useDesktopSidebar)
        assertFalse(metrics.useExpandedSidebar)
        assertEquals(0.dp, metrics.sidebarWidth)
        assertEquals(440.dp, metrics.dialogMaxWidth)
    }

    @Test
    fun streamPanelNeverConsumesTooMuchHorizontalSpace() {
        val medium = nuvioDesktopTvMetrics(900.dp)
        val wide = nuvioDesktopTvMetrics(2560.dp)
        val ultraWide = nuvioDesktopTvMetrics(3840.dp)

        assertTrue(medium.streamPanelWidth <= 520.dp)
        assertTrue(medium.streamPanelWidth <= 900.dp * 0.62f)
        assertEquals(820.dp, wide.streamPanelWidth)
        assertEquals(1120.dp, ultraWide.streamPanelWidth)
        assertEquals(1.42f, ultraWide.uiScale)
    }
}
