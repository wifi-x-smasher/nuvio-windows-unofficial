package com.nuvio.app.features.plugins

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class PluginExecutionGateTest {
    @Test
    fun quickJsWorkRunsConcurrentlyUpToTheConfiguredBound() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val permits = PluginExecutionGate.MAX_CONCURRENT_PLUGINS

        coroutineScope {
            List(permits * 3) {
                async {
                    PluginExecutionGate.runQuickJs {
                        val current = active.incrementAndGet()
                        maxActive.updateAndGet { previous -> maxOf(previous, current) }
                        delay(50)
                        active.decrementAndGet()
                    }
                }
            }.awaitAll()
        }

        assertEquals(0, active.get(), "All permits must be released after execution")
        assertTrue(
            maxActive.get() > 1,
            "Plugin execution must run concurrently, not serialized (maxActive=${maxActive.get()})",
        )
        assertTrue(
            maxActive.get() <= permits,
            "Plugin concurrency must stay within the bound $permits (maxActive=${maxActive.get()})",
        )
    }
}
