package com.nuvio.app.features.profiles

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object ProfileStorage {
    private val store = desktopPayloadStore("profiles.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
