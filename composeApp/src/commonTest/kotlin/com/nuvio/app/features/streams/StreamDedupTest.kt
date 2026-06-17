package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StreamDedupTest {

    private fun stream(
        name: String? = null,
        title: String? = null,
        description: String? = null,
        url: String? = null,
        externalUrl: String? = null,
        infoHash: String? = null,
        fileIdx: Int? = null,
        addonName: String = "Addon",
        addonId: String = "addon:test",
    ) = StreamItem(
        name = name,
        title = title,
        description = description,
        url = url,
        externalUrl = externalUrl,
        infoHash = infoHash,
        fileIdx = fileIdx,
        addonName = addonName,
        addonId = addonId,
    )

    @Test
    fun `collapses streams that share the same external url`() {
        val streams = listOf(
            stream(name = "1080p", externalUrl = "https://host/play/abc", description = "5.1"),
            stream(name = "1080p (dup)", externalUrl = "https://host/play/abc", description = "5.1"),
        )

        val result = streams.distinctByStreamIdentity()

        assertEquals(1, result.size)
        assertEquals("1080p", result.single().name) // keeps the first occurrence
    }

    @Test
    fun `collapses streams that share the same direct url`() {
        val streams = listOf(
            stream(name = "A", url = "https://host/v.mp4"),
            stream(name = "B", url = "https://host/v.mp4"),
            stream(name = "C", url = "https://host/other.mp4"),
        )

        val result = streams.distinctByStreamIdentity()

        assertEquals(listOf("A", "C"), result.map { it.name })
    }

    @Test
    fun `keeps same torrent hash with different file index separate`() {
        val streams = listOf(
            stream(name = "S1E1", infoHash = "ABCDEF", fileIdx = 0),
            stream(name = "S1E2", infoHash = "abcdef", fileIdx = 1),
        )

        val result = streams.distinctByStreamIdentity()

        assertEquals(2, result.size)
    }

    @Test
    fun `collapses same torrent hash and file index ignoring case`() {
        val streams = listOf(
            stream(name = "S1E1", infoHash = "ABCDEF", fileIdx = 0),
            stream(name = "S1E1 dup", infoHash = "abcdef", fileIdx = 0),
        )

        val result = streams.distinctByStreamIdentity()

        assertEquals(1, result.size)
        assertEquals("S1E1", result.single().name)
    }

    @Test
    fun `returns same instance when nothing is duplicated`() {
        val streams = listOf(
            stream(name = "A", url = "https://host/a.mp4"),
            stream(name = "B", url = "https://host/b.mp4"),
        )

        assertSame(streams, streams.distinctByStreamIdentity())
    }

    @Test
    fun `distinct streams without identifiers fall back to name and title`() {
        val streams = listOf(
            stream(name = "Source 1", title = "Movie"),
            stream(name = "Source 2", title = "Movie"),
            stream(name = "Source 1", title = "Movie"), // duplicate of the first
        )

        val result = streams.distinctByStreamIdentity()

        assertEquals(listOf("Source 1", "Source 2"), result.map { it.name })
    }
}
