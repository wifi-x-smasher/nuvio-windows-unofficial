package com.nuvio.app.features.home

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.sync.MOBILE_SYNC_PLATFORM
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.profiles.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class SyncCatalogItem(
    @SerialName("addon_id") val addonId: String,
    val type: String,
    @SerialName("catalog_id") val catalogId: String,
    val enabled: Boolean = true,
    val order: Int = 0,
    @SerialName("custom_title") val customTitle: String = "",
    @SerialName("is_collection") val isCollection: Boolean = false,
    @SerialName("collection_id") val collectionId: String = "",
)

@Serializable
data class SyncHomeCatalogPayload(
    @SerialName("hide_unreleased_content") val hideUnreleasedContent: Boolean = false,
    @SerialName("hide_catalog_underline") val hideCatalogUnderline: Boolean = false,
    val items: List<SyncCatalogItem> = emptyList(),
)

@Serializable
private data class SupabaseHomeCatalogSettingsBlob(
    @SerialName("profile_id") val profileId: Int = 1,
    @SerialName("settings_json") val settingsJson: JsonObject = buildJsonObject { },
    @SerialName("updated_at") val updatedAt: String? = null,
)

private data class PullToken(
    val userId: String,
    val profileId: Int,
)

object HomeCatalogSettingsSyncService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("HomeCatalogSettingsSyncService")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val PUSH_DEBOUNCE_MS = 1500L

    @Volatile
    var isSyncingFromRemote: Boolean = false

    // Identifies the account+profile whose remote settings we have already pulled this session.
    // Pushes are blocked until the initial pull for the current scope completes, so a startup/login
    // local change can't overwrite the server (and other devices' synced layout) before we've read
    // what the server has.
    @Volatile
    private var completedInitialPull: PullToken? = null

    private var pushJob: Job? = null
    private var observeJob: Job? = null

    private fun currentPullToken(profileId: Int = ProfileRepository.activeProfileId): PullToken? {
        val authState = AuthRepository.state.value
        if (authState !is AuthState.Authenticated || authState.isAnonymous) return null
        return PullToken(userId = authState.userId, profileId = profileId)
    }

    private fun hasCompletedInitialPull(token: PullToken): Boolean = completedInitialPull == token

    private fun markInitialPullComplete(token: PullToken?) {
        if (token != null) completedInitialPull = token
    }

    fun startObserving() {
        if (observeJob?.isActive == true) return
        observeLocalChangesAndPush()
    }

    suspend fun pullFromServer(profileId: Int) {
        val pullToken = currentPullToken(profileId)
        runCatching {
            val params = buildJsonObject {
                put("p_profile_id", profileId)
                put("p_platform", MOBILE_SYNC_PLATFORM)
            }
            val result = SupabaseProvider.client.postgrest.rpc("sync_pull_home_catalog_settings", params)
            val blobs = result.decodeList<SupabaseHomeCatalogSettingsBlob>()
            val blob = blobs.firstOrNull()

            if (blob == null) {
                log.i { "pullFromServer — no remote home catalog settings found" }
                val localPayload = HomeCatalogSettingsRepository.exportToSyncPayload()
                if (localPayload.items.isNotEmpty()) {
                    pushToRemote(profileId)
                }
                markInitialPullComplete(pullToken)
                return
            }

            val remotePayload = runCatching {
                json.decodeFromJsonElement(SyncHomeCatalogPayload.serializer(), blob.settingsJson)
            }.getOrNull()

            if (remotePayload == null) {
                log.w { "pullFromServer — failed to parse remote home catalog settings" }
                // We reached the server but the payload is unusable; treat as "seen" so local can
                // take over instead of staying blocked forever.
                markInitialPullComplete(pullToken)
                return
            }

            if (remotePayload.items.isEmpty()) {
                log.i { "pullFromServer — remote has empty items, preserving local catalog order" }
                isSyncingFromRemote = true
                HomeCatalogSettingsRepository.applyFromRemote(remotePayload)
                isSyncingFromRemote = false
                val localPayload = HomeCatalogSettingsRepository.exportToSyncPayload()
                if (localPayload.items.isNotEmpty()) {
                    pushToRemote(profileId)
                }
                markInitialPullComplete(pullToken)
                return
            }

            isSyncingFromRemote = true
            HomeCatalogSettingsRepository.applyFromRemote(remotePayload)
            isSyncingFromRemote = false
            log.i { "pullFromServer — applied ${remotePayload.items.size} items from remote" }
            markInitialPullComplete(pullToken)
        }.onFailure { e ->
            isSyncingFromRemote = false
            // Do NOT mark the initial pull complete on failure: we never saw the server state, so
            // pushes stay blocked to avoid overwriting it. A later pull retry will unblock them.
            log.e(e) { "pullFromServer — FAILED" }
        }
    }

    fun triggerPush() {
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(500)
            if (isSyncingFromRemote) return@launch
            val token = currentPullToken() ?: return@launch
            if (!hasCompletedInitialPull(token)) {
                log.d { "triggerPush — skipped before initial home catalog pull completed" }
                return@launch
            }
            pushToRemote(token.profileId)
        }
    }

    private suspend fun pushToRemote(profileId: Int) {
        runCatching {
            val payload = HomeCatalogSettingsRepository.exportToSyncPayload()
            val jsonElement = json.encodeToJsonElement(SyncHomeCatalogPayload.serializer(), payload)

            val params = buildJsonObject {
                put("p_profile_id", profileId)
                put("p_platform", MOBILE_SYNC_PLATFORM)
                put("p_settings_json", jsonElement)
            }
            SupabaseProvider.client.postgrest.rpc("sync_push_home_catalog_settings", params)
            log.d { "pushToRemote — success" }
        }.onFailure { e ->
            log.e(e) { "pushToRemote — FAILED" }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeLocalChangesAndPush() {
        observeJob = scope.launch {
            HomeCatalogSettingsRepository.uiState
                .map { it.signature }
                .drop(1)
                .distinctUntilChanged()
                .debounce(PUSH_DEBOUNCE_MS)
                .collect {
                    if (isSyncingFromRemote) return@collect
                    val token = currentPullToken() ?: return@collect
                    if (!hasCompletedInitialPull(token)) {
                        log.d { "observeLocalChangesAndPush — skipped before initial home catalog pull completed" }
                        return@collect
                    }
                    pushToRemote(token.profileId)
                }
        }
    }
}
