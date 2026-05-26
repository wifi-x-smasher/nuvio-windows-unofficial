package com.nuvio.app.features.downloads

import com.nuvio.app.core.desktop.desktopPayloadStore

internal actual object DownloadsStorage {
    private val store = desktopPayloadStore("downloads.json")

    actual fun loadPayload(): String? = store.readTextOrNull()

    actual fun savePayload(payload: String) = store.writeText(payload)
}
