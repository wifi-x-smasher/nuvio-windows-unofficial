package com.nuvio.app.features.plugins

import com.nuvio.app.features.streams.epochMs
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Local-only, in-memory health tracker for plugin scrapers. Fed directly by the stream-execution
 * paths (see StreamsRepository / PlayerStreamsRepository) rather than by parsing logs. Never synced.
 *
 * Scrapers run in parallel (PluginExecutionGate allows many at once), so every mutation goes through
 * MutableStateFlow.update { }, which is an atomic compare-and-set — never a read-modify-write.
 */
object PluginHealthRepository {
    private data class RunHealth(
        val status: PluginHealthStatus,
        val resultCount: Int = 0,
        val durationMs: Long = 0L,
        val lastRunAtMillis: Long = 0L,
        val failureReason: String? = null,
    )

    private val runHealth = MutableStateFlow<Map<String, RunHealth>>(emptyMap())

    // Bumped on every change so the UI can recompute the merged snapshot without exposing internals.
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun recordStart(scraperId: String) {
        val id = scraperId.trim()
        if (id.isEmpty()) return
        runHealth.update { current ->
            val previous = current[id]
            current + (id to (previous?.copy(status = PluginHealthStatus.Running)
                ?: RunHealth(status = PluginHealthStatus.Running)))
        }
        bump()
    }

    fun recordSuccess(scraperId: String, resultCount: Int, durationMs: Long) {
        val id = scraperId.trim()
        if (id.isEmpty()) return
        runHealth.update { current ->
            current + (id to RunHealth(
                status = if (resultCount > 0) PluginHealthStatus.Success else PluginHealthStatus.Empty,
                resultCount = resultCount.coerceAtLeast(0),
                durationMs = durationMs.coerceAtLeast(0L),
                lastRunAtMillis = epochMs(),
            ))
        }
        bump()
    }

    fun recordFailure(scraperId: String, error: Throwable, durationMs: Long) {
        val id = scraperId.trim()
        if (id.isEmpty()) return
        val timedOut = error is TimeoutCancellationException
        runHealth.update { current ->
            current + (id to RunHealth(
                status = if (timedOut) PluginHealthStatus.TimedOut else PluginHealthStatus.Failed,
                durationMs = durationMs.coerceAtLeast(0L),
                lastRunAtMillis = epochMs(),
                failureReason = sanitizeFailureReason(error),
            ))
        }
        bump()
    }

    fun clear() {
        runHealth.value = emptyMap()
        bump()
    }

    /**
     * Pure merge of the installed scrapers + run health into the dashboard state. Every installed
     * scraper appears: disabled ones as [PluginHealthStatus.Disabled], never-run ones as
     * [PluginHealthStatus.Idle], the rest with their last run state. Kept decoupled from
     * PluginRepository (caller passes the current state) so it is trivially testable.
     */
    fun snapshot(
        scrapers: List<PluginScraper>,
        repositories: List<PluginRepositoryItem>,
    ): PluginHealthUiState {
        val repoNameByUrl = repositories.associate { it.manifestUrl to it.name }
        val health = runHealth.value
        val entries = scrapers
            .distinctBy { it.id }
            .map { scraper ->
                val run = health[scraper.id]
                val status = when {
                    !scraper.enabled -> PluginHealthStatus.Disabled
                    run != null -> run.status
                    else -> PluginHealthStatus.Idle
                }
                val isFailure = status == PluginHealthStatus.Failed || status == PluginHealthStatus.TimedOut
                PluginHealthEntry(
                    scraperId = scraper.id,
                    providerName = scraper.name.ifBlank { scraper.id },
                    repositoryName = repoNameByUrl[scraper.repositoryUrl]?.takeIf { it.isNotBlank() }
                        ?: "Plugin repository",
                    status = status,
                    resultCount = run?.resultCount ?: 0,
                    durationMs = run?.durationMs ?: 0L,
                    lastRunAtMillis = run?.lastRunAtMillis ?: 0L,
                    failureReason = run?.failureReason?.takeIf { isFailure },
                )
            }
            .sortedWith(compareBy({ it.status.sortOrder() }, { it.providerName.lowercase() }))
        return PluginHealthUiState(
            entries = entries,
            summary = entries.toSummary(),
            lastUpdatedAtMillis = health.values.maxOfOrNull { it.lastRunAtMillis } ?: 0L,
        )
    }

