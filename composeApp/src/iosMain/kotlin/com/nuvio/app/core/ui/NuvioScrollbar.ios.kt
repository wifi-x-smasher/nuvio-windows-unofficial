package com.nuvio.app.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Touch platforms have their own scroll affordances — the draggable scrollbar is desktop-only.
@Composable
actual fun NuvioVerticalScrollbar(listState: LazyListState, modifier: Modifier) {}

@Composable
actual fun NuvioVerticalScrollbar(gridState: LazyGridState, modifier: Modifier) {}

@Composable
actual fun NuvioVerticalScrollbar(scrollState: ScrollState, modifier: Modifier) {}
