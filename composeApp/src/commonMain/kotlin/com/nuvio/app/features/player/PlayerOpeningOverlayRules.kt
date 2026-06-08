package com.nuvio.app.features.player

internal fun shouldShowOpeningOverlay(
    showLoadingOverlay: Boolean,
    initialLoadCompleted: Boolean,
    hasError: Boolean,
): Boolean = showLoadingOverlay && !initialLoadCompleted && !hasError

internal fun shouldShowPlayerChrome(
    initialLoadCompleted: Boolean,
    controlsVisible: Boolean,
    showParentalGuide: Boolean,
    playerControlsLocked: Boolean,
): Boolean = initialLoadCompleted && (controlsVisible || showParentalGuide) && !playerControlsLocked
