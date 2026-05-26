package com.nuvio.app.features.watchprogress

import com.nuvio.app.core.desktop.DesktopAppPaths
import com.nuvio.app.core.desktop.DesktopJsonStore

internal actual object WatchProgressStorage {
    actual fun loadPayload(profileId: Int): String? =
        store(profileId).readTextOrNull()

    actual fun savePayload(profileId: Int, payload: String) =
        store(profileId).writeText(payload)

    private fun store(profileId: Int): DesktopJsonStore =
        DesktopJsonStore(DesktopAppPaths.dataFile("watch-progress-profile-$profileId.json"))
}
