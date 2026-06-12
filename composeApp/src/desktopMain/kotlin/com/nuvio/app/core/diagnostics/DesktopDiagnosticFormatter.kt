package com.nuvio.app.core.diagnostics

import java.time.Instant
import java.util.Locale

internal object DesktopDiagnosticFormatter {
    private val sensitiveKeyFragments = listOf("authorization", "token", "secret", "password", "key")

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
        return AppDiagnosticsRedactor.redact(value, collapseLineBreaks = true)
    }
}
