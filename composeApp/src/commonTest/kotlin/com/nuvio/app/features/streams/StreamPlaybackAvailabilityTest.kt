package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamPlaybackAvailabilityTest {
    @Test
    fun torrentOnlyStreamExplainsThatAResolverIsNeeded() {
        val stream = StreamItem(
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            addonName = "Torrentio",
            addonId = "addon:com.stremio.torrentio.addon:https://torrentio.strem.fun/manifest.json",
        )

        assertEquals(
            StreamPlaybackUnavailableReason.TorrentNeedsResolver,
            stream.playbackUnavailableReason(),
        )
        assertEquals(
            "resolver",
            stream.playbackUnavailableMessage(
                torrentNeedsResolverMessage = "resolver",
                noDirectLinkMessage = "direct",
            ),
        )
    }

    @Test
    fun directStreamUsesTheGenericMissingLinkMessageIfItStillCannotBeLaunched() {
        val stream = StreamItem(
            name = "Broken direct stream",
            addonName = "Example",
            addonId = "addon:example",
        )

        assertEquals(StreamPlaybackUnavailableReason.NoDirectLink, stream.playbackUnavailableReason())
        assertEquals(
            "direct",
            stream.playbackUnavailableMessage(
                torrentNeedsResolverMessage = "resolver",
                noDirectLinkMessage = "direct",
            ),
        )
    }
}
