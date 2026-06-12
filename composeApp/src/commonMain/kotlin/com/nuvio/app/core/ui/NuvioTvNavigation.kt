package com.nuvio.app.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import com.nuvio.app.isDesktop
import kotlin.math.abs

private const val HorizontalWheelStepPixels = 72f

internal fun Key.isNuvioTvSelectKey(): Boolean =
    this == Key.Enter || this == Key.NumPadEnter || this == Key.Spacebar

internal fun Key.isNuvioTvBackKey(): Boolean =
    this == Key.Backspace || this == Key.Escape

internal fun Key.nuvioTvFocusDirection(): FocusDirection? =
    when (this) {
        Key.DirectionLeft -> FocusDirection.Left
        Key.DirectionRight -> FocusDirection.Right
        Key.DirectionUp -> FocusDirection.Up
        Key.DirectionDown -> FocusDirection.Down
        else -> null
    }

internal fun nuvioHorizontalWheelPixels(
    deltaX: Float,
    deltaY: Float,
    multiplier: Float = HorizontalWheelStepPixels,
    allowVerticalWheel: Boolean = true,
): Float {
    val dominantDelta = when {
        allowVerticalWheel && abs(deltaY) >= abs(deltaX) -> deltaY
        abs(deltaX) > 0f -> deltaX
        else -> 0f
    }
    return dominantDelta * multiplier
}

internal fun nuvioShouldConsumeHorizontalWheel(
    deltaPixels: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): Boolean =
    when {
        deltaPixels > 0f -> canScrollForward
        deltaPixels < 0f -> canScrollBackward
        else -> false
    }

internal fun Modifier.nuvioTvSelectKeys(
    enabled: Boolean = true,
    onSelect: (() -> Unit)?,
): Modifier {
    if (!isDesktop || !enabled || onSelect == null) return this
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key.isNuvioTvSelectKey()) {
            onSelect()
            true
        } else {
            false
        }
    }
}

@Composable
internal fun Modifier.nuvioTvDirectionalFocusTraversal(
    enabled: Boolean = true,
): Modifier {
    if (!isDesktop || !enabled) return this
    val focusManager = LocalFocusManager.current
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            event.key.nuvioTvFocusDirection()?.let { direction ->
                focusManager.moveFocus(direction)
            } ?: false
        }
    }
}

internal fun Modifier.nuvioTvBackKeys(
    enabled: Boolean = true,
    onBack: () -> Unit,
): Modifier {
    if (!isDesktop || !enabled) return this
    return onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key.isNuvioTvBackKey()) {
            onBack()
            true
        } else {
            false
        }
    }
}

internal fun Modifier.nuvioSecondaryClickAsLongPress(
    enabled: Boolean = true,
    onSecondaryClick: (() -> Unit)?,
): Modifier {
    if (!isDesktop || !enabled || onSecondaryClick == null) return this
    return pointerInput(onSecondaryClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { change -> change.consume() }
                    onSecondaryClick()
                }
            }
        }
    }
}

internal fun Modifier.nuvioHorizontalWheelScroll(
    scrollState: ScrollState,
    enabled: Boolean = true,
    allowVerticalWheel: Boolean = true,
): Modifier {
    if (!isDesktop || !enabled) return this
    return pointerInput(scrollState) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val scrollDelta = event.changes.firstOrNull()?.scrollDelta
                    val deltaPixels = nuvioHorizontalWheelPixels(
                        deltaX = scrollDelta?.x ?: 0f,
                        deltaY = scrollDelta?.y ?: 0f,
                        allowVerticalWheel = allowVerticalWheel,
                    )
                    if (
                        nuvioShouldConsumeHorizontalWheel(
                            deltaPixels = deltaPixels,
                            canScrollBackward = scrollState.value > 0,
                            canScrollForward = scrollState.value < scrollState.maxValue,
                        )
                    ) {
                        event.changes.forEach { change -> change.consume() }
                        scrollState.dispatchRawDelta(deltaPixels)
                    }
                }
            }
        }
    }
}

internal fun Modifier.nuvioLazyRowWheelScroll(
    listState: LazyListState,
    enabled: Boolean = true,
    allowVerticalWheel: Boolean = true,
): Modifier {
    if (!isDesktop || !enabled) return this
    return pointerInput(listState) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val scrollDelta = event.changes.firstOrNull()?.scrollDelta
                    val deltaPixels = nuvioHorizontalWheelPixels(
                        deltaX = scrollDelta?.x ?: 0f,
                        deltaY = scrollDelta?.y ?: 0f,
                        allowVerticalWheel = allowVerticalWheel,
                    )
                    if (
                        nuvioShouldConsumeHorizontalWheel(
                            deltaPixels = deltaPixels,
                            canScrollBackward = listState.canMoveBackward(),
                            canScrollForward = listState.canMoveForward(),
                        )
                    ) {
                        event.changes.forEach { change -> change.consume() }
                        listState.dispatchRawDelta(deltaPixels)
                    }
                }
            }
        }
    }
}

private fun LazyListState.canMoveBackward(): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

private fun LazyListState.canMoveForward(): Boolean {
    val layoutInfo = layoutInfo
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisibleIndex < layoutInfo.totalItemsCount - 1
}
