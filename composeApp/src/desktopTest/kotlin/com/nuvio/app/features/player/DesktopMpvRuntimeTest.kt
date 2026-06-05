package com.nuvio.app.features.player

import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeBootstrapResult
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeResolution
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Path

class DesktopMpvRuntimeTest {
    @Test
    fun bundledMpvRuntimeIsPreferredFromComposeResources() {
        val bundled = Path.of("C:\\Program Files\\Nuvio\\app\\resources\\mpv\\mpv.exe")
        val system = Path.of("C:\\Program Files\\mpv\\mpv.exe")

        val resolved = DesktopMpvRuntime.executablePath(
            env = mapOf("ProgramFiles" to "C:\\Program Files"),
            appResourcesDir = "C:\\Program Files\\Nuvio\\app\\resources",
            executablePath = Path.of("C:\\Program Files\\Nuvio\\Nuvio.exe"),
            exists = { it == bundled || it == system },
        )

        assertEquals(bundled, resolved)
    }

    @Test
    fun systemMpvRuntimeIsUsedWhenBundledRuntimeIsMissing() {
        val system = Path.of("C:\\Program Files\\mpv\\mpv.exe")

        val resolved = DesktopMpvRuntime.executablePath(
            env = mapOf("ProgramFiles" to "C:\\Program Files"),
            appResourcesDir = "C:\\Program Files\\Nuvio\\app\\resources",
            executablePath = Path.of("C:\\Program Files\\Nuvio\\Nuvio.exe"),
            exists = { it == system },
        )

        assertEquals(system, resolved)
    }

    @Test
    fun nativeRuntimeIsUnavailableWhenOnlyMpvExecutableExists() {
        val runtimeDir = Files.createTempDirectory("nuvio-mpv-runtime-test").toFile()
        runtimeDir.resolve("mpv.exe").writeText("placeholder")
        var bootstrapCalled = false

        val available = DesktopMpvRuntime.isNativeRuntimeAvailable(
            resolve = {
                MpvRuntimeResolution(
                    directory = runtimeDir,
                    checkedDirectories = listOf("test=${runtimeDir.absolutePath}"),
                    diagnostics = "test runtime with mpv.exe only",
                )
            },
            bootstrap = {
                bootstrapCalled = true
                MpvRuntimeBootstrapResult(success = true, diagnostics = "should not be called")
            },
        )

        assertFalse(available)
        assertFalse(bootstrapCalled)
    }

    @Test
    fun nativeRuntimeAvailabilityUsesBootstrapWhenMediampDllExists() {
        val runtimeDir = Files.createTempDirectory("nuvio-mediamp-runtime-test").toFile()
        runtimeDir.resolve("mediampv.dll").writeText("placeholder")
        runtimeDir.resolve("libmpv-2.dll").writeText("placeholder")
        var bootstrapDirectory: String? = null

        val available = DesktopMpvRuntime.isNativeRuntimeAvailable(
            resolve = {
                MpvRuntimeResolution(
                    directory = runtimeDir,
                    checkedDirectories = listOf("test=${runtimeDir.absolutePath}"),
                    diagnostics = "test runtime with mediampv.dll",
                )
            },
            bootstrap = { runtime ->
                bootstrapDirectory = runtime.directory?.absolutePath
                MpvRuntimeBootstrapResult(success = true, diagnostics = "bootstrapped")
            },
        )

        assertTrue(available)
        assertEquals(runtimeDir.absolutePath, bootstrapDirectory)
    }
}
