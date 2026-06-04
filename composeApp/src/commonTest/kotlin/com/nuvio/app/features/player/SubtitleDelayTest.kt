package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleDelayTest {
    @Test
    fun convertsSubtitleDelayMillisToMpvSeconds() {
        assertEquals(0.0, subtitleDelayMillisToMpvSeconds(0))
        assertEquals(0.25, subtitleDelayMillisToMpvSeconds(250))
        assertEquals(-1.5, subtitleDelayMillisToMpvSeconds(-1_500))
    }

    @Test
    fun formatsSubtitleDelayForPlayerUi() {
        assertEquals("0 ms", formatSubtitleDelayMillis(0))
        assertEquals("+250 ms", formatSubtitleDelayMillis(250))
        assertEquals("-250 ms", formatSubtitleDelayMillis(-250))
        assertEquals("+1.5 s", formatSubtitleDelayMillis(1_500))
        assertEquals("-2 s", formatSubtitleDelayMillis(-2_000))
    }

    @Test
    fun clampsSubtitleDelayToPracticalRange() {
        assertEquals(10_000, clampSubtitleDelayMillis(40_000))
        assertEquals(-10_000, clampSubtitleDelayMillis(-40_000))
        assertEquals(750, clampSubtitleDelayMillis(750))
    }
}
