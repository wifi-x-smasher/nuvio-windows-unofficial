package com.nuvio.app.features.player

private const val SubtitleDelayLimitMillis = 10_000

internal fun clampSubtitleDelayMillis(delayMillis: Int): Int =
    delayMillis.coerceIn(-SubtitleDelayLimitMillis, SubtitleDelayLimitMillis)

internal fun subtitleDelayMillisToMpvSeconds(delayMillis: Int): Double =
    clampSubtitleDelayMillis(delayMillis) / 1000.0

internal fun formatSubtitleDelayMillis(delayMillis: Int): String {
    val clamped = clampSubtitleDelayMillis(delayMillis)
    if (clamped == 0) return "0 ms"
    val sign = if (clamped > 0) "+" else "-"
    val absolute = kotlin.math.abs(clamped)
    return if (absolute < 1000 || absolute % 1000 != 0) {
        if (absolute < 1000) {
            "$sign$absolute ms"
        } else {
            val seconds = absolute / 1000.0
            "$sign${seconds.toString().trimEnd('0').trimEnd('.')} s"
        }
    } else {
        "$sign${absolute / 1000} s"
    }
}
