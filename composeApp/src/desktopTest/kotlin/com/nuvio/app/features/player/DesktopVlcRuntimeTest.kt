package com.nuvio.app.features.player

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopVlcRuntimeTest {
    @Test
    fun ignoresOnly32BitVlcInstallForInternalPlayback() {
        val x86Vlc = Path.of("C:\\Program Files (x86)", "VideoLAN", "VLC")

        val runtime = DesktopVlcRuntime.compatibleRuntimeDirectory(
            env = mapOf(
                "ProgramFiles(x86)" to "C:\\Program Files (x86)",
                "PROGRAMFILES(X86)" to "C:\\Program Files (x86)",
            ),
            property = x86Vlc.toString(),
            exists = { it == x86Vlc },
            hasLibrary = { it == x86Vlc },
        )

        assertNull(runtime)
    }

    @Test
    fun accepts64BitVlcInstallForInternalPlayback() {
        val x64Vlc = Path.of("C:\\Program Files", "VideoLAN", "VLC")

        val runtime = DesktopVlcRuntime.compatibleRuntimeDirectory(
            env = mapOf(
                "ProgramFiles" to "C:\\Program Files",
                "PROGRAMFILES" to "C:\\Program Files",
            ),
            property = null,
            exists = { it == x64Vlc },
            hasLibrary = { it == x64Vlc },
        )

        assertEquals(x64Vlc, runtime)
    }

    @Test
    fun prefersBundledVlcRuntimeFromComposeResources() {
        val bundledVlc = Path.of("C:\\Program Files", "Nuvio", "app", "resources", "vlc")
        val systemVlc = Path.of("C:\\Program Files", "VideoLAN", "VLC")

        val runtime = DesktopVlcRuntime.compatibleRuntimeDirectory(
            env = mapOf("ProgramFiles" to "C:\\Program Files"),
            property = null,
            appResourcesDir = Path.of("C:\\Program Files", "Nuvio", "app", "resources").toString(),
            exists = { it == bundledVlc || it == systemVlc },
            hasLibrary = { it == bundledVlc || it == systemVlc },
        )

        assertEquals(bundledVlc, runtime)
    }

    @Test
    fun findsBundledVlcBesidePackagedExecutable() {
        val bundledVlc = Path.of("C:\\Program Files", "Nuvio", "app", "vlc")

        val runtime = DesktopVlcRuntime.compatibleRuntimeDirectory(
            env = emptyMap(),
            property = null,
            executablePath = Path.of("C:\\Program Files", "Nuvio", "Nuvio.exe"),
            exists = { it == bundledVlc },
            hasLibrary = { it == bundledVlc },
        )

        assertEquals(bundledVlc, runtime)
    }
}
