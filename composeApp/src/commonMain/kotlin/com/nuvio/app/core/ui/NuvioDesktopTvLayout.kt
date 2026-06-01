package com.nuvio.app.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class NuvioDesktopTvMetrics(
    val useDesktopSidebar: Boolean,
    val useExpandedSidebar: Boolean,
    val sidebarWidth: Dp,
    val collapsedSidebarWidth: Dp,
    val expandedSidebarWidth: Dp,
    val settingsRailWidth: Dp,
    val contentMaxWidth: Dp,
    val dialogMaxWidth: Dp,
    val streamPanelWidth: Dp,
    val horizontalGutter: Dp,
)

internal val NuvioDesktopSidebarCollapsedWidth = 96.dp
internal val NuvioDesktopSidebarExpandedWidth = 260.dp
internal val NuvioDesktopSettingsRailWidth = 260.dp
internal val NuvioDesktopContentMaxWidth = 1040.dp
internal val NuvioDesktopDialogMaxWidth = 560.dp
internal val NuvioCompactDialogMaxWidth = 440.dp
internal val NuvioDesktopStreamPanelMinWidth = 520.dp
internal val NuvioDesktopStreamPanelPreferredWidth = 600.dp
internal val NuvioDesktopStreamPanelMaxWidth = 640.dp

internal fun nuvioDesktopTvMetrics(availableWidth: Dp): NuvioDesktopTvMetrics {
    val useDesktopSidebar = availableWidth >= 900.dp
    val useExpandedSidebar = useDesktopSidebar && availableWidth >= 1200.dp
    val sidebarWidth = when {
        !useDesktopSidebar -> 0.dp
        useExpandedSidebar -> NuvioDesktopSidebarExpandedWidth
        else -> NuvioDesktopSidebarCollapsedWidth
    }
    val horizontalGutter = when {
        availableWidth >= 1600.dp -> 48.dp
        availableWidth >= 1200.dp -> 40.dp
        useDesktopSidebar -> 32.dp
        else -> 24.dp
    }
    val availableContentWidth = (availableWidth - sidebarWidth - horizontalGutter * 2f).coerceAtLeast(0.dp)
    val contentMaxWidth = when {
        !useDesktopSidebar -> minOf(availableWidth, 460.dp)
        availableWidth < 1200.dp -> minOf(availableContentWidth, 920.dp)
        else -> minOf(availableContentWidth, NuvioDesktopContentMaxWidth)
    }
    val streamPanelWidth = when {
        !useDesktopSidebar -> minOf(availableWidth, NuvioCompactDialogMaxWidth)
        availableWidth >= 2200.dp -> NuvioDesktopStreamPanelMaxWidth
        availableWidth >= 1600.dp -> NuvioDesktopStreamPanelPreferredWidth
        else -> minOf(NuvioDesktopStreamPanelMinWidth, availableWidth * 0.62f)
    }

    return NuvioDesktopTvMetrics(
        useDesktopSidebar = useDesktopSidebar,
        useExpandedSidebar = useExpandedSidebar,
        sidebarWidth = sidebarWidth,
        collapsedSidebarWidth = NuvioDesktopSidebarCollapsedWidth,
        expandedSidebarWidth = NuvioDesktopSidebarExpandedWidth,
        settingsRailWidth = NuvioDesktopSettingsRailWidth,
        contentMaxWidth = contentMaxWidth,
        dialogMaxWidth = if (useDesktopSidebar) NuvioDesktopDialogMaxWidth else NuvioCompactDialogMaxWidth,
        streamPanelWidth = streamPanelWidth,
        horizontalGutter = horizontalGutter,
    )
}
