package com.nuvio.app.features.downloads

import kotlin.math.ceil

internal data class DownloadProgressSample(
    val downloadedBytes: Long,
    val epochMs: Long,
)

internal data class DownloadProgressStats(
    val bytesPerSecond: Long? = null,
    val etaSeconds: Long? = null,
)

internal object DownloadTransferStats {
    fun calculate(
        previous: DownloadProgressSample?,
        currentDownloadedBytes: Long,
        currentEpochMs: Long,
        totalBytes: Long?,
    ): DownloadProgressStats {
        previous ?: return DownloadProgressStats()

        val elapsedMs = currentEpochMs - previous.epochMs
        val byteDelta = currentDownloadedBytes - previous.downloadedBytes
        if (elapsedMs <= 0L || byteDelta <= 0L) return DownloadProgressStats()

        val bytesPerSecond = ((byteDelta * 1_000.0) / elapsedMs.toDouble())
            .toLong()
            .coerceAtLeast(1L)
        val remainingBytes = totalBytes
            ?.takeIf { it > currentDownloadedBytes }
            ?.minus(currentDownloadedBytes)
        val etaSeconds = remainingBytes
            ?.let { remaining -> ceil(remaining.toDouble() / bytesPerSecond.toDouble()).toLong() }

        return DownloadProgressStats(
            bytesPerSecond = bytesPerSecond,
            etaSeconds = etaSeconds,
        )
    }
}
