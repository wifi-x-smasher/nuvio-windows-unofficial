package com.nuvio.app.features.streams

import com.nuvio.app.core.desktop.DesktopAppPaths
import com.nuvio.app.core.desktop.DesktopJsonStore
import com.nuvio.app.core.desktop.desktopSafeFilePart

internal actual object BingeGroupCacheStorage {
    actual fun load(hashedKey: String): String? =
        store(hashedKey).readTextOrNull()

    actual fun save(hashedKey: String, value: String) =
        store(hashedKey).writeText(value)

    actual fun remove(hashedKey: String) =
        store(hashedKey).clear()

    private fun store(hashedKey: String): DesktopJsonStore =
        DesktopJsonStore(DesktopAppPaths.dataFile("binge-group-${desktopSafeFilePart(hashedKey)}.json"))
}
