package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
