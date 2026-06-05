package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerTimedSubtitleParserTest {
    @Test
    fun parsesSrtCues() {
        val cues = parsePlayerTimedSubtitles(
            """
            1
            00:00:01,250 --> 00:00:03,500
            Hello world

            2
            00:00:04,000 --> 00:00:05,000
            Second line
            wrapped
            """.trimIndent(),
        )

        assertEquals(2, cues.size)
        assertEquals(1_250L, cues[0].startMs)
        assertEquals(3_500L, cues[0].endMs)
        assertEquals("Hello world", cues[0].text)
        assertEquals("Second line\nwrapped", cues[1].text)
    }

    @Test
    fun parsesWebVttCuesAndStripsTags() {
        val cues = parsePlayerTimedSubtitles(
            """
            WEBVTT

            00:00:10.500 --> 00:00:12.000 align:center
            <i>Styled</i> subtitle
            """.trimIndent(),
        )

        assertEquals(1, cues.size)
        assertEquals(10_500L, cues.single().startMs)
        assertEquals(12_000L, cues.single().endMs)
        assertEquals("Styled subtitle", cues.single().text)
    }
}
