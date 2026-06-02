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
    fun quickJsWorkRunsSeriallyEvenWhenScrapersStartConcurrently() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)

        coroutineScope {
            List(12) {
                async {
                    PluginExecutionGate.runQuickJs {
                        val current = active.incrementAndGet()
                        maxActive.updateAndGet { previous -> maxOf(previous, current) }
                        delay(5)
                        active.decrementAndGet()
                    }
                }
            }.awaitAll()
        }

        assertEquals(0, active.get())
        assertTrue(maxActive.get() <= 1, "QuickJS execution must be serialized on desktop")
    }
}
