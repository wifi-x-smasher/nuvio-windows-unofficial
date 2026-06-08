package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerOpeningOverlayRulesTest {
    @Test
    fun openingOverlayHidesControlsUntilInitialMediaReady() {
        assertTrue(
            shouldShowOpeningOverlay(
                showLoadingOverlay = true,
                initialLoadCompleted = false,
                hasError = false,
            ),
        )

        assertFalse(
            shouldShowPlayerChrome(
                initialLoadCompleted = false,
                controlsVisible = true,
                showParentalGuide = false,
                playerControlsLocked = false,
            ),
        )
    }

    @Test
    fun playerChromeReturnsAfterInitialMediaReady() {
        assertFalse(
            shouldShowOpeningOverlay(
                showLoadingOverlay = true,
                initialLoadCompleted = true,
                hasError = false,
            ),
        )

        assertTrue(
            shouldShowPlayerChrome(
                initialLoadCompleted = true,
                controlsVisible = true,
                showParentalGuide = false,
                playerControlsLocked = false,
            ),
        )
    }

    @Test
    fun openingMedia_notReady_whileBackendReportsPlayingBeforeFirstFrame() {
        // MPV/MediaMP flips to PLAYING (isLoading=false) immediately, before duration is known and
        // before any frame is decoded — the overlay must stay up. (issue #12 follow-up)
        assertFalse(
            isOpeningMediaReady(
                isLoading = false,
                durationMs = 0L,
                positionMs = 0L,
            ),
        )
    }

    @Test
    fun openingMedia_ready_onceDurationKnown() {
        assertTrue(
            isOpeningMediaReady(
                isLoading = false,
                durationMs = 9_644_000L,
                positionMs = 0L,
            ),
        )
    }

    @Test
    fun openingMedia_ready_oncePositionAdvances_forDurationlessLiveStreams() {
        assertTrue(
            isOpeningMediaReady(
                isLoading = false,
                durationMs = 0L,
                positionMs = 2_000L,
            ),
        )
    }

    @Test
    fun openingMedia_notReady_whileStillLoading() {
        assertFalse(
            isOpeningMediaReady(
                isLoading = true,
                durationMs = 9_644_000L,
                positionMs = 5_000L,
            ),
        )
    }
}
