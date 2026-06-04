package com.nuvio.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BoxScope.NuvioHorizontalScrollControls(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    scrollFraction: Float = 0.72f,
) {
    val scope = rememberCoroutineScope()
    val canScrollLeft by remember { derivedStateOf { scrollState.value > 0 } }
    val canScrollRight by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }
    val step by remember { derivedStateOf { (scrollState.viewportSize * scrollFraction).toInt().coerceAtLeast(160) } }

    NuvioHorizontalArrowRail(
        modifier = modifier,
        canScrollLeft = canScrollLeft,
        canScrollRight = canScrollRight,
        onScrollLeft = {
            scope.launch {
                scrollState.animateScrollTo((scrollState.value - step).coerceAtLeast(0))
            }
        },
        onScrollRight = {
            scope.launch {
                scrollState.animateScrollTo((scrollState.value + step).coerceAtMost(scrollState.maxValue))
            }
        },
    )
}

@Composable
fun BoxScope.NuvioLazyRowScrollControls(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val canScrollLeft by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val canScrollRight by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalItems > 0 && lastVisible < totalItems - 1
        }
    }

    NuvioHorizontalArrowRail(
        modifier = modifier,
        canScrollLeft = canScrollLeft,
        canScrollRight = canScrollRight,
        onScrollLeft = {
            scope.launch {
                listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
            }
        },
        onScrollRight = {
            scope.launch {
                val nextIndex = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: listState.firstVisibleItemIndex) + 1
                listState.animateScrollToItem(nextIndex.coerceAtMost((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
            }
        },
    )
}

@Composable
private fun BoxScope.NuvioHorizontalArrowRail(
    canScrollLeft: Boolean,
    canScrollRight: Boolean,
    onScrollLeft: () -> Unit,
    onScrollRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .align(Alignment.CenterEnd)
            .padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(visible = canScrollLeft, enter = fadeIn(), exit = fadeOut()) {
            NuvioHorizontalArrowButton(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                onClick = onScrollLeft,
            )
        }
        AnimatedVisibility(visible = canScrollRight, enter = fadeIn(), exit = fadeOut()) {
            NuvioHorizontalArrowButton(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                onClick = onScrollRight,
            )
        }
    }
}

@Composable
private fun NuvioHorizontalArrowButton(
    imageVector: ImageVector,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(34.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), shape)
            .clickable(onClick = onClick)
            .nuvioDesktopFocusEffect(
                enabled = true,
                shape = shape,
                focusedScale = 1.04f,
                focusedShadowElevation = 10.dp,
            )
            .padding(7.dp),
    )
}
