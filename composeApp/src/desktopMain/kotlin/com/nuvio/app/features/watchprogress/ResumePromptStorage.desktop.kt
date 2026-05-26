package com.nuvio.app.features.watchprogress

import com.nuvio.app.core.desktop.DesktopPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object ResumePromptStorage {
    private const val wasInPlayerKey = "was_in_player"
    private const val lastPlayerVideoIdKey = "last_player_video_id"
    private val preferences = DesktopPreferences("nuvio_resume_prompt")

    actual fun loadWasInPlayer(): Boolean =
        preferences.getBoolean(ProfileScopedKey.of(wasInPlayerKey)) ?: false

    actual fun saveWasInPlayer(value: Boolean) {
        preferences.putBoolean(ProfileScopedKey.of(wasInPlayerKey), value)
    }

    actual fun loadLastPlayerVideoId(): String? =
        preferences.getString(ProfileScopedKey.of(lastPlayerVideoIdKey))

    actual fun saveLastPlayerVideoId(videoId: String?) {
        preferences.putString(ProfileScopedKey.of(lastPlayerVideoIdKey), videoId)
    }
}
