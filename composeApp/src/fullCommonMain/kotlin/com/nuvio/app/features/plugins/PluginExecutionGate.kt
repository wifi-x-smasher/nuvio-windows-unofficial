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
 * contexts. Plugin execution runs on [kotlinx.coroutines.Dispatchers.IO] (see PluginRuntime), so the
 * blocking fetch bridge holds IO-pool threads instead of starving the small Default pool — which is
 * what lets us run more lanes safely. With ~250 providers, 4 lanes meant fast scrapers waited
 * minutes behind slow ones; 16 keeps latency low without going fully unbounded. Tune with evidence.
 */
internal object PluginExecutionGate {
    const val MAX_CONCURRENT_PLUGINS = 16

    private val semaphore = Semaphore(MAX_CONCURRENT_PLUGINS)

    suspend fun <T> runQuickJs(block: suspend () -> T): T =
        semaphore.withPermit {
            block()
        }
}
