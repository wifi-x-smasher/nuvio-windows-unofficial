package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadTransferStatsTest {
    @Test
    fun calculatesBytesPerSecondAndEtaFromProgressSamples() {
        val stats = DownloadTransferStats.calculate(
            previous = DownloadProgressSample(downloadedBytes = 1_000L, epochMs = 1_000L),
            currentDownloadedBytes = 5_000L,
            currentEpochMs = 3_000L,
            totalBytes = 13_000L,
        )

        assertEquals(2_000L, stats.bytesPerSecond)
        assertEquals(4L, stats.etaSeconds)
    }

    @Test
    fun returnsEmptyStatsWhenProgressDoesNotAdvance() {
        val stats = DownloadTransferStats.calculate(
            previous = DownloadProgressSample(downloadedBytes = 5_000L, epochMs = 1_000L),
            currentDownloadedBytes = 5_000L,
            currentEpochMs = 3_000L,
            totalBytes = 13_000L,
        )

        assertNull(stats.bytesPerSecond)
        assertNull(stats.etaSeconds)
    }

    @Test
    fun returnsEmptyEtaWhenTotalSizeIsUnknown() {
        val stats = DownloadTransferStats.calculate(
            previous = DownloadProgressSample(downloadedBytes = 1_000L, epochMs = 1_000L),
            currentDownloadedBytes = 5_000L,
            currentEpochMs = 3_000L,
            totalBytes = null,
        )

        assertEquals(2_000L, stats.bytesPerSecond)
        assertNull(stats.etaSeconds)
    }
}
