package com.nuvio.app.core.ui

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object PosterCardStyleStorage {
    private val store = desktopPayloadStore("poster-card-style.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
