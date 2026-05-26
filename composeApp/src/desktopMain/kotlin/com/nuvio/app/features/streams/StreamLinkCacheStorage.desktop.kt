package com.nuvio.app.features.streams

import com.nuvio.app.core.desktop.DesktopAppPaths
import com.nuvio.app.core.desktop.DesktopJsonStore
import com.nuvio.app.core.desktop.desktopSafeFilePart

internal actual object StreamLinkCacheStorage {
    actual fun loadEntry(hashedKey: String): String? =
        store(hashedKey).readTextOrNull()

    actual fun saveEntry(hashedKey: String, payload: String) =
        store(hashedKey).writeText(payload)

    actual fun removeEntry(hashedKey: String) =
        store(hashedKey).clear()

    private fun store(hashedKey: String): DesktopJsonStore =
        DesktopJsonStore(DesktopAppPaths.dataFile("stream-link-${desktopSafeFilePart(hashedKey)}.json"))
}
