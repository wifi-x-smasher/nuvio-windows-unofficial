package com.nuvio.app.features.player

/**
 * Whether the media has actually opened (first frame / duration available), as opposed to the backend
 * merely having accepted the play request. The desktop MPV/MediaMP backend reports PLAYING the instant
 * playback is requested — before the first frame is decoded and before a duration is known — so gating
 * the opening overlay on `!isLoading` alone reveals a black player several seconds early. A positive
 * duration (media opened) or an advancing position (playback actually started) is the real signal.
 * (issue #12 follow-up)
 */
internal fun isOpeningMediaReady(
    isLoading: Boolean,
    durationMs: Long,
    positionMs: Long,
): Boolean = !isLoading && (durationMs > 0L || positionMs > 0L)

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
