package com.nuvio.app.features.details

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object MetaScreenSettingsStorage {
    private val store = desktopPayloadStore("meta-screen-settings.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
