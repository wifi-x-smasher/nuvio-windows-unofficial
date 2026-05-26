package com.nuvio.app.features.player

import java.awt.Desktop
import java.io.File
import java.net.URI

internal actual object ExternalPlayerPlatform {
    actual fun defaultPlayerId(): String? =
        DesktopExternalPlayerDiscovery.availablePlayers().firstOrNull()?.id

    actual fun availablePlayers(): List<ExternalPlayerApp> =
        DesktopExternalPlayerDiscovery.availablePlayers().map { player ->
            ExternalPlayerApp(id = player.id, name = player.name)
        }

    actual fun open(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
    ): ExternalPlayerOpenResult {
        if (request.sourceUrl.isBlank()) return ExternalPlayerOpenResult.Failed
        val players = DesktopExternalPlayerDiscovery.availablePlayers()
        val selectedPlayer = playerId
            ?.let { id -> players.firstOrNull { it.id == id } }
            ?: players.firstOrNull()
            ?: return ExternalPlayerOpenResult.NoPlayerAvailable

        if (selectedPlayer.kind == DesktopExternalPlayerKind.System) {
            if (!Desktop.isDesktopSupported()) return ExternalPlayerOpenResult.NoPlayerAvailable
            return runCatching {
                val source = request.sourceUrl
                if (source.startsWith("file:", ignoreCase = true)) {
                    Desktop.getDesktop().open(File(URI(source)))
                } else {
                    Desktop.getDesktop().browse(URI(source))
                }
                ExternalPlayerOpenResult.Opened
            }.getOrDefault(ExternalPlayerOpenResult.Failed)
        }

        val command = DesktopExternalPlayerCommandBuilder.build(selectedPlayer, request)
            ?: return ExternalPlayerOpenResult.NoPlayerAvailable
        return runCatching {
            ProcessBuilder(command).start()
            ExternalPlayerOpenResult.Opened
        }.getOrDefault(ExternalPlayerOpenResult.Failed)
    }
}
