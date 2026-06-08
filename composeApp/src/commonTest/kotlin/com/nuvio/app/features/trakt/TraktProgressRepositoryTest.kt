package com.nuvio.app.features.trakt

import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TraktProgressRepositoryTest {
    private fun entry(
        videoId: String,
        completed: Boolean,
        updatedAt: Long,
    ): WatchProgressEntry = WatchProgressEntry(
        contentType = "series",
        parentMetaId = "show",
        parentMetaType = "series",
        videoId = videoId,
        title = "Show",
        lastPositionMs = if (completed) 1_000L else 200L,
        durationMs = 1_000L,
        lastUpdatedEpochMs = updatedAt,
        isCompleted = completed,
        progressPercent = if (completed) 100f else 20f,
    )

    @Test
    fun retains_completed_entries_that_fell_out_of_a_smaller_refetch() {
        val previouslyWatched = entry("series:show:1:8", completed = true, updatedAt = 1_000L)
        val fresh = entry("series:show:1:9", completed = true, updatedAt = 2_000L)

        val merged = TraktProgressRepository.mergeRetainingWatchedEntries(
            previous = listOf(previouslyWatched),
            fresh = listOf(fresh),
        )

        assertEquals(
            setOf("series:show:1:8", "series:show:1:9"),
            merged.mapTo(mutableSetOf()) { it.videoId },
        )
    }

    @Test
    fun does_not_retain_in_progress_entries_missing_from_fresh_fetch() {
        val previouslyInProgress = entry("series:show:1:8", completed = false, updatedAt = 1_000L)
        val fresh = entry("series:show:1:9", completed = true, updatedAt = 2_000L)

        val merged = TraktProgressRepository.mergeRetainingWatchedEntries(
            previous = listOf(previouslyInProgress),
            fresh = listOf(fresh),
        )

        assertEquals(setOf("series:show:1:9"), merged.mapTo(mutableSetOf()) { it.videoId })
    }

    @Test
    fun fresh_entry_takes_precedence_over_previous_with_same_id() {
        val previous = entry("series:show:1:8", completed = true, updatedAt = 1_000L)
        val fresh = entry("series:show:1:8", completed = false, updatedAt = 5_000L)

        val merged = TraktProgressRepository.mergeRetainingWatchedEntries(
            previous = listOf(previous),
            fresh = listOf(fresh),
        )

        assertEquals(1, merged.size)
        assertEquals(5_000L, merged.single().lastUpdatedEpochMs)
        assertFalse(merged.single().isCompleted)
    }
}
