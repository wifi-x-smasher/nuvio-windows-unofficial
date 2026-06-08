package com.nuvio.app.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A draggable vertical scrollbar styled to match Nuvio's design.
 *
 * On desktop this renders Compose's native [androidx.compose.foundation.VerticalScrollbar], which is
 * draggable with the mouse (helpful when the scroll wheel is unavailable). On Android/iOS it's a
 * no-op — touch platforms have their own scroll affordances.
 *
 * Usage: place inside the same Box as the scrollable, aligned to the end edge, e.g.
 * `Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp)`.
 */
@Composable
expect fun NuvioVerticalScrollbar(listState: LazyListState, modifier: Modifier = Modifier)

@Composable
expect fun NuvioVerticalScrollbar(gridState: LazyGridState, modifier: Modifier = Modifier)

@Composable
expect fun NuvioVerticalScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier)
