package com.nuvio.app.features.home

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object HomeCatalogSettingsStorage {
    private val store = desktopPayloadStore("home-catalog-settings.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
