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

        // Download the active addon subtitle to a temp file so the external player can load it.
        // MPC-HC does not reliably accept a subtitle path via CLI, so skip it there.
        val subtitleFile = request.subtitleUrl
            ?.takeIf { it.isNotBlank() && selectedPlayer.kind != DesktopExternalPlayerKind.MpcHc }
            ?.let { url ->
                downloadSubtitleToTemp(url).also { path ->
                    AppDiagnostics.breadcrumb(
                        event = "player.external.open.subtitle",
                        details = externalRequestDetails(request, playerId) +
                            mapOf("subtitleDownloaded" to (path != null).toString()),
                    )
                }
            }

        val command = DesktopExternalPlayerCommandBuilder.build(selectedPlayer, request, subtitleFile)
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

private fun downloadSubtitleToTemp(url: String): java.nio.file.Path? = runCatching {
    val tempFile = File.createTempFile("nuvio_sub_", ".${subtitleExtension(url)}")
    tempFile.deleteOnExit()
    val connection = URI(url).toURL().openConnection()
    connection.setRequestProperty(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    )
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000
    connection.getInputStream().use { input ->
        tempFile.outputStream().use { output -> input.copyTo(output) }
    }
    if (tempFile.length() <= 0L) {
        tempFile.delete()
        null
    } else {
        tempFile.toPath()
    }
}.getOrNull()

private fun subtitleExtension(url: String): String {
    val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
    return when {
        path.endsWith(".vtt", ignoreCase = true) -> "vtt"
        path.endsWith(".ass", ignoreCase = true) -> "ass"
        path.endsWith(".ssa", ignoreCase = true) -> "ssa"
        path.endsWith(".sub", ignoreCase = true) -> "sub"
        else -> "srt"
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
