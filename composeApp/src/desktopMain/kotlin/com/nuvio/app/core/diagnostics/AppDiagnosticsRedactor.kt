package com.nuvio.app.core.diagnostics

internal object AppDiagnosticsRedactor {
    private val bearerPattern = Regex("""Bearer\s+[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE)
    private val urlPattern = Regex("""(?i)\bhttps?://[^\s"'<>),\]}]+""")
    private val sensitiveAssignmentPattern = Regex(
        pattern = """(?i)\b([A-Za-z0-9._-]*(?:token|api[_-]?key|apikey|secret|password|authorization)[A-Za-z0-9._-]*)\s*([:=])\s*("[^"]*"|'[^']*'|[^\s,}\]]+)""",
    )
    private val userTokenPathPattern = Regex("""(?i)(/u/)[A-Za-z0-9._~%+-]{8,}""")
    private val longEncodedSegmentPattern = Regex("""(?<=/)[A-Za-z0-9._~%+-]{48,}(?=/|$)""")

    fun redact(
        text: String,
        collapseLineBreaks: Boolean = false,
    ): String =
        text.let { source ->
            if (collapseLineBreaks) {
                source.replace('\n', ' ').replace('\r', ' ')
            } else {
                source
            }
        }
            .replace(urlPattern) { match -> redactUrl(match.value) }
            .replace(userTokenPathPattern, "$1<redacted>")
            .replace(longEncodedSegmentPattern, "<redacted-segment>")
            .replace(bearerPattern, "Bearer <redacted>")
            .replace(sensitiveAssignmentPattern) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}<redacted>"
            }

    private fun redactUrl(url: String): String {
        val schemeSeparator = url.indexOf("://")
        if (schemeSeparator <= 0) return "<redacted-url>"

        val scheme = url.substring(0, schemeSeparator)
        val remainder = url.substring(schemeSeparator + 3)
        val host = remainder.takeWhile { it != '/' && it != '?' && it != '#' }
            .trim()
            .takeIf(String::isNotBlank)
            ?: return "$scheme://<redacted-host>/<redacted-url>"

        return "$scheme://$host/<redacted-url>"
    }
}
