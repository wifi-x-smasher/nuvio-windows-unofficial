package com.nuvio.app.features.updater

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopAppUpdaterPlatformTest {
    @Test
    fun `windows updater launches verified MSI without PowerShell`() {
        val installer = Path.of("C:\\Users\\Tester\\AppData\\Local\\Temp\\Nuvio\\updates\\Nuvio-Windows-0.2.7.msi")

        val command = AppUpdaterPlatform.windowsInstallerCommand(installer)
        val joined = command.joinToString(" ")

        assertEquals(listOf("msiexec.exe", "/i", installer.toString()), command)
        assertFalse(joined.contains("powershell", ignoreCase = true))
        assertFalse(joined.contains("ExecutionPolicy", ignoreCase = true))
        assertFalse(joined.contains("Bypass", ignoreCase = true))
        assertFalse(joined.contains("Start-Process", ignoreCase = true))
        assertFalse(joined.contains("RunAs", ignoreCase = true))
    }
}
