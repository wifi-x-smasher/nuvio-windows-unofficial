package com.nuvio.app.features.player.desktop.nativempv

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

internal data class NativeMpvLaunchConfig(
    val mpvExecutable: Path,
    val ipcPath: String,
    val windowId: ULong? = null,
    val options: List<String> = emptyList(),
)

internal object NativeMpvProcess {
    private val ipcCounter = AtomicInteger(0)

    fun createIpcPath(
        processId: Long = ProcessHandle.current().pid(),
        sequence: Int = ipcCounter.incrementAndGet(),
    ): String = "\\\\.\\pipe\\nuvio-mpv-$processId-$sequence"

    fun buildLaunchCommand(config: NativeMpvLaunchConfig): List<String> =
        buildList {
            add(config.mpvExecutable.toAbsolutePath().toString())
            add("--idle=yes")
            add("--force-window=${if (config.windowId == null) "no" else "yes"}")
            add("--input-ipc-server=${config.ipcPath}")
            add("--no-config")
            add("--terminal=no")
            add("--msg-level=all=warn")
            config.windowId?.let { add("--wid=$it") }
            addAll(config.options)
        }

    fun start(config: NativeMpvLaunchConfig): Process =
        ProcessBuilder(buildLaunchCommand(config))
            .redirectErrorStream(true)
            .start()
}
