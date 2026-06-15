package com.nuvio.app.features.player.desktop.nativempv

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeMpvProcessTest {
    @Test
    fun createsPerSessionWindowsNamedPipePath() {
        val path = NativeMpvProcess.createIpcPath(processId = 1234, sequence = 7)

        assertEquals("\\\\.\\pipe\\nuvio-mpv-1234-7", path)
    }

    @Test
    fun buildsHeadlessLaunchCommandWithoutWindowId() {
        val command = NativeMpvProcess.buildLaunchCommand(
            NativeMpvLaunchConfig(
                mpvExecutable = Path.of("C:\\Program Files\\Nuvio\\mpv\\mpv.exe"),
                ipcPath = "\\\\.\\pipe\\nuvio-mpv-1234-7",
            ),
        )

        assertTrue(command.any { it.endsWith("mpv.exe") })
        assertTrue("--idle=yes" in command)
        assertTrue("--force-window=no" in command)
        assertTrue("--input-ipc-server=\\\\.\\pipe\\nuvio-mpv-1234-7" in command)
        assertTrue(command.none { it.startsWith("--wid=") })
    }

    @Test
    fun buildsEmbeddedLaunchCommandWithUnsignedWindowId() {
        val command = NativeMpvProcess.buildLaunchCommand(
            NativeMpvLaunchConfig(
                mpvExecutable = Path.of("mpv.exe"),
                ipcPath = "\\\\.\\pipe\\nuvio-mpv-1234-7",
                windowId = ULong.MAX_VALUE,
                options = listOf("--vo=gpu-next", "--gpu-api=d3d11"),
            ),
        )

        assertTrue("--force-window=yes" in command)
        assertTrue("--wid=18446744073709551615" in command)
        assertTrue("--vo=gpu-next" in command)
        assertTrue("--gpu-api=d3d11" in command)
    }
}
