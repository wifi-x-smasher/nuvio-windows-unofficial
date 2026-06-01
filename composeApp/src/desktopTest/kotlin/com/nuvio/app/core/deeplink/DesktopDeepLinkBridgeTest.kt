package com.nuvio.app.core.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
