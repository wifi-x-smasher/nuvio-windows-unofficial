package com.nuvio.app.features.player

import java.awt.Desktop
import java.net.URI

internal actual object ExternalPlayerPlatform {
    actual fun defaultPlayerId(): String? = "system"

    actual fun availablePlayers(): List<ExternalPlayerApp> =
        listOf(ExternalPlayerApp(id = "system", name = "System default"))

    actual fun open(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
    ): ExternalPlayerOpenResult {
        if (request.sourceUrl.isBlank()) return ExternalPlayerOpenResult.Failed
        if (!Desktop.isDesktopSupported()) return ExternalPlayerOpenResult.NoPlayerAvailable
        return runCatching {
            Desktop.getDesktop().browse(URI(request.sourceUrl))
            ExternalPlayerOpenResult.Opened
        }.getOrDefault(ExternalPlayerOpenResult.Failed)
    }
}
