package com.nuvio.app.features.streams

import com.nuvio.app.core.i18n.localizedByteUnit
import kotlin.math.round

internal enum class StreamMetadataBadgeKind {
    Resolution,
    Quality,
    Codec,
    BitDepth,
    Hdr,
    Audio,
    Channels,
    Language,
}

internal data class StreamMetadataBadge(
    val label: String,
    val kind: StreamMetadataBadgeKind,
)

internal fun StreamItem.streamDisplayDescription(): String? =
    streamSubtitle
        ?.takeIf { it.isNotBlank() && !it.equals(streamLabel, ignoreCase = true) }
        ?: title?.takeIf { it.isNotBlank() && !it.equals(streamLabel, ignoreCase = true) }
        ?: clientResolve?.filename?.takeIf(String::isNotBlank)
        ?: clientResolve?.torrentName?.takeIf(String::isNotBlank)

internal fun StreamItem.streamTechnicalMetadataLine(): String? {
    val parts = streamMetadataBadges().map { it.label }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

internal fun StreamItem.streamMetadataBadges(): List<StreamMetadataBadge> {
    val parsed = clientResolve?.stream?.raw?.parsed
    val visibleText = listOfNotNull(streamLabel, streamSubtitle).joinToString(" ").lowercase()
    return buildList {
        addBadgeIfUseful(StreamMetadataBadgeKind.Resolution, parsed?.resolution, visibleText)
        addBadgeIfUseful(StreamMetadataBadgeKind.Quality, parsed?.quality, visibleText)
        addBadgeIfUseful(StreamMetadataBadgeKind.Codec, parsed?.codec, visibleText)
        addBadgeIfUseful(StreamMetadataBadgeKind.BitDepth, parsed?.bitDepth, visibleText)
        addJoinedBadgeIfUseful(StreamMetadataBadgeKind.Hdr, parsed?.hdr, visibleText)
        addJoinedBadgeIfUseful(StreamMetadataBadgeKind.Audio, parsed?.audio, visibleText)
        addJoinedBadgeIfUseful(StreamMetadataBadgeKind.Channels, parsed?.channels, visibleText)
        addJoinedBadgeIfUseful(StreamMetadataBadgeKind.Language, parsed?.languages?.take(6), visibleText)
    }.distinctBy { it.normalizedMetadataToken() }
}

internal fun StreamItem.streamSizeBytes(): Long? =
    clientResolve?.stream?.raw?.size
        ?: behaviorHints.videoSize
        ?: debridCacheStatus?.cachedSize

internal fun StreamItem.streamSizeLabel(): String? =
    streamSizeBytes()?.toStreamSizeLabel()

private fun Long.toStreamSizeLabel(): String {
    val gib = toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) {
        val roundedGiB = round(gib * 10.0) / 10.0
        "$roundedGiB ${localizedByteUnit("GB")}"
    } else {
        val mib = toDouble() / (1024.0 * 1024.0)
        "${round(mib).toInt()} ${localizedByteUnit("MB")}"
    }
}

private fun MutableList<StreamMetadataBadge>.addJoinedBadgeIfUseful(
    kind: StreamMetadataBadgeKind,
    values: List<String>?,
    visibleText: String,
) {
    val joined = values
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.distinctBy { it.lowercase() }
        ?.joinToString(" / ")
    addBadgeIfUseful(kind, joined, visibleText)
}

private fun MutableList<StreamMetadataBadge>.addBadgeIfUseful(
    kind: StreamMetadataBadgeKind,
    value: String?,
    visibleText: String,
) {
    val cleaned = value?.trim()?.takeIf(String::isNotBlank) ?: return
    if (visibleText.contains(cleaned.lowercase())) return
    add(StreamMetadataBadge(label = cleaned, kind = kind))
}

private fun StreamMetadataBadge.normalizedMetadataToken(): String =
    label.lowercase().replace(Regex("[^a-z0-9]+"), "")
