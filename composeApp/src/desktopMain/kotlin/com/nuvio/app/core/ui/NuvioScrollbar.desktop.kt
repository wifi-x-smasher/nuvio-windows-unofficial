package com.nuvio.app.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun nuvioScrollbarStyle(): ScrollbarStyle = ScrollbarStyle(
    minimalHeight = 28.dp,
    thickness = 8.dp,
    shape = RoundedCornerShape(4.dp),
    hoverDurationMillis = 300,
    unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
    hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
)

@Composable
actual fun NuvioVerticalScrollbar(listState: LazyListState, modifier: Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        modifier = modifier.fillMaxHeight(),
        style = nuvioScrollbarStyle(),
    )
}

@Composable
actual fun NuvioVerticalScrollbar(gridState: LazyGridState, modifier: Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(gridState),
        modifier = modifier.fillMaxHeight(),
        style = nuvioScrollbarStyle(),
    )
}

@Composable
actual fun NuvioVerticalScrollbar(scrollState: ScrollState, modifier: Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier.fillMaxHeight(),
        style = nuvioScrollbarStyle(),
    )
}
