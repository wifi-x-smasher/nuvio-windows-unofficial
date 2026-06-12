package com.nuvio.app.features.downloads

internal const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 2

internal object DownloadQueuePolicy {
    fun nextQueuedIds(
        items: List<DownloadItem>,
        activeCount: Int,
        maxConcurrent: Int = DEFAULT_MAX_CONCURRENT_DOWNLOADS,
    ): List<String> {
        val availableSlots = (maxConcurrent - activeCount).coerceAtLeast(0)
        if (availableSlots <= 0) return emptyList()

        return items
            .asSequence()
            .filter { it.status == DownloadStatus.Queued }
            .sortedWith(
                compareBy<DownloadItem> { it.createdAtEpochMs }
                    .thenBy { it.id },
            )
            .take(availableSlots)
            .map { it.id }
            .toList()
    }
}
