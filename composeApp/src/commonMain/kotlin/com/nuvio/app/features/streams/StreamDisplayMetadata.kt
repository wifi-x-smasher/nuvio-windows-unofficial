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
        ?: title?.repairMojibake()?.takeIf { it.isNotBlank() && !it.equals(streamLabel, ignoreCase = true) }
        ?: clientResolve?.filename?.repairMojibake()?.takeIf(String::isNotBlank)
        ?: clientResolve?.torrentName?.repairMojibake()?.takeIf(String::isNotBlank)

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

internal fun String.repairMojibake(): String {
    if (!hasMojibakeMarker()) return this
    val repaired = runCatching {
        buildByteArrayFromWindows1252Text()?.decodeToString()
    }.getOrNull()
    return repaired
        ?.takeIf { it.isNotBlank() && it.countMojibakeMarkers() < countMojibakeMarkers() }
        ?: this
}

private fun String.hasMojibakeMarker(): Boolean =
    any { it == 'Â' || it == 'Ã' || it == 'â' || it == 'ð' || it == 'Ÿ' || it == '�' }

private fun String.countMojibakeMarkers(): Int =
    count { it == 'Â' || it == 'Ã' || it == 'â' || it == 'ð' || it == 'Ÿ' || it == '�' }

private fun String.buildByteArrayFromWindows1252Text(): ByteArray? {
    val bytes = ByteArray(length)
    forEachIndexed { index, char ->
        bytes[index] = when (val code = char.code) {
            in 0x00..0x7F -> code.toByte()
            in 0xA0..0xFF -> code.toByte()
            0x20AC -> 0x80.toByte()
            0x201A -> 0x82.toByte()
            0x0192 -> 0x83.toByte()
            0x201E -> 0x84.toByte()
            0x2026 -> 0x85.toByte()
            0x2020 -> 0x86.toByte()
            0x2021 -> 0x87.toByte()
            0x02C6 -> 0x88.toByte()
            0x2030 -> 0x89.toByte()
            0x0160 -> 0x8A.toByte()
            0x2039 -> 0x8B.toByte()
            0x0152 -> 0x8C.toByte()
            0x017D -> 0x8E.toByte()
            0x2018 -> 0x91.toByte()
            0x2019 -> 0x92.toByte()
            0x201C -> 0x93.toByte()
            0x201D -> 0x94.toByte()
            0x2022 -> 0x95.toByte()
            0x2013 -> 0x96.toByte()
            0x2014 -> 0x97.toByte()
            0x02DC -> 0x98.toByte()
            0x2122 -> 0x99.toByte()
            0x0161 -> 0x9A.toByte()
            0x203A -> 0x9B.toByte()
            0x0153 -> 0x9C.toByte()
            0x017E -> 0x9E.toByte()
            0x0178 -> 0x9F.toByte()
            else -> return null
        }
    }
    return bytes
}
