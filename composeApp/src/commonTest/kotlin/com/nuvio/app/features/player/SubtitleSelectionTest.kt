package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleSelectionTest {
    @Test
    fun picksPreferredAddonSubtitleByExactLanguage() {
        val subtitles = listOf(
            addonSubtitle(id = "en", language = "en"),
            addonSubtitle(id = "nl", language = "nl"),
        )

        val index = findPreferredAddonSubtitleIndex(
            subtitles = subtitles,
            targets = listOf("nl"),
        )

        assertEquals(1, index)
    }

    @Test
    fun picksPreferredAddonSubtitleByPrimaryLanguage() {
        val subtitles = listOf(
            addonSubtitle(id = "en", language = "en-US"),
            addonSubtitle(id = "nl", language = "nl-NL"),
        )

        val index = findPreferredAddonSubtitleIndex(
            subtitles = subtitles,
            targets = listOf("nl-BE"),
        )

        assertEquals(1, index)
    }

    @Test
    fun respectsTargetPriority() {
        val subtitles = listOf(
            addonSubtitle(id = "nl", language = "nl"),
            addonSubtitle(id = "fr", language = "fr"),
        )

        val index = findPreferredAddonSubtitleIndex(
            subtitles = subtitles,
            targets = listOf("fr", "nl"),
        )

        assertEquals(1, index)
    }

    @Test
    fun doesNotSelectForcedOnlyAddonSubtitlePreference() {
        val subtitles = listOf(
            addonSubtitle(id = "nl", language = "nl"),
        )

        val index = findPreferredAddonSubtitleIndex(
            subtitles = subtitles,
            targets = listOf(SubtitleLanguageOption.FORCED),
        )

        assertEquals(-1, index)
    }

    @Test
    fun doesNotSelectWhenTargetsAreEmpty() {
        val subtitles = listOf(
            addonSubtitle(id = "nl", language = "nl"),
        )

        val index = findPreferredAddonSubtitleIndex(
            subtitles = subtitles,
            targets = emptyList(),
        )

        assertEquals(-1, index)
    }

    private fun addonSubtitle(id: String, language: String): AddonSubtitle =
        AddonSubtitle(
            id = id,
            url = "https://example.invalid/$id.vtt",
            language = language,
            display = id,
        )
}
