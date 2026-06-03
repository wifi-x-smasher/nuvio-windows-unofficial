package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import kotlin.math.roundToInt

@Composable
internal fun rememberPosterCardStyleUiState(): PosterCardStyleUiState {
    PosterCardStyleRepository.ensureLoaded()
    val uiState by PosterCardStyleRepository.uiState.collectAsState()
    val desktopScale = nuvioDesktopUiScale
    if (desktopScale == 1f) return uiState
    return uiState.copy(
        widthDp = (uiState.widthDp * desktopScale).roundToInt(),
        heightDp = (uiState.heightDp * desktopScale).roundToInt(),
        cornerRadiusDp = (uiState.cornerRadiusDp * desktopScale).roundToInt(),
    )
}
