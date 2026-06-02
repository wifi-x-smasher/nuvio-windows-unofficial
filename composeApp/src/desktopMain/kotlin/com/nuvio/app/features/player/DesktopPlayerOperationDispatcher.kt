package com.nuvio.app.features.player

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

internal interface DesktopPlayerOperationDispatcher {
    fun dispatch(operation: () -> Unit)
    fun close()
}

internal class SwingDesktopPlayerOperationDispatcher : DesktopPlayerOperationDispatcher {
    private val closed = AtomicBoolean(false)

    override fun dispatch(operation: () -> Unit) {
        if (closed.get()) return
        if (SwingUtilities.isEventDispatchThread()) {
            if (!closed.get()) operation()
        } else {
            SwingUtilities.invokeLater {
                if (!closed.get()) operation()
            }
        }
    }

    override fun close() {
        closed.set(true)
    }
}

internal class ExecutorDesktopPlayerOperationDispatcher(
    threadName: String = "Nuvio-MPV-Player",
) : DesktopPlayerOperationDispatcher {
    private val closed = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, threadName).apply { isDaemon = true }
    }

    override fun dispatch(operation: () -> Unit) {
        if (closed.get()) return
        try {
            executor.execute {
                if (!closed.get()) {
                    operation()
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow()
        }
    }
}
