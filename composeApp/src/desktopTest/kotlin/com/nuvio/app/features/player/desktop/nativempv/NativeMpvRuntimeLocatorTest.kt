package com.nuvio.app.features.player.desktop.nativempv

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeMpvRuntimeLocatorTest {
    @Test
    fun resolvesFlatMpvExecutableFromComposeResourcesDirectory() {
        val resourcesDir = Files.createTempDirectory("nuvio-native-mpv-resources").toFile()
        val executable = resourcesDir.resolve("mpv.exe").apply { writeText("placeholder") }
        val previousResourcesDir = System.getProperty("compose.application.resources.dir")

        try {
            System.setProperty("compose.application.resources.dir", resourcesDir.absolutePath)

            val resolved = NativeMpvRuntimeLocator.resolve()

            assertTrue(resolved.available)
            assertEquals(executable.absoluteFile.toPath(), resolved.executable)
        } finally {
            if (previousResourcesDir == null) {
                System.clearProperty("compose.application.resources.dir")
            } else {
                System.setProperty("compose.application.resources.dir", previousResourcesDir)
            }
            executable.delete()
            resourcesDir.delete()
        }
    }
}
