package com.nuvio.app.features.streams

import com.nuvio.app.core.desktop.DesktopPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object StreamBadgeSettingsStorage {
    private const val streamBadgeRulesKey = "stream_badge_rules"
    private const val legacyDebridStreamBadgeRulesKey = "debrid_stream_badge_rules"

    private val preferences = DesktopPreferences("nuvio_stream_badge_settings")
    private val legacyDebridPreferences = DesktopPreferences("nuvio_debrid_settings")
    private val syncKeys = listOf(streamBadgeRulesKey)

    actual fun loadStreamBadgeRules(): String? =
        preferences.getString(ProfileScopedKey.of(streamBadgeRulesKey))

    actual fun saveStreamBadgeRules(rules: String) {
        preferences.putString(ProfileScopedKey.of(streamBadgeRulesKey), rules)
    }

    actual fun loadLegacyDebridStreamBadgeRules(): String? =
        legacyDebridPreferences.getString(ProfileScopedKey.of(legacyDebridStreamBadgeRulesKey))

    actual fun clearLegacyDebridStreamBadgeRules() {
        legacyDebridPreferences.remove(ProfileScopedKey.of(legacyDebridStreamBadgeRulesKey))
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadStreamBadgeRules()?.let { put(streamBadgeRulesKey, encodeSyncString(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        syncKeys.forEach { preferences.remove(ProfileScopedKey.of(it)) }
        payload.decodeSyncString(streamBadgeRulesKey)?.let(::saveStreamBadgeRules)
        clearLegacyDebridStreamBadgeRules()
    }
}
