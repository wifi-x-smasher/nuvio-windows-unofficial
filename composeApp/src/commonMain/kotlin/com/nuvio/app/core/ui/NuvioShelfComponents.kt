package com.nuvio.app.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_view_all
import nuvio.composeapp.generated.resources.poster_logo_content_description
import org.jetbrains.compose.resources.stringResource

enum class NuvioPosterShape {
    Poster,
    Square,
    Landscape,
}

enum class NuvioViewAllPillSize {
    Default,
    Compact,
}

@Composable
fun <T> NuvioShelfSection(
    title: String,
    entries: List<T>,
    modifier: Modifier = Modifier,
    headerHorizontalPadding: Dp = 0.dp,
    rowContentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 10.dp,
    showHeaderAccent: Boolean = true,
    onViewAllClick: (() -> Unit)? = null,
    viewAllPillSize: NuvioViewAllPillSize = NuvioViewAllPillSize.Default,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val desktopScale = nuvioDesktopUiScale
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollControls = entries.size > 1
    fun scrollShelf(direction: Int) {
        val targetIndex = (listState.firstVisibleItemIndex + (direction * 4))
            .coerceIn(0, entries.lastIndex.coerceAtLeast(0))
        scope.launch {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp.scaledByDesktop(desktopScale)),
    ) {
        if (title.isNotBlank()) {
            NuvioShelfSectionHeader(
                title = title,
                modifier = Modifier.padding(horizontal = headerHorizontalPadding.scaledByDesktop(desktopScale)),
                showAccent = showHeaderAccent,
                onViewAllClick = onViewAllClick,
                viewAllPillSize = viewAllPillSize,
                onScrollLeftClick = if (showScrollControls) { { scrollShelf(-1) } } else null,
                onScrollRightClick = if (showScrollControls) { { scrollShelf(1) } } else null,
            )
        }
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .nuvioTvDirectionalFocusTraversal()
                .nuvioLazyRowWheelScroll(listState, allowVerticalWheel = false),
            contentPadding = rowContentPadding.scaledByDesktop(desktopScale),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing.scaledByDesktop(desktopScale)),
        ) {
            if (key != null) {
                items(
                    items = entries.withDuplicateSafeLazyKeys(key),
                    key = { entry -> entry.lazyKey },
                ) { keyedEntry ->
                    itemContent(keyedEntry.value)
                }
            } else {
                items(entries) { entry ->
                    itemContent(entry)
                }
            }
        }
    }
}

@Composable
fun NuvioPosterCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    shape: NuvioPosterShape = NuvioPosterShape.Poster,
    detailLine: String? = null,
    showTitleBelow: Boolean = true,
    bottomLeftLogoUrl: String? = null,
    bottomLeftText: String? = null,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val desktopScale = nuvioDesktopUiScale
    val posterCardStyle = rememberPosterCardStyleUiState()
    val cardWidth = shape.cardWidth(basePosterWidthDp = posterCardStyle.widthDp)
    val cardShape = RoundedCornerShape(posterCardStyle.cornerRadiusDp.dp)
    val catalogLogoOverlaySize = catalogLogoOverlaySize(
        basePosterWidthDp = posterCardStyle.widthDp,
        shape = shape,
    )
    val shouldShowTitleBelow = showTitleBelow && !posterCardStyle.hideLabelsEnabled
    val focusScale = if (shape == NuvioPosterShape.Landscape) 1.025f else 1.04f

    Column(
        modifier = modifier.width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp.scaledByDesktop(desktopScale)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(shape.aspectRatio)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surface)
                .posterCardClickable(onClick = onClick, onLongClick = onLongClick)
                .nuvioDesktopFocusEffect(
                    enabled = onClick != null || onLongClick != null,
                    shape = cardShape,
                    focusedScale = focusScale,
                    focusedShadowElevation = 22.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 14.dp.scaledByDesktop(desktopScale)),
                    style = MaterialTheme.typography.titleMedium.scaledByDesktop(desktopScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!bottomLeftLogoUrl.isNullOrBlank() || !bottomLeftText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            horizontal = 10.dp.scaledByDesktop(desktopScale),
                            vertical = 10.dp.scaledByDesktop(desktopScale),
                        ),
                ) {
                    if (!bottomLeftLogoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = bottomLeftLogoUrl,
                            contentDescription = stringResource(Res.string.poster_logo_content_description, title),
                            modifier = Modifier
                                .width(catalogLogoOverlaySize.width)
                                .height(catalogLogoOverlaySize.height),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = bottomLeftText.orEmpty(),
                            style = MaterialTheme.typography.labelMedium.scaledByDesktop(desktopScale),
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = catalogLogoOverlaySize.textMaxWidth),
                        )
                    }
                }
            }

            NuvioPosterWatchedOverlay(isWatched = isWatched)
        }
        if (shouldShowTitleBelow) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.scaledByDesktop(desktopScale),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detailLine.isNullOrBlank()) {
                Text(
                    text = detailLine,
                    style = MaterialTheme.typography.labelSmall.scaledByDesktop(desktopScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(modifier = Modifier.height(0.dp))
            }
        } else {
            Box(modifier = Modifier.height(0.dp))
        }
    }
}

