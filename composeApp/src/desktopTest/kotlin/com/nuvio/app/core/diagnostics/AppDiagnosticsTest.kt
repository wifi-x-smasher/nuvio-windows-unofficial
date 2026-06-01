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
        assertTrue(formatted.contains("https://example.test/movie.mp4?<redacted>"))
        assertTrue(formatted.contains("safe=visible"))
        assertFalse(formatted.contains("super-secret-token"))
        assertFalse(formatted.contains("another-secret-token"))
        assertFalse(formatted.contains("other=value"))
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
