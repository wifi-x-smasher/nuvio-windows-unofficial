package com.nuvio.app.features.player

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopExternalPlayerDiscoveryTest {
    @Test
    fun discoversVlcAndMpvFromKnownWindowsLocations() {
        val env = mapOf(
            "ProgramFiles" to "C:\\Program Files",
            "LOCALAPPDATA" to "C:\\Users\\Example\\AppData\\Local",
        )
        val existing = setOf(
            Path.of("C:\\Program Files", "VideoLAN", "VLC", "vlc.exe"),
            Path.of("C:\\Users\\Example\\AppData\\Local", "Programs", "mpv", "mpv.exe"),
        )

        val players = DesktopExternalPlayerDiscovery.availablePlayers(
            env = env,
            exists = { it in existing },
        )

        assertEquals(listOf("vlc", "mpv", "system"), players.map { it.id })
        assertEquals("vlc", players.preferredImplicitExternalPlayer()?.id)
    }

    @Test
    fun systemDefaultIsNotUsedForImplicitExternalFallback() {
        val players = DesktopExternalPlayerDiscovery.availablePlayers(
            env = emptyMap(),
            exists = { false },
        )

        assertEquals(listOf("system"), players.map { it.id })
        assertEquals(null, players.preferredImplicitExternalPlayer()?.id)
    }

    @Test
    fun vlcCommandUsesSanitizedHeaders() {
        val player = DesktopExternalPlayer(
            id = "vlc",
            name = "VLC media player",
            kind = DesktopExternalPlayerKind.Vlc,
            executable = Path.of("C:\\Program Files", "VideoLAN", "VLC", "vlc.exe"),
        )
        val request = ExternalPlayerPlaybackRequest(
            sourceUrl = "https://example.test/video.m3u8",
            title = "Episode",
            streamTitle = "1080p",
            sourceHeaders = mapOf(
                "User-Agent" to "Nuvio Desktop",
                "Referer" to "https://example.test",
                "Range" to "bytes=0-",
                "X-Test" to "1",
            ),
        )

        val command = DesktopExternalPlayerCommandBuilder.build(player, request).orEmpty()

        assertTrue(command.contains("--http-user-agent=Nuvio Desktop"))
        assertTrue(command.contains("--http-referrer=https://example.test"))
        assertTrue(command.contains("--http-header=X-Test: 1"))
        assertFalse(command.any { it.contains("Range", ignoreCase = true) })
    }

    @Test
    fun discoversMpcHcFromProgramFiles() {
        val env = mapOf("ProgramFiles" to "C:\\Program Files")
        val existing = setOf(
            Path.of("C:\\Program Files", "MPC-HC", "mpc-hc64.exe"),
        )

        val players = DesktopExternalPlayerDiscovery.availablePlayers(
            env = env,
            exists = { it in existing },
        )

        assertTrue(players.any { it.id == "mpchc" })
        assertEquals("mpchc", players.first { it.id == "mpchc" }.id)
        assertEquals("MPC-HC", players.first { it.id == "mpchc" }.name)
    }

    @Test
    fun mpcHcCommandPassesUrlWithoutHeaders() {
        val player = DesktopExternalPlayer(
            id = "mpchc",
            name = "MPC-HC",
            kind = DesktopExternalPlayerKind.MpcHc,
            executable = Path.of("C:\\Program Files", "MPC-HC", "mpc-hc64.exe"),
        )
        val request = ExternalPlayerPlaybackRequest(
            sourceUrl = "https://example.test/video.mkv",
            title = "Movie",
            sourceHeaders = mapOf(
                "User-Agent" to "Nuvio Desktop",
                "Referer" to "https://example.test",
            ),
        )

        val command = DesktopExternalPlayerCommandBuilder.build(player, request).orEmpty()

        assertTrue(command.contains("/play"))
        assertTrue(command.contains("https://example.test/video.mkv"))
        assertFalse(command.any { it.contains("User-Agent", ignoreCase = true) })
        assertFalse(command.any { it.contains("Referer", ignoreCase = true) })
    }

    @Test
    fun mpvCommandPassesHeadersBeforeSourceUrl() {
        val player = DesktopExternalPlayer(
            id = "mpv",
            name = "mpv",
            kind = DesktopExternalPlayerKind.Mpv,
            executable = Path.of("C:\\Program Files", "mpv", "mpv.exe"),
        )
        val request = ExternalPlayerPlaybackRequest(
            sourceUrl = "https://example.test/video.m3u8",
            title = "Episode",
            sourceHeaders = mapOf("Authorization" to "Bearer token"),
        )

        val command = DesktopExternalPlayerCommandBuilder.build(player, request).orEmpty()

        assertTrue(command.contains("--http-header-fields=Authorization: Bearer token"))
        assertEquals("https://example.test/video.m3u8", command.last())
    }

    @Test
    fun mpvCommandJoinsExtraHeadersIntoOneField() {
        val request = ExternalPlayerPlaybackRequest(
            sourceUrl = "https://example.test/video.m3u8",
            title = "Episode",
            sourceHeaders = linkedMapOf(
                "Authorization" to "Bearer token",
                "X-Session" to "abc123",
            ),
        )

        val command = DesktopExternalPlayerCommandBuilder.build(mpvPlayer(), request).orEmpty()

        assertEquals(
            listOf("--http-header-fields=Authorization: Bearer token,X-Session: abc123"),
            command.filter { it.startsWith("--http-header-fields=") },
        )
    }

    @Test
    fun mpvCommandEscapesCommasAndBackslashesInHeaderValues() {
        val request = ExternalPlayerPlaybackRequest(
            sourceUrl = "https://example.test/video.m3u8",
            title = "Episode",
            sourceHeaders = mapOf("Cookie" to "a=1, b=2\\3"),
        )

        val command = DesktopExternalPlayerCommandBuilder.build(mpvPlayer(), request).orEmpty()

        assertTrue(command.contains("--http-header-fields=Cookie: a=1\\, b=2\\\\3"))
    }

    @Test
    fun mpvCommandOmitsHeaderFieldsWhenOnlyDedicatedHeadersArePresent() {
        val request = ExternalPlayerPlaybackRequest(
            sourceUrl = "https://example.test/video.m3u8",
            title = "Episode",
            sourceHeaders = mapOf(
                "User-Agent" to "Nuvio",
                "Referer" to "https://example.test",
            ),
        )

        val command = DesktopExternalPlayerCommandBuilder.build(mpvPlayer(), request).orEmpty()

        assertFalse(command.any { it.startsWith("--http-header-fields=") })
        assertTrue(command.contains("--user-agent=Nuvio"))
        assertTrue(command.contains("--referrer=https://example.test"))
    }

    private fun mpvPlayer(): DesktopExternalPlayer =
        DesktopExternalPlayer(
            id = "mpv",
            name = "mpv",
            kind = DesktopExternalPlayerKind.Mpv,
            executable = Path.of("C:\\Program Files", "mpv", "mpv.exe"),
        )
}
