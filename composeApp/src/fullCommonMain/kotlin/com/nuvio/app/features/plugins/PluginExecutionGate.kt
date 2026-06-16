package com.nuvio.app.features.plugins

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Bounds how many plugin scrapers may execute in the QuickJS runtime at once.
 *
 * This was previously a single [kotlinx.coroutines.sync.Mutex], i.e. strict serialization. That
 * starved scrapers: the stream screen (and in-player Sources flow) launch every scraper at once,
 * each under a 20s timeout, so while one plugin held the lock the rest counted down and were
 * cancelled before they ever ran. Official mobile/TV and the CreepsoOff desktop fork run the same
 * QuickJS library concurrently with no such gate.
 *
 * A [Semaphore] restores parallelism while still bounding the number of concurrent native QuickJS
 * contexts. Keep this conservative on Windows: high native QuickJS concurrency has produced JVM
 * access-violation crashes inside executePendingJob during large stream searches. Tune only with
 * crash-log evidence and broad Windows testing.
 */
internal object PluginExecutionGate {
    const val MAX_CONCURRENT_PLUGINS = 4

    private val semaphore = Semaphore(MAX_CONCURRENT_PLUGINS)

    suspend fun <T> runQuickJs(block: suspend () -> T): T =
        semaphore.withPermit {
            block()
        }
}
