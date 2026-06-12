package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadQueuePolicyTest {
    @Test
    fun selectsQueuedDownloadsOldestFirstWithinAvailableSlots() {
        val items = listOf(
            downloadItem(id = "newer", status = DownloadStatus.Queued, createdAtEpochMs = 300),
            downloadItem(id = "active", status = DownloadStatus.Downloading, createdAtEpochMs = 100),
            downloadItem(id = "oldest", status = DownloadStatus.Queued, createdAtEpochMs = 100),
            downloadItem(id = "middle", status = DownloadStatus.Queued, createdAtEpochMs = 200),
        )

        val selected = DownloadQueuePolicy.nextQueuedIds(
            items = items,
            activeCount = 1,
            maxConcurrent = 3,
        )

        assertEquals(listOf("oldest", "middle"), selected)
    }

    @Test
    fun returnsNoQueuedDownloadsWhenConcurrencyLimitIsAlreadyFull() {
        val items = listOf(
            downloadItem(id = "queued", status = DownloadStatus.Queued, createdAtEpochMs = 100),
        )

        val selected = DownloadQueuePolicy.nextQueuedIds(
            items = items,
            activeCount = 2,
            maxConcurrent = 2,
        )

        assertEquals(emptyList(), selected)
    }

    private fun downloadItem(
        id: String,
        status: DownloadStatus,
        createdAtEpochMs: Long,
    ): DownloadItem = DownloadItem(
        id = id,
        contentType = "movie",
        parentMetaId = "tt123",
        parentMetaType = "movie",
        videoId = "tt123",
        title = "Movie",
        streamTitle = "Stream",
        providerName = "Provider",
        sourceUrl = "https://example.com/video.mp4",
        fileName = "$id.mp4",
        status = status,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = createdAtEpochMs,
    )
}
