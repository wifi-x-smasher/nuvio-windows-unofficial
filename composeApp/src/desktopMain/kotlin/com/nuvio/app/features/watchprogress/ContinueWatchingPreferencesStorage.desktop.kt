package com.nuvio.app.features.watchprogress

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object ContinueWatchingPreferencesStorage {
    private val store = desktopPayloadStore("continue-watching-preferences.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
