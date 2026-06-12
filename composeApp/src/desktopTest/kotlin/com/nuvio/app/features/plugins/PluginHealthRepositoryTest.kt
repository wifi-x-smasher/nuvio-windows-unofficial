package com.nuvio.app.features.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginHealthRepositoryTest {

    private val repos = listOf(
        PluginRepositoryItem(manifestUrl = "https://repo.example/manifest.json", name = "My Repo"),
    )

    private fun scraper(
        id: String,
        enabled: Boolean = true,
        name: String = id,
        repo: String = "https://repo.example/manifest.json",
    ) = PluginScraper(
        id = id,
        repositoryUrl = repo,
        name = name,
        description = "",
        version = "1",
        filename = "$id.js",
        supportedTypes = listOf("movie"),
        enabled = enabled,
        manifestEnabled = true,
        code = "",
    )

    @BeforeTest
    fun reset() {
        PluginHealthRepository.clear()
    }

    @Test
    fun successWithResultsRecordsSuccess() {
        PluginHealthRepository.recordStart("a")
        PluginHealthRepository.recordSuccess("a", resultCount = 5, durationMs = 1200)
        val state = PluginHealthRepository.snapshot(listOf(scraper("a")), repos)
        val entry = state.entries.single()
        assertEquals(PluginHealthStatus.Success, entry.status)
        assertEquals(5, entry.resultCount)
        assertEquals(1, state.summary.ok)
    }

    @Test
    fun successWithZeroResultsRecordsEmpty() {
        PluginHealthRepository.recordSuccess("a", resultCount = 0, durationMs = 800)
        val entry = PluginHealthRepository.snapshot(listOf(scraper("a")), repos).entries.single()
        assertEquals(PluginHealthStatus.Empty, entry.status)
        assertEquals(1, PluginHealthRepository.snapshot(listOf(scraper("a")), repos).summary.empty)
    }

    @Test
    fun timeoutMapsToTimedOut() {
        val error = runCatching {
            runBlocking { withTimeout(1) { delay(1_000) } }
        }.exceptionOrNull() ?: throw AssertionError("expected a timeout exception")
        PluginHealthRepository.recordFailure("a", error, durationMs = 60_000)
        val entry = PluginHealthRepository.snapshot(listOf(scraper("a")), repos).entries.single()
        assertEquals(PluginHealthStatus.TimedOut, entry.status)
        assertEquals("Timed out", entry.failureReason)
    }

    @Test
    fun normalExceptionMapsToFailed() {
        PluginHealthRepository.recordFailure("a", RuntimeException("Request failed with HTTP 403"), durationMs = 500)
        val entry = PluginHealthRepository.snapshot(listOf(scraper("a")), repos).entries.single()
        assertEquals(PluginHealthStatus.Failed, entry.status)
        assertEquals("HTTP 403", entry.failureReason)
    }

    @Test
    fun disabledScraperMapsToDisabledEvenAfterRunning() {
        PluginHealthRepository.recordSuccess("a", resultCount = 5, durationMs = 100)
        val entry = PluginHealthRepository.snapshot(listOf(scraper("a", enabled = false)), repos).entries.single()
        assertEquals(PluginHealthStatus.Disabled, entry.status)
        assertEquals(1, PluginHealthRepository.snapshot(listOf(scraper("a", enabled = false)), repos).summary.disabled)
    }

    @Test
    fun neverRunEnabledScraperIsIdle() {
        val entry = PluginHealthRepository.snapshot(listOf(scraper("a")), repos).entries.single()
        assertEquals(PluginHealthStatus.Idle, entry.status)
    }

    @Test
    fun diagnosticSummaryRedactsUrlsAndTokens() {
        PluginHealthRepository.recordFailure(
            "a",
            RuntimeException("https://debrid.example/api?token=SUPERSECRET failed HTTP 403"),
            durationMs = 500,
        )
        val state = PluginHealthRepository.snapshot(listOf(scraper("a", name = "Provider X")), repos)
        val summary = PluginHealthRepository.buildDiagnosticSummary(state)
        assertFalse(summary.contains("SUPERSECRET"))
        assertFalse(summary.contains("debrid.example"))
        assertFalse(summary.contains("token="))
        assertFalse(summary.contains("https://"))
        assertFalse(summary.contains("manifest.json"))
        assertTrue(summary.contains("HTTP 403"))
        assertTrue(summary.contains("Provider X"))
        assertTrue(summary.contains("My Repo"))
    }

    @Test
    fun dashboardVisibleEntriesKeepCompleteDiagnosticsOutOfTheMainList() {
        PluginHealthRepository.recordStart("running")
        PluginHealthRepository.recordSuccess("ok", resultCount = 2, durationMs = 700)
        PluginHealthRepository.recordSuccess("empty", resultCount = 0, durationMs = 300)
        PluginHealthRepository.recordFailure("failed", RuntimeException("Request failed with HTTP 403"), durationMs = 500)
        val state = PluginHealthRepository.snapshot(
            listOf(
                scraper("running", name = "Still running"),
                scraper("ok", name = "Returned streams"),
                scraper("empty", name = "No streams"),
                scraper("failed", name = "Broken provider"),
                scraper("idle", name = "Never used"),
                scraper("disabled", enabled = false, name = "Disabled provider"),
            ),
            repos,
        )

        val visibleNames = state.visibleDashboardEntries().map { it.providerName }
        val diagnostics = PluginHealthRepository.buildDiagnosticSummary(state)

        assertEquals(listOf("Still running", "Returned streams"), visibleNames)
        assertTrue(diagnostics.contains("No streams"))
        assertTrue(diagnostics.contains("Broken provider"))
        assertTrue(diagnostics.contains("Never used"))
        assertTrue(diagnostics.contains("Disabled provider"))
    }

    @Test
    fun concurrentRecordingStaysConsistent() {
        runBlocking {
            val ids = (1..50).map { "s$it" }
            ids.map { id ->
                launch(Dispatchers.Default) {
                    PluginHealthRepository.recordStart(id)
                    PluginHealthRepository.recordSuccess(id, resultCount = 1, durationMs = 10)
                }
            }.forEach { it.join() }
            val state = PluginHealthRepository.snapshot(ids.map { scraper(it) }, repos)
            assertEquals(50, state.summary.ok)
        }
    }
}
