package com.nuvio.app.features.collection

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object CollectionStorage {
    private val store = desktopPayloadStore("collections.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
