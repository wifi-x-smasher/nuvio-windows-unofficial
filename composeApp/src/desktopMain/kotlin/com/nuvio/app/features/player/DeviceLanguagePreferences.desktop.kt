package com.nuvio.app.features.player

import java.util.Locale

internal actual object DeviceLanguagePreferences {
    actual fun preferredLanguageCodes(): List<String> {
        val locale = Locale.getDefault()
        return listOf(locale.toLanguageTag(), locale.language)
            .filter { it.isNotBlank() }
            .distinct()
    }
}
