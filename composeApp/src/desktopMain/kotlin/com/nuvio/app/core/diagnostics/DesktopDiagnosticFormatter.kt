package com.nuvio.app.core.diagnostics

import java.time.Instant
import java.util.Locale

internal object DesktopDiagnosticFormatter {
    private val sensitiveKeyFragments = listOf("authorization", "token", "secret", "password", "key")
    private val bearerPattern = Regex("""Bearer\s+[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE)
    private val queryPattern = Regex("""(\bhttps?://[^\s?]+)\?[^}\s]+""")

    fun format(
        level: String,
        event: String,
        details: Map<String, String?>,
        throwable: Throwable? = null,
    ): String = buildString {
        append(Instant.now().toString())
        append(' ')
        append(level.uppercase(Locale.US))
        append(' ')
        append(event)
        val normalizedDetails = details
            .filterValues { it != null }
            .mapValues { (key, value) -> redact(key, value.orEmpty()) }
        if (normalizedDetails.isNotEmpty()) {
            append(' ')
            append(normalizedDetails.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
                "$key=$value"
            })
        }
        throwable?.let {
            append(' ')
            append(it::class.simpleName ?: "Throwable")
            it.message?.takeIf(String::isNotBlank)?.let { message ->
                append(": ")
                append(redact("message", message))
            }
        }
    }

    private fun redact(key: String, value: String): String {
        if (sensitiveKeyFragments.any { key.contains(it, ignoreCase = true) }) {
            return "<redacted>"
        }
        return value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(bearerPattern, "Bearer <redacted>")
            .replace(queryPattern, "$1?<redacted>")
    }
}
