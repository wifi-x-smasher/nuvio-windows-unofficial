package com.nuvio.app.features.player.desktop.mpv

import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.player.normalizeLanguageCode
import java.util.Locale

internal fun Color.toMpvSubtitleColorString(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return "#${a.hex()}${r.hex()}${g.hex()}${b.hex()}"
}

internal fun displaySubtitleTrackLabel(
    title: String?,
    language: String?,
    id: Int,
): String {
    val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }
    val languageName = displayLanguageName(language)

    return when {
        cleanTitle == null && languageName != null -> languageName
        cleanTitle == null -> "Subtitle $id"
        languageName == null -> cleanTitle
        cleanTitle.equals(languageName, ignoreCase = true) -> languageName
        cleanTitle.equals(language, ignoreCase = true) -> languageName
        cleanTitle.equals("sdh", ignoreCase = true) -> "$languageName (SDH)"
        cleanTitle.equals("forced", ignoreCase = true) -> "$languageName (Forced)"
        cleanTitle.startsWith("$languageName ", ignoreCase = true) -> cleanTitle
        else -> cleanTitle
    }
}

private fun displayLanguageName(language: String?): String? {
    val normalized = normalizeLanguageCode(language)
        ?.takeIf { it.isNotBlank() && it != "und" && it != "unknown" }
        ?: return null
    val locale = Locale.forLanguageTag(normalized)
    val display = locale.getDisplayName(Locale.ENGLISH).trim()
    return display.takeIf {
        it.isNotBlank() &&
            !it.equals(normalized, ignoreCase = true) &&
            it.any(Char::isLetter)
    }
}

private fun Int.hex(): String = toString(16).padStart(2, '0').uppercase()
