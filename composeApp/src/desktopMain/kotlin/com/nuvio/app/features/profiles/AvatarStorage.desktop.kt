package com.nuvio.app.features.profiles

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object AvatarStorage {
    private val store = desktopPayloadStore("avatar-catalog.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
