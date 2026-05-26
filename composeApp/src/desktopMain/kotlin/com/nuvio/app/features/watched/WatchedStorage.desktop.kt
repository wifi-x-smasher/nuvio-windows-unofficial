package com.nuvio.app.features.watched

import com.nuvio.app.core.desktop.DesktopAppPaths
import com.nuvio.app.core.desktop.DesktopJsonStore

actual object WatchedStorage {
    actual fun loadPayload(profileId: Int): String? =
        store(profileId).readTextOrNull()

    actual fun savePayload(profileId: Int, payload: String) =
        store(profileId).writeText(payload)

    private fun store(profileId: Int): DesktopJsonStore =
        DesktopJsonStore(DesktopAppPaths.dataFile("watched-profile-$profileId.json"))
}
