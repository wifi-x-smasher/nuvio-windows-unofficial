package com.nuvio.app.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified

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
    val uiScale: Float,
)

internal val NuvioDesktopSidebarCollapsedWidth = 96.dp
internal val NuvioDesktopSidebarExpandedWidth = 260.dp
internal val NuvioDesktopSettingsRailWidth = 260.dp
internal val NuvioDesktopContentMaxWidth = 1040.dp
internal val NuvioDesktopDialogMaxWidth = 560.dp
internal val NuvioCompactDialogMaxWidth = 440.dp
internal val NuvioDesktopStreamPanelMinWidth = 520.dp
internal val NuvioDesktopStreamPanelPreferredWidth = 600.dp
internal val NuvioDesktopStreamPanelMaxWidth = 1120.dp

internal val LocalNuvioDesktopUiScale = staticCompositionLocalOf { 1f }

internal val nuvioDesktopUiScale: Float
    @Composable
    @ReadOnlyComposable
    get() = LocalNuvioDesktopUiScale.current

internal fun Dp.scaledByDesktop(scale: Float): Dp = this * scale

internal fun Int.scaledDpByDesktop(scale: Float): Dp = (this * scale).dp

private fun TextUnit.scaleIfSpecified(scale: Float): TextUnit =
    if (isUnspecified) this else this * scale

internal fun TextStyle.scaledByDesktop(scale: Float): TextStyle =
    if (scale == 1f) {
        this
    } else {
        copy(
            fontSize = fontSize.scaleIfSpecified(scale),
            lineHeight = lineHeight.scaleIfSpecified(scale),
        )
    }

internal fun nuvioDesktopTvMetrics(availableWidth: Dp): NuvioDesktopTvMetrics {
    val useDesktopSidebar = availableWidth >= 900.dp
    val useExpandedSidebar = useDesktopSidebar && availableWidth >= 1200.dp
    val uiScale = when {
        availableWidth >= 3200.dp -> 1.42f
        availableWidth >= 2600.dp -> 1.28f
        availableWidth >= 2100.dp -> 1.14f
        else -> 1f
    }
    val sidebarWidth = when {
        !useDesktopSidebar -> 0.dp
        useExpandedSidebar -> NuvioDesktopSidebarExpandedWidth * uiScale.coerceAtMost(1.18f)
        else -> NuvioDesktopSidebarCollapsedWidth * uiScale.coerceAtMost(1.12f)
    }
    val horizontalGutter = when {
        availableWidth >= 3200.dp -> 88.dp
        availableWidth >= 2600.dp -> 72.dp
        availableWidth >= 2100.dp -> 56.dp
        availableWidth >= 1600.dp -> 48.dp
        availableWidth >= 1200.dp -> 40.dp
        useDesktopSidebar -> 32.dp
        else -> 24.dp
    }
    val availableContentWidth = (availableWidth - sidebarWidth - horizontalGutter * 2f).coerceAtLeast(0.dp)
    val scaledContentMaxWidth = NuvioDesktopContentMaxWidth * uiScale.coerceAtMost(1.24f)
    val contentMaxWidth = when {
        !useDesktopSidebar -> minOf(availableWidth, 460.dp)
        availableWidth < 1200.dp -> minOf(availableContentWidth, 920.dp)
        else -> minOf(availableContentWidth, scaledContentMaxWidth)
    }
    val streamPanelWidth = when {
        !useDesktopSidebar -> minOf(availableWidth, NuvioCompactDialogMaxWidth)
        availableWidth >= 3200.dp -> minOf(availableWidth * 0.36f, NuvioDesktopStreamPanelMaxWidth)
        availableWidth >= 2600.dp -> minOf(availableWidth * 0.35f, 980.dp)
        availableWidth >= 2100.dp -> minOf(availableWidth * 0.34f, 820.dp)
        availableWidth >= 1600.dp -> 680.dp
        else -> minOf(NuvioDesktopStreamPanelMinWidth, availableWidth * 0.62f)
    }

    return NuvioDesktopTvMetrics(
        useDesktopSidebar = useDesktopSidebar,
        useExpandedSidebar = useExpandedSidebar,
        sidebarWidth = sidebarWidth,
        collapsedSidebarWidth = NuvioDesktopSidebarCollapsedWidth,
        expandedSidebarWidth = NuvioDesktopSidebarExpandedWidth,
        settingsRailWidth = NuvioDesktopSettingsRailWidth * uiScale.coerceAtMost(1.16f),
        contentMaxWidth = contentMaxWidth,
        dialogMaxWidth = if (useDesktopSidebar) NuvioDesktopDialogMaxWidth else NuvioCompactDialogMaxWidth,
        streamPanelWidth = streamPanelWidth,
        horizontalGutter = horizontalGutter,
        uiScale = uiScale,
    )
}
