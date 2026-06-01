package com.nuvio.app.core.diagnostics

import java.awt.Desktop
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

actual object AppDiagnostics {
    private val lock = Any()
    private val logDirectory: Path by lazy {
        Path.of(System.getProperty("user.home"), "AppData", "Local", "Nuvio", "logs").also { it.createDirectories() }
    }
    private val logFile: DesktopDiagnosticLogFile by lazy { DesktopDiagnosticLogFile(logDirectory) }

    actual fun install() {
        ensureLogFile()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            error(
                event = "app.uncaught_exception",
                throwable = throwable,
                details = mapOf("thread" to thread.name),
            )
            previousHandler?.uncaughtException(thread, throwable)
        }
        breadcrumb(
            event = "app.diagnostics.installed",
            details = mapOf(
                "os" to System.getProperty("os.name"),
                "java" to System.getProperty("java.version"),
            ),
        )
    }

    actual fun breadcrumb(
        event: String,
        details: Map<String, String?>,
    ) {
        append("INFO", event, details, null)
    }

    actual fun error(
        event: String,
        throwable: Throwable?,
        details: Map<String, String?>,
    ) {
        append("ERROR", event, details, throwable)
    }

    actual fun logFilePath(): String? =
        runCatching { logFile.activePath().absolutePathString() }.getOrNull()

    actual fun logDirectoryPath(): String? =
        runCatching { logDirectory.absolutePathString() }.getOrNull()

    actual fun openLogDirectory(): Boolean =
        runCatching {
            if (!Desktop.isDesktopSupported()) return false
            Desktop.getDesktop().open(logDirectory.toFile())
            true
        }.getOrDefault(false)

    actual fun recentDiagnosticLines(limit: Int): List<String> =
        runCatching {
            val activePath = logFile.activePath()
            if (!activePath.exists()) return emptyList()
            activePath.readLines(StandardCharsets.UTF_8).takeLast(limit.coerceAtLeast(0))
        }.getOrDefault(emptyList())

    private fun append(
        level: String,
        event: String,
        details: Map<String, String?>,
        throwable: Throwable?,
    ) {
        runCatching {
            synchronized(lock) {
                ensureLogFile()
                val line = DesktopDiagnosticFormatter.format(level, event, details, throwable)
                logFile.append(line + System.lineSeparator() + throwable.stackTraceStringOrEmpty())
            }
        }
    }

    private fun ensureLogFile() {
        logDirectory.createDirectories()
        if (!logFile.activePath().exists()) {
            logFile.activePath().writeText("", StandardCharsets.UTF_8)
        }
    }
}

private fun Throwable?.stackTraceStringOrEmpty(): String =
    this?.let { throwable ->
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        writer.toString().takeIf(String::isNotBlank)?.let { it + System.lineSeparator() }.orEmpty()
    }.orEmpty()
