package com.nuvio.app.features.profiles

import com.nuvio.app.core.desktop.DesktopAppPaths
import com.nuvio.app.core.desktop.DesktopJsonStore

internal actual object ProfilePinCacheStorage {
    actual fun loadPayload(profileIndex: Int): String? =
        store(profileIndex).readTextOrNull()

    actual fun savePayload(profileIndex: Int, payload: String) =
        store(profileIndex).writeText(payload)

    actual fun removePayload(profileIndex: Int) =
        store(profileIndex).clear()

    private fun store(profileIndex: Int): DesktopJsonStore =
        DesktopJsonStore(DesktopAppPaths.dataFile("profile-pin-cache-$profileIndex.json"))
}
