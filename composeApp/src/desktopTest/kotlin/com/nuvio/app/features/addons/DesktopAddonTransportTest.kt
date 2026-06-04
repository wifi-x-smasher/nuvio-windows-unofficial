package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAddonTransportTest {
    @Test
    fun normalizesEncodedPipesForAddonCompatibility() {
        val url = "https://example.test/stream/movie/tt123%7C1.json"

        assertEquals("https://example.test/stream/movie/tt123|1.json", normalizeDesktopAddonRequestUrl(url))
    }

    @Test
    fun onlyAllowsBodiesForMethodsThatSupportThem() {
        assertFalse(desktopAddonRequestAllowsBody("GET"))
        assertFalse(desktopAddonRequestAllowsBody("HEAD"))
        assertTrue(desktopAddonRequestAllowsBody("POST"))
        assertTrue(desktopAddonRequestAllowsBody("PUT"))
        assertTrue(desktopAddonRequestAllowsBody("PATCH"))
        assertTrue(desktopAddonRequestAllowsBody("DELETE"))
    }

    @Test
    fun stripsAcceptEncodingSoOkHttpCanTransparentlyDecode() {
        val sanitized = sanitizeDesktopAddonRequestHeaders(
            mapOf(
                "Accept-Encoding" to "gzip",
                "User-Agent" to "Nuvio",
                "Accept" to "application/json",
            ),
        )

        assertFalse(sanitized.keys.any { it.equals("Accept-Encoding", ignoreCase = true) })
        assertEquals("Nuvio", sanitized["User-Agent"])
        assertEquals("application/json", sanitized["Accept"])
    }

    @Test
    fun redactsAddonUrlsForDiagnostics() {
        val redacted = redactDesktopAddonUrl("https://example.test/stream/movie/tt123.json?token=secret&x=1")

        assertEquals("https://example.test/stream/movie/tt123.json?<redacted>", redacted)
    }
}
