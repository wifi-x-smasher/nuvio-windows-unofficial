package com.nuvio.app.core.diagnostics

import com.nuvio.app.desktop.DesktopRuntimeLog
import java.awt.Desktop
import java.awt.GraphicsEnvironment
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
                "osVersion" to System.getProperty("os.version"),
                "java" to System.getProperty("java.version"),
                "javaVendor" to System.getProperty("java.vendor"),
                "skikoRenderApi" to System.getProperty("skiko.renderApi"),
                "composeInteropBlending" to System.getProperty("compose.interop.blending"),
                "composeLayersType" to System.getProperty("compose.layers.type"),
                "graphicsDevices" to desktopGraphicsDeviceSummary(),
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

    actual fun runtimeLogFilePath(): String? =
        runCatching { DesktopRuntimeLog.path().absolutePathString() }.getOrNull()

    actual fun logDirectoryPath(): String? =
        runCatching { logDirectory.absolutePathString() }.getOrNull()

    actual fun runtimeLogDirectoryPath(): String? =
        runCatching { DesktopRuntimeLog.path().parent.absolutePathString() }.getOrNull()

    actual fun openLogFile(): Boolean =
        openFile(logFile.activePath())

    actual fun openRuntimeLogFile(): Boolean =
        openFile(DesktopRuntimeLog.path())

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

    private fun openFile(path: Path): Boolean =
        runCatching {
            if (!Desktop.isDesktopSupported() || !path.exists()) return false
            Desktop.getDesktop().open(path.toFile())
            true
        }.getOrDefault(false)
}

private fun desktopGraphicsDeviceSummary(): String =
    runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .screenDevices
            .mapIndexed { index, device ->
                val configuration = device.defaultConfiguration
                val bounds = configuration.bounds
                val transform = configuration.defaultTransform
                "#$index:${device.safeIdString()}:${bounds.x},${bounds.y},${bounds.width}x${bounds.height}:scale=${"%.2f".format(transform.scaleX)}x${"%.2f".format(transform.scaleY)}"
            }
            .joinToString(separator = " | ")
            .take(900)
    }.getOrElse { error ->
        "unavailable:${error.message ?: error::class.simpleName.orEmpty()}"
    }

private fun java.awt.GraphicsDevice.safeIdString(): String =
    runCatching {
        javaClass.getMethod("getIDstring").invoke(this)?.toString()
    }.getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: toString()

private fun Throwable?.stackTraceStringOrEmpty(): String =
    this?.let { throwable ->
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        writer.toString().takeIf(String::isNotBlank)?.let { it + System.lineSeparator() }.orEmpty()
    }.orEmpty()
