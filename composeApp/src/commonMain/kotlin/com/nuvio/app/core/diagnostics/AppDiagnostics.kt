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

    fun runtimeLogFilePath(): String?

    fun logDirectoryPath(): String?

    fun runtimeLogDirectoryPath(): String?

    fun openLogFile(): Boolean

    fun openRuntimeLogFile(): Boolean

    fun openLogDirectory(): Boolean

    fun recentDiagnosticLines(limit: Int): List<String>
}
