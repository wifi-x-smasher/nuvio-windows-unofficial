package com.nuvio.app.features.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioIconActionButton
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.features.streams.epochMs
import kotlinx.coroutines.launch

@Composable
fun PluginHealthDashboardCard(
    scrapers: List<PluginScraper>,
    repositories: List<PluginRepositoryItem>,
    modifier: Modifier = Modifier,
) {
    val revision by PluginHealthRepository.revision.collectAsStateWithLifecycle()
    val state = remember(scrapers, repositories, revision) {
        PluginHealthRepository.snapshot(scrapers, repositories)
    }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun retry(scraperId: String) {
        scope.launch {
            PluginHealthRepository.recordStart(scraperId)
            val started = epochMs()
            PluginRepository.testScraper(scraperId).fold(
                onSuccess = { PluginHealthRepository.recordSuccess(scraperId, it.size, epochMs() - started) },
                onFailure = { PluginHealthRepository.recordFailure(scraperId, it, epochMs() - started) },
            )
        }
    }

    NuvioSurfaceCard(modifier = modifier) {
        val summary = state.summary
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HealthChip("Running", summary.running, statusColor(PluginHealthStatus.Running))
            HealthChip("OK", summary.ok, statusColor(PluginHealthStatus.Success))
            HealthChip("No results", summary.empty, statusColor(PluginHealthStatus.Empty))
            HealthChip("Failed", summary.failed, statusColor(PluginHealthStatus.Failed))
            HealthChip("Timed out", summary.timedOut, statusColor(PluginHealthStatus.TimedOut))
            HealthChip("Disabled", summary.disabled, statusColor(PluginHealthStatus.Disabled))
        }

        val visibleEntries = state.visibleDashboardEntries()

        Spacer(modifier = Modifier.height(12.dp))
        if (visibleEntries.isEmpty()) {
            Text(
                text = if (state.lastUpdatedAtMillis == 0L) {
                    "No plugin runs recorded yet. Start a stream search to populate provider health."
                } else {
                    "No providers returned streams in the last search. Copy diagnostics includes the full provider breakdown."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            visibleEntries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                ProviderHealthRow(entry = entry, onRetry = { retry(entry.scraperId) })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NuvioPrimaryButton(
                text = "Copy diagnostics",
                onClick = {
                    clipboard.setText(AnnotatedString(PluginHealthRepository.buildDiagnosticSummary(state)))
                },
            )
            NuvioPrimaryButton(
                text = "Retry failed",
                enabled = summary.failed + summary.timedOut > 0,
                onClick = {
                    state.entries
                        .filter { it.status == PluginHealthStatus.Failed || it.status == PluginHealthStatus.TimedOut }
                        .forEach { retry(it.scraperId) }
                },
            )
            NuvioPrimaryButton(
                text = "Clear",
                onClick = { PluginHealthRepository.clear() },
            )
        }
    }
}

@Composable
private fun ProviderHealthRow(
    entry: PluginHealthEntry,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor(entry.status)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.providerName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildMetaLine(entry),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.status != PluginHealthStatus.Running) {
            NuvioIconActionButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Test ${entry.providerName}",
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun HealthChip(label: String, count: Int, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

private fun buildMetaLine(entry: PluginHealthEntry): String {
    val parts = mutableListOf(entry.repositoryName)
    parts += entry.status.shortLabel()
    entry.failureReason?.let { parts += it }
    if (entry.status == PluginHealthStatus.Success || entry.status == PluginHealthStatus.Empty) {
        parts += "${entry.resultCount} results"
    }
    if (entry.durationMs > 0L) {
        parts += if (entry.durationMs >= 1000L) "${(entry.durationMs / 100L) / 10.0}s" else "${entry.durationMs}ms"
    }
    return parts.joinToString(" · ")
}

private fun statusColor(status: PluginHealthStatus): Color = when (status) {
    PluginHealthStatus.Running -> Color(0xFF4FC3F7)
    PluginHealthStatus.Success -> Color(0xFF66BB6A)
    PluginHealthStatus.Empty -> Color(0xFFFFB74D)
    PluginHealthStatus.Failed -> Color(0xFFEF5350)
    PluginHealthStatus.TimedOut -> Color(0xFFFF8A65)
    PluginHealthStatus.Disabled -> Color(0xFF9E9E9E)
    PluginHealthStatus.Idle -> Color(0xFF9E9E9E)
}
