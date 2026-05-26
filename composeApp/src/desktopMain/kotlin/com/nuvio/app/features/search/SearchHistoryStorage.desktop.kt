package com.nuvio.app.features.search

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object SearchHistoryStorage {
    private val store = desktopPayloadStore("search-history.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
