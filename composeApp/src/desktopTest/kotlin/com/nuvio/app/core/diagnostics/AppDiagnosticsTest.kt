package com.nuvio.app.core.diagnostics

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppDiagnosticsTest {
    @Test
    fun redactsSecretsHeadersAndUrlQueries() {
        val formatted = DesktopDiagnosticFormatter.format(
            level = "INFO",
            event = "stream.select",
            details = mapOf(
                "authorization" to "Bearer super-secret-token",
                "url" to "https://example.test/movie.mp4?token=secret-token&other=value",
                "message" to "failed with Bearer another-secret-token",
                "safe" to "visible",
            ),
        )

        assertTrue(formatted.contains("authorization=<redacted>"))
        assertTrue(formatted.contains("https://example.test/<redacted-url>"))
        assertTrue(formatted.contains("safe=visible"))
        assertFalse(formatted.contains("super-secret-token"))
        assertFalse(formatted.contains("another-secret-token"))
        assertFalse(formatted.contains("movie.mp4"))
        assertFalse(formatted.contains("other=value"))
    }

    @Test
    fun diagnosticsRedactionRemovesAddonConfigUrlsAndTokenPathSegments() {
        val text = """
            provider=Torrentio status=success resultCount=28 elapsedMs=120 httpStatus=403
            addonId=addon:com.stremio.torrentio.addon:https://torrentio.strem.fun/eyJkZWJyaWQiOiJ0b3Jib3gifQ/manifest.json
            providerAddonId=addon:aiostreams.viren070.com.8e9bb463-4f1:https://aiostreams.fortheweak.cloud/stremio/8e9bb463-4f18-45c2-b99d-9b11942e11ae/eyJpIjoicWdiN0NrWmhUTTZuOTFMSnh5OTIwUT09IiwiZSI6IlFSdkJnZ3lkcHlKUDZERjVYMHBpTU42V09mN0U3OGREbkNsSHk5U0tIcUU9IiwidCI6ImEifQ/manifest.json
            request=https://plugins.example.test/u/super-secret-user-token-1234567890abcdef/configure/stream/movie/tt123.json?token=secret-token
            direct=https://media.example.test/path/${"a".repeat(72)}/stream.mkv?apikey=private-key
            Authorization: Bearer bearer-secret-value
        """.trimIndent()

        val redacted = AppDiagnosticsRedactor.redact(text)

        assertTrue(redacted.contains("provider=Torrentio"))
        assertTrue(redacted.contains("status=success"))
        assertTrue(redacted.contains("resultCount=28"))
        assertTrue(redacted.contains("elapsedMs=120"))
        assertTrue(redacted.contains("httpStatus=403"))
        assertTrue(redacted.contains("https://torrentio.strem.fun/<redacted-url>"))
        assertTrue(redacted.contains("https://plugins.example.test/<redacted-url>"))
        assertTrue(redacted.contains("https://media.example.test/<redacted-url>"))

        assertFalse(redacted.contains("manifest.json"))
        assertFalse(redacted.contains("8e9bb463-4f18-45c2-b99d-9b11942e11ae"))
        assertFalse(redacted.contains("eyJpIjoicWdi"))
        assertFalse(redacted.contains("super-secret-user-token"))
        assertFalse(redacted.contains("apikey=private-key"))
        assertFalse(redacted.contains("bearer-secret-value"))
        assertFalse(redacted.contains("a".repeat(48)))
    }

    @Test
    fun diagnosticsRedactionKeepsExportedBundleReadableAcrossLines() {
        val redacted = AppDiagnosticsRedactor.redact(
            """
                ===== nuvio.log =====
                request=https://example.test/u/user-token-1234567890abcdef/manifest.json?token=secret
                provider=Torrentio resultCount=28
            """.trimIndent(),
        )

        assertTrue(redacted.contains("===== nuvio.log =====\n"))
        assertTrue(redacted.contains("https://example.test/<redacted-url>\n"))
        assertTrue(redacted.contains("provider=Torrentio resultCount=28"))
    }

    @Test
    fun rotatesDiagnosticLogs() {
        val directory = Files.createTempDirectory("nuvio-diagnostics-test")
        try {
            val logFile = DesktopDiagnosticLogFile(
                directory = directory,
                maxBytes = 120,
                maxBackups = 2,
            )

            repeat(8) { index ->
                logFile.append("line=$index ${"x".repeat(80)}\n")
            }

            assertTrue(directory.resolve("nuvio.log").exists())
            assertTrue(directory.resolve("nuvio-1.log").exists())
            assertTrue(directory.resolve("nuvio-2.log").exists())
            assertFalse(directory.resolve("nuvio-3.log").exists())
            assertTrue(directory.resolve("nuvio.log").readText().isNotBlank())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
