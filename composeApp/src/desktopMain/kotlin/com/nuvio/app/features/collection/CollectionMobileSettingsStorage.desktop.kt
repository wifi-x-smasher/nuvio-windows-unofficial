package com.nuvio.app.features.collection

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object CollectionMobileSettingsStorage {
    private val store = desktopPayloadStore("collection-mobile-settings.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
