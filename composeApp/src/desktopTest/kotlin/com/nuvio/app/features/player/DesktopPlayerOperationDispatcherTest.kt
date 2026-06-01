package com.nuvio.app.features.player

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPlayerOperationDispatcherTest {
    @Test
    fun dispatchReturnsWhilePlayerOperationContinuesInBackground() {
        val dispatcher = ExecutorDesktopPlayerOperationDispatcher(threadName = "NuvioTestPlayer")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = AtomicBoolean(false)

        val elapsedMs = measureTimeMillis {
            dispatcher.dispatch {
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
                completed.set(true)
            }
        }

        try {
            assertTrue(elapsedMs < 200, "dispatch should not wait for the player operation to finish")
            assertTrue(started.await(1, TimeUnit.SECONDS), "player operation should run on a background thread")
            assertFalse(completed.get(), "test operation should still be waiting after dispatch returns")
        } finally {
            release.countDown()
            dispatcher.close()
        }
    }

    @Test
    fun closePreventsLaterPlayerOperations() {
        val dispatcher = ExecutorDesktopPlayerOperationDispatcher(threadName = "NuvioClosedPlayer")
        val ran = AtomicBoolean(false)

        dispatcher.close()
        dispatcher.dispatch { ran.set(true) }
        Thread.sleep(100)

        assertFalse(ran.get())
    }
}
