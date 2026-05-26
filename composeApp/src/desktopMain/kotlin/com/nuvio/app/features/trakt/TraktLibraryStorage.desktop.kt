package com.nuvio.app.features.trakt

import com.nuvio.app.core.desktop.DesktopPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object TraktLibraryStorage {
    private const val payloadKey = "trakt_library_payload"
    private val preferences = DesktopPreferences("nuvio_trakt_library")

    actual fun loadPayload(): String? =
        preferences.getString(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        preferences.putString(ProfileScopedKey.of(payloadKey), payload)
    }
}
