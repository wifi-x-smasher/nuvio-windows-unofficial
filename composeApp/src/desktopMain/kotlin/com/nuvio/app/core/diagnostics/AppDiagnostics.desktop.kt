package com.nuvio.app.core.diagnostics

import com.nuvio.app.desktop.DesktopRuntimeLog
import com.nuvio.app.desktop.DesktopDisplayDiagnostics
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
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
                "rendererSource" to desktopRendererSourceSummary(),
                "graphicsDevices" to desktopGraphicsDeviceSummary(),
                "displaySnapshot" to desktopDisplaySnapshotSummary(),
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

    actual fun exportDiagnosticsBundle(appSummary: Map<String, String>): String? =
        runCatching {
            synchronized(lock) {
                ensureLogFile()
                val stamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                val target = logDirectory.resolve("nuvio-diagnostics-$stamp.txt")
                target.writeText(redactBundle(buildDiagnosticsBundle(appSummary)), StandardCharsets.UTF_8)
                target.absolutePathString()
            }
        }.getOrNull()

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

private fun buildDiagnosticsBundle(appSummary: Map<String, String>): String = buildString {
    appendLine("===== Nuvio Diagnostics Bundle =====")
    appendLine("Generated: ${Instant.now()}")
    appSummary.forEach { (key, value) -> appendLine("$key: $value") }
    appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
    appendLine("JVM: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
    appendLine(
        "Renderer: skiko=${System.getProperty("skiko.renderApi")} " +
            "composeLayers=${System.getProperty("compose.layers.type")} " +
            "source=${desktopRendererSourceSummary()}",
    )
    appendLine("GPU/Display: ${desktopGraphicsDeviceSummary()}")
    appendLine("Active Display: ${desktopDisplaySnapshotSummary()}")
    appendLine()
    appendLine("===== nuvio.log =====")
    appendLine(readBundleFile(AppDiagnostics.logFilePath()))
    appendLine()
    appendLine("===== desktop-runtime.log =====")
    appendLine(readBundleFile(AppDiagnostics.runtimeLogFilePath()))
}

private fun readBundleFile(pathString: String?): String {
    if (pathString.isNullOrBlank()) return "(not available)"
    return runCatching {
        val path = Path.of(pathString)
        if (path.exists()) path.readText(StandardCharsets.UTF_8) else "(not available)"
    }.getOrElse { "(unreadable: ${it.message ?: it::class.simpleName})" }
}

private fun redactBundle(text: String): String =
    AppDiagnosticsRedactor.redact(text)

private fun desktopGraphicsDeviceSummary(): String =
    runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .screenDevices
            .mapIndexed { index, device ->
                val configuration = device.defaultConfiguration
                val bounds = configuration.bounds
                val transform = configuration.defaultTransform
                "#$index:${device.safeIdString()}:${bounds.x},${bounds.y},${bounds.width}x${bounds.height}:scale=${formatScale(transform.scaleX)}x${formatScale(transform.scaleY)}"
            }
            .joinToString(separator = " | ")
            .take(900)
    }.getOrElse { error ->
        "unavailable:${error.message ?: error::class.simpleName.orEmpty()}"
    }

private fun desktopDisplaySnapshotSummary(): String {
    val snapshot = DesktopDisplayDiagnostics.latest()
        ?: DesktopDisplayDiagnostics.snapshot(window = null, fullscreen = false)
    return "screens=${snapshot.screenCount} " +
        "active=${snapshot.activeBounds} " +
        "scale=${formatScale(snapshot.activeScaleX)}x${formatScale(snapshot.activeScaleY)} " +
        "refresh=${snapshot.activeRefreshRateHz ?: "unknown"} " +
        "window=${snapshot.windowBounds} " +
        "fullscreen=${snapshot.fullscreen} " +
        "renderer=${snapshot.renderer ?: "unknown"}"
}

private fun formatScale(value: Double): String =
    String.format(Locale.US, "%.2f", value)

private fun desktopRendererSourceSummary(): String =
    when {
        !System.getProperty("nuvio.renderApi").isNullOrBlank() -> "system-property:nuvio.renderApi"
        !System.getenv("NUVIO_RENDER_API").isNullOrBlank() -> "env:NUVIO_RENDER_API"
        !System.getProperty("skiko.renderApi").isNullOrBlank() -> "system-property:skiko.renderApi"
        else -> "default"
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
