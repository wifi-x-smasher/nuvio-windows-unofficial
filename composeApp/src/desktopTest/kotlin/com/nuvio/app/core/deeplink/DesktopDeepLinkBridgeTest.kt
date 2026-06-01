package com.nuvio.app.core.deeplink

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopDeepLinkBridgeTest {
    @Test
    fun `extracts nuvio callback from launcher arguments`() {
        val callback = "nuvio://auth/trakt?code=abc&state=xyz"

        assertEquals(
            callback,
            DesktopDeepLinkBridge.extractDeepLinkArg(arrayOf(callback)),
        )
    }

    @Test
    fun `ignores non nuvio launcher arguments`() {
        assertNull(
            DesktopDeepLinkBridge.extractDeepLinkArg(arrayOf("--debug", "https://trakt.tv/oauth")),
        )
    }

    @Test
    fun `registers protocol command with callback argument placeholder`() {
        val executable = Path.of("C:\\Program Files\\Nuvio\\Nuvio.exe")

        val commandWrite = DesktopDeepLinkBridge
            .windowsProtocolRegistryWrites(executable)
            .single { it.label == "open_command" }

        assertEquals("powershell.exe", commandWrite.executable)
        assertTrue(commandWrite.args.contains("-Command"))
        assertTrue(commandWrite.args.any { it.contains("Set-Item -Path") })
        assertTrue(commandWrite.args.any { it.contains("\"C:\\Program Files\\Nuvio\\Nuvio.exe\" \"%1\"") })
    }
}
