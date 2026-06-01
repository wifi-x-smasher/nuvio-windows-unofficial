package com.nuvio.app.core.diagnostics

actual object AppDiagnostics {
    actual fun install() = Unit

    actual fun breadcrumb(
        event: String,
        details: Map<String, String?>,
    ) = Unit

    actual fun error(
        event: String,
        throwable: Throwable?,
        details: Map<String, String?>,
    ) = Unit

    actual fun logFilePath(): String? = null

    actual fun logDirectoryPath(): String? = null

    actual fun openLogDirectory(): Boolean = false

    actual fun recentDiagnosticLines(limit: Int): List<String> = emptyList()
}
