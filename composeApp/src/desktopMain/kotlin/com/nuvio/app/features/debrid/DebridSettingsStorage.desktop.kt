package com.nuvio.app.features.debrid

import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.desktop.DesktopAccountScopedKey
import com.nuvio.app.core.desktop.DesktopPreferences
import com.nuvio.app.core.desktop.DesktopSecureStore
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncInt
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncInt
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object DebridSettingsStorage {
    private const val enabledKey = "debrid_enabled"
    private const val cloudLibraryEnabledKey = "debrid_cloud_library_enabled"
    private const val preferredResolverProviderIdKey = "debrid_preferred_resolver_provider_id"
    private const val torboxApiKeyKey = "debrid_torbox_api_key"
    private const val realDebridApiKeyKey = "debrid_real_debrid_api_key"
    private const val instantPlaybackPreparationLimitKey = "debrid_instant_playback_preparation_limit"
    private const val streamMaxResultsKey = "debrid_stream_max_results"
    private const val streamSortModeKey = "debrid_stream_sort_mode"
    private const val streamMinimumQualityKey = "debrid_stream_minimum_quality"
    private const val streamDolbyVisionFilterKey = "debrid_stream_dolby_vision_filter"
    private const val streamHdrFilterKey = "debrid_stream_hdr_filter"
    private const val streamCodecFilterKey = "debrid_stream_codec_filter"
    private const val streamPreferencesKey = "debrid_stream_preferences"
    private const val streamNameTemplateKey = "debrid_stream_name_template"
    private const val streamDescriptionTemplateKey = "debrid_stream_description_template"
    private const val scopeSignatureKey = "debrid_scope_signature"
    private val preferences = DesktopPreferences("nuvio_debrid_settings")

    actual fun loadEnabled(): Boolean? = loadBoolean(enabledKey)
    actual fun saveEnabled(enabled: Boolean) = saveBoolean(enabledKey, enabled)

    actual fun loadCloudLibraryEnabled(): Boolean? = loadBoolean(cloudLibraryEnabledKey)
    actual fun saveCloudLibraryEnabled(enabled: Boolean) = saveBoolean(cloudLibraryEnabledKey, enabled)

    actual fun loadPreferredResolverProviderId(): String? = loadString(preferredResolverProviderIdKey)
    actual fun savePreferredResolverProviderId(providerId: String) = saveString(preferredResolverProviderIdKey, providerId)

    actual fun loadProviderApiKey(providerId: String): String? {
        val key = providerApiKeyKey(providerId)
        return DesktopSecureStore.readString(scopedKey(key))
            ?: preferences.getString(scopedKey(key))
    }

    actual fun saveProviderApiKey(providerId: String, apiKey: String) {
        val key = providerApiKeyKey(providerId)
        val scopedKey = scopedKey(key)
        if (apiKey.isBlank()) {
            DesktopSecureStore.remove(scopedKey)
        } else {
            DesktopSecureStore.writeString(scopedKey, apiKey)
        }
        preferences.remove(scopedKey)
    }

    actual fun loadTorboxApiKey(): String? = loadProviderApiKey(DebridProviders.TORBOX_ID)
    actual fun saveTorboxApiKey(apiKey: String) = saveProviderApiKey(DebridProviders.TORBOX_ID, apiKey)

    actual fun loadRealDebridApiKey(): String? = loadProviderApiKey(DebridProviders.REAL_DEBRID_ID)
    actual fun saveRealDebridApiKey(apiKey: String) = saveProviderApiKey(DebridProviders.REAL_DEBRID_ID, apiKey)

    actual fun loadInstantPlaybackPreparationLimit(): Int? = loadInt(instantPlaybackPreparationLimitKey)
    actual fun saveInstantPlaybackPreparationLimit(limit: Int) = saveInt(instantPlaybackPreparationLimitKey, limit)

    actual fun loadStreamMaxResults(): Int? = loadInt(streamMaxResultsKey)
    actual fun saveStreamMaxResults(maxResults: Int) = saveInt(streamMaxResultsKey, maxResults)

    actual fun loadStreamSortMode(): String? = loadString(streamSortModeKey)
    actual fun saveStreamSortMode(mode: String) = saveString(streamSortModeKey, mode)

    actual fun loadStreamMinimumQuality(): String? = loadString(streamMinimumQualityKey)
    actual fun saveStreamMinimumQuality(quality: String) = saveString(streamMinimumQualityKey, quality)

    actual fun loadStreamDolbyVisionFilter(): String? = loadString(streamDolbyVisionFilterKey)
    actual fun saveStreamDolbyVisionFilter(filter: String) = saveString(streamDolbyVisionFilterKey, filter)

    actual fun loadStreamHdrFilter(): String? = loadString(streamHdrFilterKey)
    actual fun saveStreamHdrFilter(filter: String) = saveString(streamHdrFilterKey, filter)

    actual fun loadStreamCodecFilter(): String? = loadString(streamCodecFilterKey)
    actual fun saveStreamCodecFilter(filter: String) = saveString(streamCodecFilterKey, filter)

    actual fun loadStreamPreferences(): String? = loadString(streamPreferencesKey)
    actual fun saveStreamPreferences(preferences: String) = saveString(streamPreferencesKey, preferences)

    actual fun loadStreamNameTemplate(): String? = loadString(streamNameTemplateKey)
    actual fun saveStreamNameTemplate(template: String) = saveString(streamNameTemplateKey, template)

    actual fun loadStreamDescriptionTemplate(): String? = loadString(streamDescriptionTemplateKey)
    actual fun saveStreamDescriptionTemplate(template: String) = saveString(streamDescriptionTemplateKey, template)

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(enabledKey, encodeSyncBoolean(it)) }
        loadCloudLibraryEnabled()?.let { put(cloudLibraryEnabledKey, encodeSyncBoolean(it)) }
        loadPreferredResolverProviderId()?.let { put(preferredResolverProviderIdKey, encodeSyncString(it)) }
        DebridProviders.all().forEach { provider ->
            loadProviderApiKey(provider.id)?.let {
                put(providerApiKeyKey(provider.id), encodeSyncString(it))
            }
        }
        loadInstantPlaybackPreparationLimit()?.let { put(instantPlaybackPreparationLimitKey, encodeSyncInt(it)) }
        loadStreamMaxResults()?.let { put(streamMaxResultsKey, encodeSyncInt(it)) }
        loadStreamSortMode()?.let { put(streamSortModeKey, encodeSyncString(it)) }
        loadStreamMinimumQuality()?.let { put(streamMinimumQualityKey, encodeSyncString(it)) }
        loadStreamDolbyVisionFilter()?.let { put(streamDolbyVisionFilterKey, encodeSyncString(it)) }
        loadStreamHdrFilter()?.let { put(streamHdrFilterKey, encodeSyncString(it)) }
        loadStreamCodecFilter()?.let { put(streamCodecFilterKey, encodeSyncString(it)) }
        loadStreamPreferences()?.let { put(streamPreferencesKey, encodeSyncString(it)) }
        loadStreamNameTemplate()?.let { put(streamNameTemplateKey, encodeSyncString(it)) }
        loadStreamDescriptionTemplate()?.let { put(streamDescriptionTemplateKey, encodeSyncString(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        syncKeys().forEach {
            preferences.remove(scopedKey(it))
            DesktopSecureStore.remove(scopedKey(it))
        }

        payload.decodeSyncBoolean(enabledKey)?.let(::saveEnabled)
        payload.decodeSyncBoolean(cloudLibraryEnabledKey)?.let(::saveCloudLibraryEnabled)
        payload.decodeSyncString(preferredResolverProviderIdKey)?.let(::savePreferredResolverProviderId)
        DebridProviders.all().forEach { provider ->
            payload.decodeSyncString(providerApiKeyKey(provider.id))?.let { apiKey ->
                saveProviderApiKey(provider.id, apiKey)
            }
        }
        payload.decodeSyncInt(instantPlaybackPreparationLimitKey)?.let(::saveInstantPlaybackPreparationLimit)
        payload.decodeSyncInt(streamMaxResultsKey)?.let(::saveStreamMaxResults)
        payload.decodeSyncString(streamSortModeKey)?.let(::saveStreamSortMode)
        payload.decodeSyncString(streamMinimumQualityKey)?.let(::saveStreamMinimumQuality)
        payload.decodeSyncString(streamDolbyVisionFilterKey)?.let(::saveStreamDolbyVisionFilter)
        payload.decodeSyncString(streamHdrFilterKey)?.let(::saveStreamHdrFilter)
        payload.decodeSyncString(streamCodecFilterKey)?.let(::saveStreamCodecFilter)
        payload.decodeSyncString(streamPreferencesKey)?.let(::saveStreamPreferences)
        payload.decodeSyncString(streamNameTemplateKey)?.let(::saveStreamNameTemplate)
        payload.decodeSyncString(streamDescriptionTemplateKey)?.let(::saveStreamDescriptionTemplate)
    }

    actual fun clearLocalState() {
        syncKeys().forEach {
            preferences.remove(scopedKey(it))
            DesktopSecureStore.remove(scopedKey(it))
        }
    }

    actual fun currentScopeSignature(): String = scopedKey(scopeSignatureKey)

    private fun syncKeys(): List<String> =
        listOf(
            enabledKey,
            cloudLibraryEnabledKey,
            preferredResolverProviderIdKey,
            instantPlaybackPreparationLimitKey,
            streamMaxResultsKey,
            streamSortModeKey,
            streamMinimumQualityKey,
            streamDolbyVisionFilterKey,
            streamHdrFilterKey,
            streamCodecFilterKey,
            streamPreferencesKey,
            streamNameTemplateKey,
            streamDescriptionTemplateKey,
        ) + DebridProviders.all().map { providerApiKeyKey(it.id) }

    private fun loadBoolean(key: String): Boolean? =
        preferences.getBoolean(scopedKey(key))

    private fun saveBoolean(key: String, enabled: Boolean) {
        preferences.putBoolean(scopedKey(key), enabled)
    }

    private fun loadInt(key: String): Int? =
        preferences.getInt(scopedKey(key))

    private fun saveInt(key: String, value: Int) {
        preferences.putInt(scopedKey(key), value)
    }

    private fun loadString(key: String): String? =
        preferences.getString(scopedKey(key))

    private fun saveString(key: String, value: String) {
        preferences.putString(scopedKey(key), value)
    }

    private fun scopedKey(baseKey: String): String =
        DesktopAccountScopedKey.of(
            baseKey = baseKey,
            accountId = currentAccountId(),
            profileId = ProfileRepository.activeProfileId,
        )

    private fun currentAccountId(): String =
        (AuthRepository.state.value as? AuthState.Authenticated)
            ?.userId
            .orEmpty()

    private fun providerApiKeyKey(providerId: String): String {
        val normalized = DebridProviders.byId(providerId)?.id
            ?: providerId.trim().lowercase().replace(Regex("[^a-z0-9_]+"), "_")
        return when (normalized) {
            DebridProviders.TORBOX_ID -> torboxApiKeyKey
            DebridProviders.REAL_DEBRID_ID -> realDebridApiKeyKey
            else -> "debrid_${normalized}_api_key"
        }
    }
}
