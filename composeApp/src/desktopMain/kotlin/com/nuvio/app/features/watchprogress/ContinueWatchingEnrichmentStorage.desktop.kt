package com.nuvio.app.features.watchprogress

import com.nuvio.app.core.desktop.DesktopAppPaths
import com.nuvio.app.core.desktop.DesktopJsonStore
import com.nuvio.app.core.desktop.desktopSafeFilePart

internal actual object ContinueWatchingEnrichmentStorage {
    actual fun loadPayload(key: String): String? =
        store(key).readTextOrNull()

    actual fun savePayload(key: String, payload: String) =
        store(key).writeText(payload)

    private fun store(key: String): DesktopJsonStore =
        DesktopJsonStore(DesktopAppPaths.dataFile("cw-enrichment-${desktopSafeFilePart(key)}.json"))
}
