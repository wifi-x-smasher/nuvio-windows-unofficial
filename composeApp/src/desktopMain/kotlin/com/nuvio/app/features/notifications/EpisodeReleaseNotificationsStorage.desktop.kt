package com.nuvio.app.features.notifications

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object EpisodeReleaseNotificationsStorage {
    private val store = desktopPayloadStore("episode-release-notifications.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
