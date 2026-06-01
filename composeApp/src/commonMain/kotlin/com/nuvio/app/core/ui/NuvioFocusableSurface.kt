package com.nuvio.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop

@Composable
internal fun NuvioFocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    minWidth: Dp = 40.dp,
    minHeight: Dp = 40.dp,
    focusedScale: Float = 1.018f,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val pointerScale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            pressed -> 0.96f
            hovered && isDesktop -> 1.006f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 120),
        label = "nuvio_focusable_surface_pointer_scale",
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = minWidth, minHeight = minHeight)
            .graphicsLayer {
                scaleX = pointerScale
                scaleY = pointerScale
            }
            .clip(shape)
            .background(containerColor, shape)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .nuvioDesktopFocusEffect(
                enabled = enabled,
                shape = shape,
                focusedScale = focusedScale,
                focusedShadowElevation = 10.dp,
            )
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}
