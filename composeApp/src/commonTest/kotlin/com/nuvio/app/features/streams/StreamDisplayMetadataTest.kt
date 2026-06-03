package com.nuvio.app.features.streams

import com.nuvio.app.features.plugins.PluginRuntimeResult
import com.nuvio.app.features.plugins.PluginScraper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamDisplayMetadataTest {
    @Test
    fun `technical metadata line includes parsed debrid details and size fallback`() {
        val stream = StreamItem(
            name = "Movie Pack",
            addonName = "Debrid",
            addonId = "addon:debrid",
            clientResolve = StreamClientResolve(
                type = "debrid",
                infoHash = "abc",
                isCached = true,
                stream = StreamClientResolveStream(
                    raw = StreamClientResolveRaw(
                        size = 10_307_921_510L,
                        parsed = StreamClientResolveParsed(
                            resolution = "2160p",
                            quality = "WEB-DL",
                            codec = "HEVC",
                            bitDepth = "10bit",
                            hdr = listOf("DV", "HDR10"),
                            audio = listOf("Atmos"),
                            channels = listOf("5.1"),
                            languages = listOf("EN", "HI"),
                        ),
                    ),
                ),
            ),
        )

        val metadata = stream.streamTechnicalMetadataLine().orEmpty()

        assertTrue(metadata.contains("2160p"))
        assertTrue(metadata.contains("WEB-DL"))
        assertTrue(metadata.contains("HEVC"))
        assertTrue(metadata.contains("DV / HDR10"))
        assertTrue(metadata.contains("Atmos"))
        assertTrue(metadata.contains("5.1"))
        assertTrue(metadata.contains("EN / HI"))
        assertEquals("9.6 GB", stream.streamSizeLabel())
        assertTrue(!metadata.contains("9.6"))
    }

    @Test
    fun `metadata badges expose TV readable technical fields without size duplication`() {
        val stream = StreamItem(
            name = "Movie Pack",
            addonName = "Debrid",
            addonId = "addon:debrid",
            clientResolve = StreamClientResolve(
                type = "debrid",
                infoHash = "abc",
                isCached = true,
                stream = StreamClientResolveStream(
                    raw = StreamClientResolveRaw(
                        size = 10_307_921_510L,
                        parsed = StreamClientResolveParsed(
                            resolution = "2160p",
                            quality = "WEB-DL",
                            codec = "HEVC",
                            bitDepth = "10bit",
                            hdr = listOf("DV", "HDR10"),
                            audio = listOf("Atmos"),
                            channels = listOf("5.1"),
                            languages = listOf("EN", "HI"),
                        ),
                    ),
                ),
            ),
        )

        val badges = stream.streamMetadataBadges()

        assertEquals(
            listOf("2160p", "WEB-DL", "HEVC", "10bit", "DV / HDR10", "Atmos", "5.1", "EN / HI"),
            badges.map { it.label },
        )
        assertTrue(badges.none { it.label.contains("GB") })
    }

    @Test
    fun `plugin streams show quality in name and size language seed peers in description`() {
        val stream = PluginRuntimeResult(
            title = "The Movie",
            name = "The.Movie.Release",
            url = "https://example.test/movie.mkv",
            quality = "4K UHD",
            size = "9.6 GB",
            language = "EN",
            seeders = 42,
            peers = 7,
        ).toStreamItem(
            scraper = PluginScraper(
                id = "scraper",
                repositoryUrl = "https://repo.example/manifest.json",
                name = "MovieBlast",
                description = "",
                version = "1.0.0",
                filename = "movieblast.js",
                supportedTypes = listOf("movie"),
                enabled = true,
                manifestEnabled = true,
                code = "",
            ),
            addonName = "MovieBlast",
            addonId = "plugin:scraper",
        )

        assertEquals("The.Movie.Release - 4K UHD", stream.name)
        assertEquals("The Movie", stream.title)
        assertEquals("9.6 GB • EN • S:42 • P:7", stream.description)
    }

    @Test
    fun `stream labels repair common windows mojibake without touching valid emoji`() {
        val broken = StreamItem(
            name = "ðŸ”¥ Cached 4K",
            description = "ðŸŽ¬ WEB-DL",
            addonName = "Comet",
            addonId = "addon:comet",
        )
        val valid = StreamItem(
            name = "🔥 Cached 4K",
            description = "🎬 WEB-DL",
            addonName = "Comet",
            addonId = "addon:comet",
        )

        assertEquals("🔥 Cached 4K", broken.streamLabel)
        assertEquals("🎬 WEB-DL", broken.streamSubtitle)
        assertEquals("🔥 Cached 4K", valid.streamLabel)
        assertEquals("🎬 WEB-DL", valid.streamSubtitle)
    }
}
