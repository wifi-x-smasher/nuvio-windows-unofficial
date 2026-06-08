package com.nuvio.app.features.watchprogress

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchProgressRepositoryMergeTest {
    @Test
    fun mergeTraktEntriesWithLocalFallback_uses_local_cache_before_remote_load_completes() {
        val cachedLocalEntry = progressEntry(
            videoId = "series:show:1:1",
            source = WatchProgressSourceTraktHistory,
            percent = 100f,
            updatedAt = 1_000L,
            completed = true,
        )

        val merged = WatchProgressRepository.mergeTraktEntriesWithLocalFallback(
            traktEntries = emptyList(),
            localEntries = listOf(cachedLocalEntry),
            hasLoadedRemoteProgress = false,
        )

        assertEquals(listOf(cachedLocalEntry), merged)
    }

    @Test
    fun mergeTraktEntriesWithLocalFallback_keeps_newer_local_progress_when_remote_regresses() {
        val remoteEntry = progressEntry(
            videoId = "series:show:1:8",
            source = WatchProgressSourceTraktPlayback,
            percent = 10f,
            updatedAt = 1_000L,
            completed = false,
        )
        val localEntry = progressEntry(
            videoId = "series:show:1:8",
            source = WatchProgressSourceLocal,
            percent = 86f,
            updatedAt = 2_000L,
            completed = false,
        )

        val merged = WatchProgressRepository.mergeTraktEntriesWithLocalFallback(
            traktEntries = listOf(remoteEntry),
            localEntries = listOf(localEntry),
            hasLoadedRemoteProgress = true,
        )

        assertEquals(listOf(localEntry), merged)
    }

    @Test
    fun mergeTraktEntriesWithLocalFallback_drops_stale_trakt_cache_missing_from_loaded_remote() {
        val remoteEntry = progressEntry(
            videoId = "series:show:1:9",
            source = WatchProgressSourceTraktHistory,
            percent = 100f,
            updatedAt = 2_000L,
            completed = true,
        )
        val staleCachedTraktEntry = progressEntry(
            videoId = "series:show:1:8",
            source = WatchProgressSourceTraktHistory,
            percent = 100f,
            updatedAt = 1_000L,
            completed = true,
        )

        val merged = WatchProgressRepository.mergeTraktEntriesWithLocalFallback(
            traktEntries = listOf(remoteEntry),
            localEntries = listOf(staleCachedTraktEntry),
            hasLoadedRemoteProgress = true,
        )

        assertEquals(listOf(remoteEntry), merged)
    }

    private fun progressEntry(
        videoId: String,
        source: String,
        percent: Float,
        updatedAt: Long,
        completed: Boolean,
    ): WatchProgressEntry = WatchProgressEntry(
        contentType = "series",
        parentMetaId = "show",
        parentMetaType = "series",
        videoId = videoId,
        title = "Show",
        seasonNumber = 1,
        episodeNumber = 1,
        lastPositionMs = if (completed) 1_000L else 500L,
        durationMs = 1_000L,
        lastUpdatedEpochMs = updatedAt,
        isCompleted = completed,
        progressPercent = percent,
        source = source,
    )
}
