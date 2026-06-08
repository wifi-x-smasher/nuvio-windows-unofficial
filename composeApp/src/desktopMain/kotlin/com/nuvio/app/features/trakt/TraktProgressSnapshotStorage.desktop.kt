package com.nuvio.app.features.trakt

import com.nuvio.app.core.desktop.DesktopPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object TraktProgressSnapshotStorage {
    private const val payloadKey = "trakt_progress_snapshot_payload"
    private val preferences = DesktopPreferences("nuvio_trakt_progress")

    actual fun loadPayload(): String? =
        preferences.getString(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        preferences.putString(ProfileScopedKey.of(payloadKey), payload)
    }
}
