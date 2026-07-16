package com.nuvio.app.features.player

import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal enum class DesktopExternalPlayerKind {
    System,
    Vlc,
    Mpv,
    MpcHc,
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
        val players = mutableListOf<DesktopExternalPlayer>()

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

        firstExisting(mpcHcCandidates(env), exists)?.let {
            players += DesktopExternalPlayer(
                id = "mpchc",
                name = "MPC-HC",
                kind = DesktopExternalPlayerKind.MpcHc,
                executable = it,
            )
        }

        players += DesktopExternalPlayer(
            id = "system",
            name = "System default",
            kind = DesktopExternalPlayerKind.System,
        )

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

    private fun mpcHcCandidates(env: Map<String, String>): List<Path> =
        listOfNotNull(
            env["ProgramFiles"]?.let { Path.of(it, "MPC-HC", "mpc-hc64.exe") },
            env["ProgramFiles"]?.let { Path.of(it, "MPC-HC", "mpc-hc.exe") },
            env["ProgramFiles(x86)"]?.let { Path.of(it, "MPC-HC", "mpc-hc.exe") },
            env["PROGRAMFILES"]?.let { Path.of(it, "MPC-HC", "mpc-hc64.exe") },
            env["PROGRAMFILES"]?.let { Path.of(it, "MPC-HC", "mpc-hc.exe") },
            env["PROGRAMFILES(X86)"]?.let { Path.of(it, "MPC-HC", "mpc-hc.exe") },
        ).distinct()

    private fun firstExisting(
        paths: List<Path>,
        exists: (Path) -> Boolean,
    ): Path? =
        paths.firstOrNull { path -> runCatching { exists(path) }.getOrDefault(false) }
}

internal fun List<DesktopExternalPlayer>.preferredImplicitExternalPlayer(): DesktopExternalPlayer? =
    firstOrNull { it.kind != DesktopExternalPlayerKind.System }

internal object DesktopExternalPlayerCommandBuilder {
    fun build(
        player: DesktopExternalPlayer,
        request: ExternalPlayerPlaybackRequest,
        subtitleFile: Path? = null,
    ): List<String>? {
        val executable = player.executable ?: return null
        val headers = sanitizePlaybackHeaders(request.sourceHeaders)
        return when (player.kind) {
            DesktopExternalPlayerKind.System -> null
            DesktopExternalPlayerKind.Vlc -> buildVlcCommand(executable, request, headers, subtitleFile)
            DesktopExternalPlayerKind.Mpv -> buildMpvCommand(executable, request, headers, subtitleFile)
            DesktopExternalPlayerKind.MpcHc -> buildMpcHcCommand(executable, request)
        }
    }

    private fun buildVlcCommand(
        executable: Path,
        request: ExternalPlayerPlaybackRequest,
        headers: Map<String, String>,
        subtitleFile: Path?,
    ): List<String> =
        buildList {
            add(executable.toString())
            add(request.sourceUrl)
            title(request)?.let { add("--meta-title=$it") }
            subtitleFile?.let { add("--sub-file=$it") }
            headers.findHeader("User-Agent")?.let { add("--http-user-agent=$it") }
            headers.findHeader("Referer", "Referrer")?.let { add("--http-referrer=$it") }
            headers.forEach { (key, value) ->
                if (!key.isDedicatedHeader()) {
                    add("--http-header=$key: $value")
                }
            }
        }

    private fun buildMpvCommand(
        executable: Path,
        request: ExternalPlayerPlaybackRequest,
        headers: Map<String, String>,
        subtitleFile: Path?,
    ): List<String> =
        buildList {
            add(executable.toString())
            add("--force-window=yes")
            title(request)?.let { add("--force-media-title=$it") }
            subtitleFile?.let { add("--sub-file=$it") }
            headers.findHeader("User-Agent")?.let { add("--user-agent=$it") }
            headers.findHeader("Referer", "Referrer")?.let { add("--referrer=$it") }
            headers
                .filterKeys { key -> !key.isDedicatedHeader() }
                .map { (key, value) -> escapeMpvHeaderField("$key: $value") }
                .takeIf { it.isNotEmpty() }
                ?.let { add("--http-header-fields=${it.joinToString(separator = ",")}") }
            add(request.sourceUrl)
        }

    private fun buildMpcHcCommand(
        executable: Path,
        request: ExternalPlayerPlaybackRequest,
    ): List<String> =
        buildList {
            add(executable.toString())
            add("/play")
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

    /** Headers passed through a player's own flag rather than the generic header list. */
    private fun String.isDedicatedHeader(): Boolean =
        equals("User-Agent", ignoreCase = true) ||
            equals("Referer", ignoreCase = true) ||
            equals("Referrer", ignoreCase = true)

    /**
     * mpv reads `http-header-fields` as a single comma-separated list, so commas and
     * backslashes inside a header value must be escaped or the value splits into bogus fields.
     */
    private fun escapeMpvHeaderField(field: String): String =
        field
            .replace("\\", "\\\\")
            .replace(",", "\\,")
}