@Composable
private fun NuvioShelfSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    showAccent: Boolean = true,
    onViewAllClick: (() -> Unit)? = null,
    viewAllPillSize: NuvioViewAllPillSize = NuvioViewAllPillSize.Default,
    onScrollLeftClick: (() -> Unit)? = null,
    onScrollRightClick: (() -> Unit)? = null,
) {
    val desktopScale = nuvioDesktopUiScale
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.scaledByDesktop(desktopScale),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAccent) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(60.dp.scaledByDesktop(desktopScale))
                        .height(4.dp.scaledByDesktop(desktopScale))
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp),
                    ),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp.scaledByDesktop(desktopScale)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onScrollLeftClick != null) {
                NuvioShelfArrowButton(
                    onClick = onScrollLeftClick,
                    direction = ShelfArrowDirection.Left,
                )
            }
            if (onScrollRightClick != null) {
                NuvioShelfArrowButton(
                    onClick = onScrollRightClick,
                    direction = ShelfArrowDirection.Right,
                )
            }
            if (onViewAllClick != null) {
                NuvioViewAllPill(
                    onClick = onViewAllClick,
                    size = viewAllPillSize,
                )
            }
        }
    }
}

private enum class ShelfArrowDirection {
    Left,
    Right,
}

@Composable
private fun NuvioShelfArrowButton(
    onClick: () -> Unit,
    direction: ShelfArrowDirection,
) {
    val desktopScale = nuvioDesktopUiScale
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .height(38.dp.scaledByDesktop(desktopScale))
            .width(38.dp.scaledByDesktop(desktopScale))
            .background(
                color = colorScheme.surface.copy(alpha = 0.82f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .nuvioDesktopFocusEffect(
                enabled = true,
                shape = shape,
                focusedScale = 1.04f,
                focusedShadowElevation = 10.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (direction) {
                ShelfArrowDirection.Left -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
                ShelfArrowDirection.Right -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
            },
            contentDescription = null,
            tint = colorScheme.onSurface,
            modifier = Modifier.height(22.dp.scaledByDesktop(desktopScale)),
        )
    }
}

@Composable
private fun NuvioViewAllPill(
    onClick: (() -> Unit)?,
    size: NuvioViewAllPillSize,
) {
    val desktopScale = nuvioDesktopUiScale
    val colorScheme = MaterialTheme.colorScheme
    val isAmoled = colorScheme.background == androidx.compose.ui.graphics.Color.Black && colorScheme.surface == androidx.compose.ui.graphics.Color(0xFF050505)
    val shape = RoundedCornerShape(20.dp)
    val horizontalPadding = (if (size == NuvioViewAllPillSize.Compact) 12.dp else 18.dp).scaledByDesktop(desktopScale)
    val verticalPadding = (if (size == NuvioViewAllPillSize.Compact) 9.dp else 14.dp).scaledByDesktop(desktopScale)
    val textStyle = if (size == NuvioViewAllPillSize.Compact) {
        MaterialTheme.typography.labelLarge
    } else {
        MaterialTheme.typography.titleMedium
    }.scaledByDesktop(desktopScale)
    val iconSpacing = (if (size == NuvioViewAllPillSize.Compact) 2.dp else 4.dp).scaledByDesktop(desktopScale)

    Row(
        modifier = Modifier
            .background(
                color = if (isAmoled) androidx.compose.ui.graphics.Color(0xFF0D0D0D) else colorScheme.surface,
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .nuvioDesktopFocusEffect(
                enabled = onClick != null,
                shape = shape,
                focusedScale = 1.02f,
                focusedShadowElevation = 10.dp,
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(iconSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.home_view_all),
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(
                (if (size == NuvioViewAllPillSize.Compact) 16.dp else 20.dp).scaledByDesktop(desktopScale),
            ),
        )
    }
}

private fun PaddingValues.scaledByDesktop(scale: Float): PaddingValues =
    if (scale == 1f) {
        this
    } else {
        PaddingValues(
            start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr).scaledByDesktop(scale),
            top = calculateTopPadding().scaledByDesktop(scale),
            end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr).scaledByDesktop(scale),
            bottom = calculateBottomPadding().scaledByDesktop(scale),
        )
    }

