package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamModelsTest {
    private val hexHash = "0123456789abcdef0123456789abcdef01234567"
    private val base32Hash = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    @Test
    fun `torrent scheme url is detected as torrent stream`() {
        val stream = stream(url = "torrent://$hexHash")

        assertTrue(stream.isTorrentStream)
        assertNull(stream.playableDirectUrl)
    }

    @Test
    fun `torrent scheme url is detected case-insensitively with whitespace`() {
        val stream = stream(url = "  TORRENT://$hexHash/2")

        assertTrue(stream.isTorrentStream)
        assertNull(stream.playableDirectUrl)
        assertEquals(hexHash, stream.p2pInfoHash)
        assertEquals(2, stream.p2pFileIdx)
    }

    @Test
    fun `torrent scheme externalUrl is not exposed as direct playback`() {
        val stream = stream(externalUrl = "torrent://$hexHash")

        assertTrue(stream.isTorrentStream)
        assertNull(stream.playableDirectUrl)
    }

    @Test
    fun `torrent url falls through to http externalUrl`() {
        val httpUrl = "https://cdn.example.com/video.mp4"
        val stream = stream(
            url = "torrent://$hexHash",
            externalUrl = httpUrl,
        )

        assertTrue(stream.isTorrentStream)
        assertEquals(httpUrl, stream.playableDirectUrl)
    }

    @Test
    fun `magnet url is not exposed as direct playback`() {
        val stream = stream(url = "\nmagnet:?xt=urn:btih:$hexHash&dn=Test")

        assertTrue(stream.isTorrentStream)
        assertNull(stream.playableDirectUrl)
        assertEquals(hexHash, stream.p2pInfoHash)
    }

    @Test
    fun `p2pInfoHash extracts base32 hash from torrent scheme url`() {
        val stream = stream(url = "torrent://$base32Hash")

        assertEquals(base32Hash, stream.p2pInfoHash)
    }

    @Test
    fun `dedicated infoHash wins over torrent scheme url`() {
        val dedicated = "fedcba9876543210fedcba9876543210fedcba98"
        val stream = stream(
            url = "torrent://$hexHash",
            infoHash = dedicated,
        )

        assertEquals(dedicated, stream.p2pInfoHash)
    }

    @Test
    fun `torrent-null sentinel yields null p2pInfoHash`() {
        val stream = stream(url = "torrent://null")

        assertTrue(stream.isTorrentStream)
        assertNull(stream.p2pInfoHash)
    }

    @Test
    fun `plain http url is not a torrent stream`() {
        val stream = stream(url = "https://cdn.example.com/torrent/video.mp4")

        assertFalse(stream.isTorrentStream)
        assertEquals("https://cdn.example.com/torrent/video.mp4", stream.playableDirectUrl)
        assertNull(stream.p2pInfoHash)
    }

    private fun stream(
        url: String? = null,
        infoHash: String? = null,
        fileIdx: Int? = null,
        externalUrl: String? = null,
    ): StreamItem = StreamItem(
        url = url,
        infoHash = infoHash,
        fileIdx = fileIdx,
        externalUrl = externalUrl,
        addonName = "TestAddon",
        addonId = "test.addon",
    )
}
