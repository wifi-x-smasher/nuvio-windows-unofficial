package com.nuvio.app.features.player

import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal enum class DesktopExternalPlayerKind {
    System,
    Vlc,
    Mpv,
}

internal data class DesktopExternalPlayer(
    val id: String,
    val name: String,
    val kind: DesktopExternalPlayerKind,
    val executable: Path? = null,
)

internal object DesktopExternalPlayerDiscovery {
    fun availablePlayers(): List<DesktopExternalPlayer> =
        availablePlayers(
            env = System.getenv(),
            exists = { it.isRegularFile() },
        )

    internal fun availablePlayers(
        env: Map<String, String>,
        exists: (Path) -> Boolean,
    ): List<DesktopExternalPlayer> {
        val players = mutableListOf(
            DesktopExternalPlayer(
                id = "system",
                name = "System default",
                kind = DesktopExternalPlayerKind.System,
            )
        )

        firstExisting(vlcCandidates(env), exists)?.let {
            players += DesktopExternalPlayer(
                id = "vlc",
                name = "VLC media player",
                kind = DesktopExternalPlayerKind.Vlc,
                executable = it,
            )
        }

        firstExisting(mpvCandidates(env), exists)?.let {
            players += DesktopExternalPlayer(
                id = "mpv",
                name = "mpv",
                kind = DesktopExternalPlayerKind.Mpv,
                executable = it,
            )
        }

        return players
    }

    private fun vlcCandidates(env: Map<String, String>): List<Path> =
        listOfNotNull(
            env["ProgramFiles"]?.let { Path.of(it, "VideoLAN", "VLC", "vlc.exe") },
            env["ProgramFiles(x86)"]?.let { Path.of(it, "VideoLAN", "VLC", "vlc.exe") },
            env["PROGRAMFILES"]?.let { Path.of(it, "VideoLAN", "VLC", "vlc.exe") },
            env["PROGRAMFILES(X86)"]?.let { Path.of(it, "VideoLAN", "VLC", "vlc.exe") },
        ).distinct()

    private fun mpvCandidates(env: Map<String, String>): List<Path> =
        listOfNotNull(
            env["LOCALAPPDATA"]?.let { Path.of(it, "Programs", "mpv", "mpv.exe") },
            env["LOCALAPPDATA"]?.let { Path.of(it, "mpv", "mpv.exe") },
            env["ProgramFiles"]?.let { Path.of(it, "mpv", "mpv.exe") },
            env["ProgramFiles(x86)"]?.let { Path.of(it, "mpv", "mpv.exe") },
            env["PROGRAMFILES"]?.let { Path.of(it, "mpv", "mpv.exe") },
            env["PROGRAMFILES(X86)"]?.let { Path.of(it, "mpv", "mpv.exe") },
        ).distinct()

    private fun firstExisting(
        paths: List<Path>,
        exists: (Path) -> Boolean,
    ): Path? =
        paths.firstOrNull { path -> runCatching { exists(path) }.getOrDefault(false) }
}

internal object DesktopExternalPlayerCommandBuilder {
    fun build(
        player: DesktopExternalPlayer,
        request: ExternalPlayerPlaybackRequest,
    ): List<String>? {
        val executable = player.executable ?: return null
        val headers = sanitizePlaybackHeaders(request.sourceHeaders)
        return when (player.kind) {
            DesktopExternalPlayerKind.System -> null
            DesktopExternalPlayerKind.Vlc -> buildVlcCommand(executable, request, headers)
            DesktopExternalPlayerKind.Mpv -> buildMpvCommand(executable, request, headers)
        }
    }

    private fun buildVlcCommand(
        executable: Path,
        request: ExternalPlayerPlaybackRequest,
        headers: Map<String, String>,
    ): List<String> =
        buildList {
            add(executable.toString())
            add(request.sourceUrl)
            title(request)?.let { add("--meta-title=$it") }
            headers.findHeader("User-Agent")?.let { add("--http-user-agent=$it") }
            headers.findHeader("Referer", "Referrer")?.let { add("--http-referrer=$it") }
            headers.forEach { (key, value) ->
                if (!key.equals("User-Agent", ignoreCase = true) &&
                    !key.equals("Referer", ignoreCase = true) &&
                    !key.equals("Referrer", ignoreCase = true)
                ) {
                    add("--http-header=$key: $value")
                }
            }
        }

    private fun buildMpvCommand(
        executable: Path,
        request: ExternalPlayerPlaybackRequest,
        headers: Map<String, String>,
    ): List<String> =
        buildList {
            add(executable.toString())
            add("--force-window=yes")
            title(request)?.let { add("--force-media-title=$it") }
            headers.findHeader("User-Agent")?.let { add("--user-agent=$it") }
            headers.findHeader("Referer", "Referrer")?.let { add("--referrer=$it") }
            headers.forEach { (key, value) ->
                if (!key.equals("User-Agent", ignoreCase = true) &&
                    !key.equals("Referer", ignoreCase = true) &&
                    !key.equals("Referrer", ignoreCase = true)
                ) {
                    add("--http-header-fields=$key: $value")
                }
            }
            add(request.sourceUrl)
        }

    private fun title(request: ExternalPlayerPlaybackRequest): String? =
        listOf(request.title, request.streamTitle)
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .joinToString(" - ")
            .takeIf(String::isNotBlank)

    private fun Map<String, String>.findHeader(vararg names: String): String? =
        entries.firstOrNull { (key, _) ->
            names.any { name -> key.equals(name, ignoreCase = true) }
        }?.value
}
