package com.nuvio.app.features.trakt

import com.nuvio.app.core.desktop.DesktopSecureStore
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object TraktAuthStorage {
    private const val payloadKey = "trakt_auth_payload"

    actual fun loadPayload(): String? =
        DesktopSecureStore.readString(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        DesktopSecureStore.writeString(ProfileScopedKey.of(payloadKey), payload)
    }
}
