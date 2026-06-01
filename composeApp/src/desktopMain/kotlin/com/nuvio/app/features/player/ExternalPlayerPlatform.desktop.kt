package com.nuvio.app.features.player

import com.nuvio.app.core.diagnostics.AppDiagnostics
import java.awt.Desktop
import java.io.File
import java.net.URI

internal actual object ExternalPlayerPlatform {
    actual fun defaultPlayerId(): String? =
        DesktopExternalPlayerDiscovery.availablePlayers().preferredImplicitExternalPlayer()?.id

    actual fun availablePlayers(): List<ExternalPlayerApp> =
        DesktopExternalPlayerDiscovery.availablePlayers().map { player ->
            ExternalPlayerApp(id = player.id, name = player.name)
        }

    actual fun open(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
    ): ExternalPlayerOpenResult {
        if (request.sourceUrl.isBlank()) {
            AppDiagnostics.error(
                event = "player.external.open.missing_source",
                throwable = null,
                details = externalRequestDetails(request, playerId),
            )
            return ExternalPlayerOpenResult.Failed
        }
        val players = DesktopExternalPlayerDiscovery.availablePlayers()
        val selectedPlayer = playerId
            ?.let { id -> players.firstOrNull { it.id == id } }
            ?: players.preferredImplicitExternalPlayer()
            ?: run {
                AppDiagnostics.breadcrumb(
                    event = "player.external.open.no_player",
                    details = externalRequestDetails(request, playerId) +
                        mapOf("availablePlayers" to players.size.toString()),
                )
                return ExternalPlayerOpenResult.NoPlayerAvailable
            }

        AppDiagnostics.breadcrumb(
            event = "player.external.open.launch",
            details = externalRequestDetails(request, playerId) +
                mapOf(
                    "selectedPlayerId" to selectedPlayer.id,
                    "selectedPlayerName" to selectedPlayer.name,
                    "selectedPlayerKind" to selectedPlayer.kind.name,
                ),
        )

        if (selectedPlayer.kind == DesktopExternalPlayerKind.System) {
            if (!Desktop.isDesktopSupported()) {
                AppDiagnostics.breadcrumb(
                    event = "player.external.open.desktop_unsupported",
                    details = externalRequestDetails(request, playerId),
                )
                return ExternalPlayerOpenResult.NoPlayerAvailable
            }
            return runCatching {
                val source = request.sourceUrl
                if (source.startsWith("file:", ignoreCase = true)) {
                    Desktop.getDesktop().open(File(URI(source)))
                } else {
                    Desktop.getDesktop().browse(URI(source))
                }
                ExternalPlayerOpenResult.Opened
            }.onFailure { throwable ->
                AppDiagnostics.error(
                    event = "player.external.open.system_failure",
                    throwable = throwable,
                    details = externalRequestDetails(request, playerId),
                )
            }.getOrDefault(ExternalPlayerOpenResult.Failed)
        }

        val command = DesktopExternalPlayerCommandBuilder.build(selectedPlayer, request)
            ?: run {
                AppDiagnostics.breadcrumb(
                    event = "player.external.open.command_unavailable",
                    details = externalRequestDetails(request, playerId) +
                        mapOf("selectedPlayerId" to selectedPlayer.id),
                )
                return ExternalPlayerOpenResult.NoPlayerAvailable
            }
        return runCatching {
            ProcessBuilder(command).start()
            ExternalPlayerOpenResult.Opened
        }.onFailure { throwable ->
            AppDiagnostics.error(
                event = "player.external.open.process_failure",
                throwable = throwable,
                details = externalRequestDetails(request, playerId) +
                    mapOf("selectedPlayerId" to selectedPlayer.id),
            )
        }.getOrDefault(ExternalPlayerOpenResult.Failed)
    }

}

private fun externalRequestDetails(
    request: ExternalPlayerPlaybackRequest,
    playerId: String?,
): Map<String, String?> =
    mapOf(
        "playerId" to playerId,
        "title" to request.title,
        "streamTitle" to request.streamTitle,
        "sourceKind" to request.sourceUrl.diagnosticSourceKind(),
        "headerCount" to request.sourceHeaders.size.toString(),
    )

private fun String?.diagnosticSourceKind(): String =
    when {
        isNullOrBlank() -> "none"
        startsWith("magnet:", ignoreCase = true) -> "magnet"
        startsWith("file:", ignoreCase = true) -> "file"
        startsWith("http://", ignoreCase = true) -> "http"
        startsWith("https://", ignoreCase = true) -> "https"
        contains(':') -> substringBefore(':').take(24)
        else -> "unknown"
    }
