package com.nuvio.app.features.player

import com.nuvio.app.features.player.skip.PlayerNextEpisodeRules
import com.nuvio.app.features.player.skip.SkipInterval
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerNextEpisodeRulesTest {
    @Test
    fun `auto skip only fires for active supported intervals once`() {
        val intro = SkipInterval(
            startTime = 12.0,
            endTime = 72.0,
            type = "intro",
            provider = "introdb",
        )
        val key = with(PlayerNextEpisodeRules) { intro.autoSkipKey() }

        assertTrue(
            PlayerNextEpisodeRules.shouldAutoSkipInterval(
                interval = intro,
                alreadySkippedKeys = emptySet(),
                dismissed = false,
                initialLoadCompleted = true,
                pausedOverlayVisible = false,
            ),
        )
        assertFalse(
            PlayerNextEpisodeRules.shouldAutoSkipInterval(
                interval = intro,
                alreadySkippedKeys = setOf(key),
                dismissed = false,
                initialLoadCompleted = true,
                pausedOverlayVisible = false,
            ),
        )
        assertFalse(
            PlayerNextEpisodeRules.shouldAutoSkipInterval(
                interval = intro.copy(type = "chapter"),
                alreadySkippedKeys = emptySet(),
                dismissed = false,
                initialLoadCompleted = true,
                pausedOverlayVisible = false,
            ),
        )
    }

    @Test
    fun `still watching prompt waits for enabled aired binge streak`() {
        assertFalse(
            PlayerNextEpisodeRules.shouldAskStillWatching(
                autoPlayEnabled = true,
                nextEpisodeHasAired = true,
                consecutiveAutoPlayedEpisodes = 2,
            ),
        )
        assertTrue(
            PlayerNextEpisodeRules.shouldAskStillWatching(
                autoPlayEnabled = true,
                nextEpisodeHasAired = true,
                consecutiveAutoPlayedEpisodes = 3,
            ),
        )
        assertFalse(
            PlayerNextEpisodeRules.shouldAskStillWatching(
                autoPlayEnabled = false,
                nextEpisodeHasAired = true,
                consecutiveAutoPlayedEpisodes = 3,
            ),
        )
        assertFalse(
            PlayerNextEpisodeRules.shouldAskStillWatching(
                autoPlayEnabled = true,
                nextEpisodeHasAired = false,
                consecutiveAutoPlayedEpisodes = 3,
            ),
        )
    }
}
