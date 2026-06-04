package com.nuvio.app.features.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class PluginRuntimeTest {
    @Test
    fun asyncGetStreamsPromiseProducesResults() = runBlocking {
        val results = PluginRuntime.executePlugin(
            code = """
                module.exports.getStreams = async function(tmdbId, mediaType, season, episode) {
                    return Promise.resolve([
                        {
                            title: "Async Stream",
                            name: "Async.Release",
                            url: "https://example.test/video.mkv",
                            quality: "4K"
                        }
                    ]);
                };
            """.trimIndent(),
            tmdbId = "603",
            mediaType = "movie",
            season = null,
            episode = null,
            scraperId = "test:async",
        )

        assertEquals(1, results.size)
        assertEquals("Async Stream", results.first().title)
        assertEquals("https://example.test/video.mkv", results.first().url)
    }
}
