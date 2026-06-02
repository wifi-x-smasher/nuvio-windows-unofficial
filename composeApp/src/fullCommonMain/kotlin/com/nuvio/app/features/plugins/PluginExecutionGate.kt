package com.nuvio.app.features.plugins

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object PluginExecutionGate {
    private val quickJsMutex = Mutex()

    suspend fun <T> runQuickJs(block: suspend () -> T): T =
        quickJsMutex.withLock {
            block()
        }
}
