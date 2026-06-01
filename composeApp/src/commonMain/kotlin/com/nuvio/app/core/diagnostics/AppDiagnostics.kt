package com.nuvio.app.core.diagnostics

expect object AppDiagnostics {
    fun install()

    fun breadcrumb(
        event: String,
        details: Map<String, String?> = emptyMap(),
    )

    fun error(
        event: String,
        throwable: Throwable? = null,
        details: Map<String, String?> = emptyMap(),
    )

    fun logFilePath(): String?

    fun logDirectoryPath(): String?

    fun openLogDirectory(): Boolean

    fun recentDiagnosticLines(limit: Int): List<String>
}
