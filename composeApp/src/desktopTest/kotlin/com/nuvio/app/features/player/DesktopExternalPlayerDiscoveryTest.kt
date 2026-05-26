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

        assertEquals(listOf("system", "vlc", "mpv"), players.map { it.id })
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
}
