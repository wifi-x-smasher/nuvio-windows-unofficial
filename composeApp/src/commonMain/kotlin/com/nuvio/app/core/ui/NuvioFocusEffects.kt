package com.nuvio.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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
    focusedBorderWidth: Dp = 3.dp,
    focusedShadowElevation: Dp = 18.dp,
    interactionSource: MutableInteractionSource? = null,
    attachFocusable: Boolean = true,
): Modifier {
    if (!enabled || !isDesktop) return this

    // Focus is detected two ways: by observing this node's focus tree (onFocusChanged) and, when a
    // shared interactionSource is supplied, by reading the focus interactions emitted by the
    // clickable that owns it. The interactionSource path is order-independent and reliable even when
    // the focusable lives below a clip/background in the same chain.
    var focusObserved by remember { mutableStateOf(false) }
    val fallbackInteractionSource = remember { MutableInteractionSource() }
    val interactionFocused by (interactionSource ?: fallbackInteractionSource).collectIsFocusedAsState()
    val isFocused = focusObserved || interactionFocused
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "nuvio_desktop_focus_scale",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "nuvio_desktop_focus_ring",
    )
    val shadowElevation by animateFloatAsState(
        targetValue = if (isFocused) focusedShadowElevation.value else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "nuvio_desktop_focus_shadow",
    )
    val density = LocalDensity.current
    val ringColor = ThemeColors.getColorPalette(LocalAppTheme.current).focusRing

    return this
        .onFocusChanged { focusState ->
            focusObserved = focusState.isFocused || focusState.hasFocus
        }
        .then(if (attachFocusable) Modifier.focusable(interactionSource = interactionSource) else Modifier)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = false
            this.shadowElevation = with(density) { shadowElevation.dp.toPx() }
        }
        .drawWithContent {
            drawContent()
            if (ringAlpha <= 0f) return@drawWithContent
            val strokePx = with(density) { focusedBorderWidth.toPx() }
            // Inset the outline by half the stroke so the ring stays inside the component's
            // own bounds and is not clipped by parent containers (lazy rows, cards, etc.).
            val inset = strokePx / 2f
            val outlineSize = Size(
                width = (size.width - strokePx).coerceAtLeast(0f),
                height = (size.height - strokePx).coerceAtLeast(0f),
            )
            val outline = shape.createOutline(outlineSize, layoutDirection, this)
            translate(left = inset, top = inset) {
                // Soft outer halo for the "glow", then the crisp focus ring on top.
                drawOutline(
                    outline = outline,
                    color = ringColor.copy(alpha = ringAlpha * 0.40f),
                    style = Stroke(width = strokePx * 3.5f),
                )
                drawOutline(
                    outline = outline,
                    color = ringColor.copy(alpha = ringAlpha),
                    style = Stroke(width = strokePx),
                )
            }
        }
}
