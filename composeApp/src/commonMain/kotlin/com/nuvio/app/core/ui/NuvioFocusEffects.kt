package com.nuvio.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop

@Composable
internal fun Modifier.nuvioDesktopFocusEffect(
    enabled: Boolean,
    shape: Shape,
    focusedScale: Float = 1.035f,
    focusedBorderWidth: Dp = 2.dp,
    focusedShadowElevation: Dp = 18.dp,
): Modifier {
    if (!enabled || !isDesktop) return this

    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "nuvio_desktop_focus_scale",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.86f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "nuvio_desktop_focus_ring",
    )
    val shadowElevation by animateFloatAsState(
        targetValue = if (isFocused) focusedShadowElevation.value else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "nuvio_desktop_focus_shadow",
    )
    val density = LocalDensity.current
    val focusRingColor = MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha)

    return this
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused || focusState.hasFocus
        }
        .focusable()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = false
            this.shadowElevation = with(density) { shadowElevation.dp.toPx() }
        }
        .border(focusedBorderWidth, focusRingColor, shape)
}
