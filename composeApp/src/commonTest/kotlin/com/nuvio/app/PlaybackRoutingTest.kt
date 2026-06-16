package com.nuvio.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackRoutingTest {
    @Test
    fun externalPlaybackFollowsExplicitUserChoiceOnly() {
        assertFalse(shouldUseExternalPlayback(externalPlayerEnabled = false))
        assertTrue(shouldUseExternalPlayback(externalPlayerEnabled = true))
        assertTrue(shouldUseExternalPlayback(externalPlayerEnabled = false, forceExternal = true))
        assertFalse(shouldUseExternalPlayback(externalPlayerEnabled = true, forceInternal = true))
        assertFalse(
            shouldUseExternalPlayback(
                externalPlayerEnabled = false,
                forceExternal = true,
                forceInternal = true,
            ),
        )
    }
}
