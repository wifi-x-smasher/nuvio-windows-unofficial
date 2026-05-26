package com.nuvio.app.features.plugins

import com.nuvio.app.core.desktop.DesktopAppPaths
import com.nuvio.app.core.desktop.DesktopJsonStore

internal object PluginStorage {
    fun loadState(profileId: Int): String? =
        store(profileId).readTextOrNull()

    fun saveState(profileId: Int, payload: String) {
        store(profileId).writeText(payload)
    }

    private fun store(profileId: Int): DesktopJsonStore =
        DesktopJsonStore(DesktopAppPaths.dataFile("plugins-state-profile-$profileId.json"))
}

internal fun currentPluginPlatform(): String = "desktop"

internal fun currentEpochMillis(): Long = System.currentTimeMillis()
