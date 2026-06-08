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
}