private val NuvioPosterShape.aspectRatio: Float
    get() = when (this) {
        NuvioPosterShape.Poster -> 0.675f
        NuvioPosterShape.Square -> 1f
        NuvioPosterShape.Landscape -> PosterLandscapeAspectRatio
    }

private data class CatalogLogoOverlaySize(
    val width: Dp,
    val height: Dp,
    val textMaxWidth: Dp,
)

private fun catalogLogoOverlaySize(
    basePosterWidthDp: Int,
    shape: NuvioPosterShape,
): CatalogLogoOverlaySize =
    if (shape == NuvioPosterShape.Landscape) {
        when {
            basePosterWidthDp <= 108 -> CatalogLogoOverlaySize(width = 92.dp, height = 24.dp, textMaxWidth = 120.dp)
            basePosterWidthDp <= 120 -> CatalogLogoOverlaySize(width = 104.dp, height = 28.dp, textMaxWidth = 132.dp)
            basePosterWidthDp <= 132 -> CatalogLogoOverlaySize(width = 116.dp, height = 30.dp, textMaxWidth = 144.dp)
            else -> CatalogLogoOverlaySize(width = 128.dp, height = 34.dp, textMaxWidth = 156.dp)
        }
    } else {
        when {
            basePosterWidthDp <= 108 -> CatalogLogoOverlaySize(width = 72.dp, height = 18.dp, textMaxWidth = 92.dp)
            basePosterWidthDp <= 120 -> CatalogLogoOverlaySize(width = 80.dp, height = 20.dp, textMaxWidth = 104.dp)
            basePosterWidthDp <= 132 -> CatalogLogoOverlaySize(width = 88.dp, height = 22.dp, textMaxWidth = 112.dp)
            else -> CatalogLogoOverlaySize(width = 96.dp, height = 24.dp, textMaxWidth = 124.dp)
        }
    }

private fun NuvioPosterShape.cardWidth(basePosterWidthDp: Int): Dp =
    when (this) {
        NuvioPosterShape.Poster -> basePosterWidthDp.dp
        NuvioPosterShape.Square -> basePosterWidthDp.dp
        NuvioPosterShape.Landscape -> landscapePosterWidth(basePosterWidthDp)
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.posterCardClickable(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
): Modifier =
    if (onClick != null || onLongClick != null) {
        this
            .nuvioSecondaryClickAsLongPress(onSecondaryClick = onLongClick)
            .nuvioTvSelectKeys(onSelect = onClick)
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            )
    } else {
        this
    }
