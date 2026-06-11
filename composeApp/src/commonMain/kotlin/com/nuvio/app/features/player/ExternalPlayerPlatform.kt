package com.nuvio.app.features.player

data class ExternalPlayerApp(
    val id: String,
    val name: String,
)

data class ExternalPlayerPlaybackRequest(
    val sourceUrl: String,
    val title: String,
    val streamTitle: String? = null,
    val sourceHeaders: Map<String, String> = emptyMap(),
    // Remote URL of the subtitle the user has active in the internal player. Platforms that
    // support external subtitle handoff (desktop) download it locally before launch.
    val subtitleUrl: String? = null,
)

enum class ExternalPlayerOpenResult {
    Opened,
    NotConfigured,
    NoPlayerAvailable,
    Failed,
}

internal expect object ExternalPlayerPlatform {
    fun defaultPlayerId(): String?
    fun availablePlayers(): List<ExternalPlayerApp>
    fun open(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
    ): ExternalPlayerOpenResult
}
