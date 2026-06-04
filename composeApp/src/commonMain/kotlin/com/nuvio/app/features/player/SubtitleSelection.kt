package com.nuvio.app.features.player

internal fun findPreferredAddonSubtitleIndex(
    subtitles: List<AddonSubtitle>,
    targets: List<String>,
): Int {
    if (subtitles.isEmpty() || targets.isEmpty()) return -1

    for (target in targets) {
        val normalizedTarget = normalizeLanguageCode(target) ?: continue
        if (normalizedTarget == SubtitleLanguageOption.FORCED) continue

        val matchIndex = subtitles.indexOfFirst { subtitle ->
            languageMatchesPreference(
                trackLanguage = subtitle.language,
                targetLanguage = normalizedTarget,
            )
        }
        if (matchIndex >= 0) return matchIndex
    }

    return -1
}