    /**
     * Short, redacted, copy-pasteable summary. Contains only provider/repo display names, status,
     * counts, durations, and a sanitized reason — never URLs, tokens, headers, or scraper code.
     */
    fun buildDiagnosticSummary(state: PluginHealthUiState): String = buildString {
        val s = state.summary
        appendLine("Nuvio plugin health (local, redacted)")
        appendLine(
            "Summary: running=${s.running} ok=${s.ok} empty=${s.empty} " +
                "failed=${s.failed} timedOut=${s.timedOut} disabled=${s.disabled}",
        )
        state.entries.forEach { entry ->
            append("- [")
            append(entry.status.shortLabel())
            append("] ")
            append(entry.providerName)
            append(" (")
            append(entry.repositoryName)
            append(")")
            if (entry.status == PluginHealthStatus.Success) append(" count=${entry.resultCount}")
            if (entry.durationMs > 0L) append(" ${formatDuration(entry.durationMs)}")
            entry.failureReason?.let { append(" reason=$it") }
            appendLine()
        }
    }

    private fun bump() {
        _revision.update { it + 1 }
    }
}

private fun List<PluginHealthEntry>.toSummary(): PluginHealthSummary {
    var running = 0
    var ok = 0
    var empty = 0
    var failed = 0
    var timedOut = 0
    var disabled = 0
    forEach {
        when (it.status) {
            PluginHealthStatus.Running -> running++
            PluginHealthStatus.Success -> ok++
            PluginHealthStatus.Empty -> empty++
            PluginHealthStatus.Failed -> failed++
            PluginHealthStatus.TimedOut -> timedOut++
            PluginHealthStatus.Disabled -> disabled++
            PluginHealthStatus.Idle -> Unit
        }
    }
    return PluginHealthSummary(running, ok, empty, failed, timedOut, disabled)
}

internal fun PluginHealthStatus.shortLabel(): String = when (this) {
    PluginHealthStatus.Idle -> "IDLE"
    PluginHealthStatus.Running -> "RUNNING"
    PluginHealthStatus.Success -> "OK"
    PluginHealthStatus.Empty -> "EMPTY"
    PluginHealthStatus.Failed -> "FAILED"
    PluginHealthStatus.TimedOut -> "TIMED OUT"
    PluginHealthStatus.Disabled -> "DISABLED"
}

private fun formatDuration(ms: Long): String =
    if (ms >= 1000L) "${(ms / 100L) / 10.0}s" else "${ms}ms"

private val httpCodePattern = Regex("""(?:HTTP|status)\s*[:= ]?\s*(\d{3})""", RegexOption.IGNORE_CASE)

/**
 * Maps a scraper failure to a short, URL-free category so the dashboard and copied summary never
 * leak the raw exception message (which can contain stream/manifest URLs with tokens).
 */
internal fun sanitizeFailureReason(error: Throwable): String {
    if (error is TimeoutCancellationException) return "Timed out"
    val message = error.message.orEmpty()
    val httpCode = httpCodePattern.find(message)?.groupValues?.get(1)
    return when {
        httpCode != null -> "HTTP $httpCode"
        message.contains("timed out", ignoreCase = true) || message.contains("timeout", ignoreCase = true) -> "Timed out"
        message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("Failed to connect", ignoreCase = true) ||
            message.contains("ConnectException", ignoreCase = true) ||
            message.contains("UnknownHostException", ignoreCase = true) ||
            message.contains("Network is unreachable", ignoreCase = true) -> "Network unavailable"
        else -> "Plugin error"
    }
}
