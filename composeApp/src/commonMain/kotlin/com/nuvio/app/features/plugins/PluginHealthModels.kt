package com.nuvio.app.features.plugins

/**
 * Local-only health view of plugin scraper providers. Fed directly by the stream-execution paths
 * (no log parsing) and never synced or uploaded.
 */
enum class PluginHealthStatus {
    Idle,
    Running,
    Success,
    Empty,
    Failed,
    TimedOut,
    Disabled,
}

internal fun PluginHealthStatus.sortOrder(): Int = when (this) {
    PluginHealthStatus.Running -> 0
    PluginHealthStatus.Failed -> 1
    PluginHealthStatus.TimedOut -> 2
    PluginHealthStatus.Empty -> 3
    PluginHealthStatus.Success -> 4
    PluginHealthStatus.Idle -> 5
    PluginHealthStatus.Disabled -> 6
}

data class PluginHealthEntry(
    val scraperId: String,
    val providerName: String,
    val repositoryName: String,
    val status: PluginHealthStatus,
    val resultCount: Int = 0,
    val durationMs: Long = 0L,
    val lastRunAtMillis: Long = 0L,
    val failureReason: String? = null,
)

data class PluginHealthSummary(
    val running: Int = 0,
    val ok: Int = 0,
    val empty: Int = 0,
    val failed: Int = 0,
    val timedOut: Int = 0,
    val disabled: Int = 0,
)

data class PluginHealthUiState(
    val entries: List<PluginHealthEntry> = emptyList(),
    val summary: PluginHealthSummary = PluginHealthSummary(),
    val lastUpdatedAtMillis: Long = 0L,
)

/**
 * Main dashboard rows stay intentionally short: show providers that are actively running or
 * actually returned streams. Copy diagnostics keeps the complete provider-by-provider state.
 */
fun PluginHealthUiState.visibleDashboardEntries(): List<PluginHealthEntry> =
    entries.filter {
        it.status == PluginHealthStatus.Running ||
            (it.status == PluginHealthStatus.Success && it.resultCount > 0)
    }
