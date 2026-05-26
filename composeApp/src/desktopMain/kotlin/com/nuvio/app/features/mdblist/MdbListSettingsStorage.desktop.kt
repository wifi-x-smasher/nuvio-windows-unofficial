package com.nuvio.app.features.mdblist

import com.nuvio.app.core.desktop.DesktopPreferences
import com.nuvio.app.core.desktop.DesktopSecureStore
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object MdbListSettingsStorage {
    private const val enabledKey = "mdblist_enabled"
    private const val apiKey = "mdblist_api_key"
    private const val useImdbKey = "mdblist_use_imdb"
    private const val useTmdbKey = "mdblist_use_tmdb"
    private const val useTomatoesKey = "mdblist_use_tomatoes"
    private const val useMetacriticKey = "mdblist_use_metacritic"
    private const val useTraktKey = "mdblist_use_trakt"
    private const val useLetterboxdKey = "mdblist_use_letterboxd"
    private const val useAudienceKey = "mdblist_use_audience"
    private val syncKeys = listOf(
        enabledKey,
        apiKey,
        useImdbKey,
        useTmdbKey,
        useTomatoesKey,
        useMetacriticKey,
        useTraktKey,
        useLetterboxdKey,
        useAudienceKey,
    )
    private val preferences = DesktopPreferences("nuvio_mdblist_settings")

    actual fun loadEnabled(): Boolean? = loadBoolean(enabledKey)
    actual fun saveEnabled(enabled: Boolean) = saveBoolean(enabledKey, enabled)

    actual fun loadApiKey(): String? =
        DesktopSecureStore.readString(ProfileScopedKey.of(apiKey))
            ?: preferences.getString(ProfileScopedKey.of(apiKey))

    actual fun saveApiKey(apiKey: String) {
        DesktopSecureStore.writeString(ProfileScopedKey.of(this.apiKey), apiKey)
        preferences.remove(ProfileScopedKey.of(this.apiKey))
    }

    actual fun loadUseImdb(): Boolean? = loadBoolean(useImdbKey)
    actual fun saveUseImdb(enabled: Boolean) = saveBoolean(useImdbKey, enabled)
    actual fun loadUseTmdb(): Boolean? = loadBoolean(useTmdbKey)
    actual fun saveUseTmdb(enabled: Boolean) = saveBoolean(useTmdbKey, enabled)
    actual fun loadUseTomatoes(): Boolean? = loadBoolean(useTomatoesKey)
    actual fun saveUseTomatoes(enabled: Boolean) = saveBoolean(useTomatoesKey, enabled)
    actual fun loadUseMetacritic(): Boolean? = loadBoolean(useMetacriticKey)
    actual fun saveUseMetacritic(enabled: Boolean) = saveBoolean(useMetacriticKey, enabled)
    actual fun loadUseTrakt(): Boolean? = loadBoolean(useTraktKey)
    actual fun saveUseTrakt(enabled: Boolean) = saveBoolean(useTraktKey, enabled)
    actual fun loadUseLetterboxd(): Boolean? = loadBoolean(useLetterboxdKey)
    actual fun saveUseLetterboxd(enabled: Boolean) = saveBoolean(useLetterboxdKey, enabled)
    actual fun loadUseAudience(): Boolean? = loadBoolean(useAudienceKey)
    actual fun saveUseAudience(enabled: Boolean) = saveBoolean(useAudienceKey, enabled)

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(enabledKey, encodeSyncBoolean(it)) }
        loadApiKey()?.let { put(apiKey, encodeSyncString(it)) }
        loadUseImdb()?.let { put(useImdbKey, encodeSyncBoolean(it)) }
        loadUseTmdb()?.let { put(useTmdbKey, encodeSyncBoolean(it)) }
        loadUseTomatoes()?.let { put(useTomatoesKey, encodeSyncBoolean(it)) }
        loadUseMetacritic()?.let { put(useMetacriticKey, encodeSyncBoolean(it)) }
        loadUseTrakt()?.let { put(useTraktKey, encodeSyncBoolean(it)) }
        loadUseLetterboxd()?.let { put(useLetterboxdKey, encodeSyncBoolean(it)) }
        loadUseAudience()?.let { put(useAudienceKey, encodeSyncBoolean(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        syncKeys.forEach {
            preferences.remove(ProfileScopedKey.of(it))
            DesktopSecureStore.remove(ProfileScopedKey.of(it))
        }

        payload.decodeSyncBoolean(enabledKey)?.let(::saveEnabled)
        payload.decodeSyncString(apiKey)?.let(::saveApiKey)
        payload.decodeSyncBoolean(useImdbKey)?.let(::saveUseImdb)
        payload.decodeSyncBoolean(useTmdbKey)?.let(::saveUseTmdb)
        payload.decodeSyncBoolean(useTomatoesKey)?.let(::saveUseTomatoes)
        payload.decodeSyncBoolean(useMetacriticKey)?.let(::saveUseMetacritic)
        payload.decodeSyncBoolean(useTraktKey)?.let(::saveUseTrakt)
        payload.decodeSyncBoolean(useLetterboxdKey)?.let(::saveUseLetterboxd)
        payload.decodeSyncBoolean(useAudienceKey)?.let(::saveUseAudience)
    }

    private fun loadBoolean(key: String): Boolean? =
        preferences.getBoolean(ProfileScopedKey.of(key))

    private fun saveBoolean(key: String, enabled: Boolean) {
        preferences.putBoolean(ProfileScopedKey.of(key), enabled)
    }
}
