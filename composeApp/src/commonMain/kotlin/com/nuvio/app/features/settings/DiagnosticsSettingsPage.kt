package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.diagnostics.AppDiagnostics
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_copy
import nuvio.composeapp.generated.resources.action_open
import nuvio.composeapp.generated.resources.compose_about_version_format
import nuvio.composeapp.generated.resources.settings_diagnostics_config_health_description
import nuvio.composeapp.generated.resources.settings_diagnostics_config_health_title
import nuvio.composeapp.generated.resources.settings_diagnostics_copied_log_path
import nuvio.composeapp.generated.resources.settings_diagnostics_copied_log_folder
import nuvio.composeapp.generated.resources.settings_diagnostics_latest_log
import nuvio.composeapp.generated.resources.settings_diagnostics_local_only_description
import nuvio.composeapp.generated.resources.settings_diagnostics_log_folder
import nuvio.composeapp.generated.resources.settings_diagnostics_log_folder_open_failed
import nuvio.composeapp.generated.resources.settings_diagnostics_log_folder_opened
import nuvio.composeapp.generated.resources.settings_diagnostics_logs_unavailable
import nuvio.composeapp.generated.resources.settings_diagnostics_recent_empty
import nuvio.composeapp.generated.resources.settings_diagnostics_recent_section
import nuvio.composeapp.generated.resources.settings_diagnostics_section_app
import nuvio.composeapp.generated.resources.settings_diagnostics_section_logs
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.diagnosticsSettingsContent(
    isTablet: Boolean,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_diagnostics_section_app),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                DiagnosticsValueRow(
                    title = stringResource(Res.string.compose_about_version_format, AppVersionConfig.VERSION_NAME, AppVersionConfig.VERSION_CODE),
                    value = stringResource(Res.string.settings_diagnostics_local_only_description),
                    isTablet = isTablet,
                )
                SettingsGroupDivider(isTablet = isTablet)
                DiagnosticsValueRow(
                    title = stringResource(Res.string.settings_diagnostics_config_health_title),
                    value = stringResource(Res.string.settings_diagnostics_config_health_description),
                    isTablet = isTablet,
                )
            }
        }
    }

    item {
        val clipboardManager = LocalClipboardManager.current
        val logFilePath = AppDiagnostics.logFilePath()
        val logDirectoryPath = AppDiagnostics.logDirectoryPath()
        val copiedLogPathMessage = stringResource(Res.string.settings_diagnostics_copied_log_path)
        val copiedLogFolderMessage = stringResource(Res.string.settings_diagnostics_copied_log_folder)
        val openedLogFolderMessage = stringResource(Res.string.settings_diagnostics_log_folder_opened)
        val openLogFolderFailedMessage = stringResource(Res.string.settings_diagnostics_log_folder_open_failed)
        var actionMessage by remember { mutableStateOf<String?>(null) }

        SettingsSection(
            title = stringResource(Res.string.settings_diagnostics_section_logs),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                DiagnosticsValueRow(
                    title = stringResource(Res.string.settings_diagnostics_latest_log),
                    value = logFilePath ?: stringResource(Res.string.settings_diagnostics_logs_unavailable),
                    isTablet = isTablet,
                )
                SettingsGroupDivider(isTablet = isTablet)
                DiagnosticsValueRow(
                    title = stringResource(Res.string.settings_diagnostics_log_folder),
                    value = logDirectoryPath ?: stringResource(Res.string.settings_diagnostics_logs_unavailable),
                    isTablet = isTablet,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isTablet) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DiagnosticsLogActions(
                                logFilePath = logFilePath,
                                logDirectoryPath = logDirectoryPath,
                                onCopy = { value, copiedMessage ->
                                    clipboardManager.setText(AnnotatedString(value))
                                    actionMessage = copiedMessage
                                },
                                onOpen = {
                                    actionMessage = if (AppDiagnostics.openLogDirectory()) {
                                        openedLogFolderMessage
                                    } else {
                                        openLogFolderFailedMessage
                                    }
                                },
                                copiedLogPathMessage = copiedLogPathMessage,
                                copiedLogFolderMessage = copiedLogFolderMessage,
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DiagnosticsLogActions(
                                logFilePath = logFilePath,
                                logDirectoryPath = logDirectoryPath,
                                onCopy = { value, copiedMessage ->
                                    clipboardManager.setText(AnnotatedString(value))
                                    actionMessage = copiedMessage
                                },
                                onOpen = {
                                    actionMessage = if (AppDiagnostics.openLogDirectory()) {
                                        openedLogFolderMessage
                                    } else {
                                        openLogFolderFailedMessage
                                    }
                                },
                                copiedLogPathMessage = copiedLogPathMessage,
                                copiedLogFolderMessage = copiedLogFolderMessage,
                            )
                        }
                    }
                    actionMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    item {
        val recentLines = remember { AppDiagnostics.recentDiagnosticLines(limit = 8) }
        SettingsSection(
            title = stringResource(Res.string.settings_diagnostics_recent_section),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                if (recentLines.isEmpty()) {
                    DiagnosticsValueRow(
                        title = stringResource(Res.string.settings_diagnostics_recent_empty),
                        value = stringResource(Res.string.settings_diagnostics_local_only_description),
                        isTablet = isTablet,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        recentLines.forEach { line ->
                            Text(
                                text = line,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsLogActions(
    logFilePath: String?,
    logDirectoryPath: String?,
    onCopy: (String, String) -> Unit,
    onOpen: () -> Unit,
    copiedLogPathMessage: String,
    copiedLogFolderMessage: String,
) {
    Button(
        enabled = logDirectoryPath != null,
        onClick = onOpen,
    ) {
        Text(stringResource(Res.string.action_open))
    }
    TextButton(
        enabled = logDirectoryPath != null,
        onClick = {
            logDirectoryPath?.let {
                onCopy(it, copiedLogFolderMessage)
            }
        },
    ) {
        Text(stringResource(Res.string.settings_diagnostics_log_folder))
    }
    TextButton(
        enabled = logFilePath != null,
        onClick = {
            logFilePath?.let {
                onCopy(it, copiedLogPathMessage)
            }
        },
    ) {
        Text(stringResource(Res.string.action_copy))
    }
}

@Composable
private fun DiagnosticsValueRow(
    title: String,
    value: String,
    isTablet: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = if (isTablet) 16.dp else 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
