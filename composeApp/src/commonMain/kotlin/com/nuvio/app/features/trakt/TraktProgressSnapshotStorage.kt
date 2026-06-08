package com.nuvio.app.features.trakt

/**
 * Persists the last resolved Trakt watch-progress snapshot so that, on a cold launch, already
 * watched / in-progress items can be shown immediately instead of flickering as "unwatched" until
 * the remote history finishes loading (issue #12). Profile-scoped via [ProfileScopedKey].
 */
internal expect object TraktProgressSnapshotStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
