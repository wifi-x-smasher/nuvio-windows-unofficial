package com.nuvio.app.core.deeplink

import com.nuvio.app.core.diagnostics.AppDiagnostics
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.concurrent.thread

internal object DesktopDeepLinkBridge {
    private const val HOST = "127.0.0.1"
    private const val PORT = 47587
    private const val PROTOCOL = "nuvio"

    internal data class RegistryWrite(
        val label: String,
        val executable: String,
        val args: List<String>,
    )

    fun forwardToPrimaryInstanceIfNeeded(args: Array<String>): Boolean {
        val deepLink = extractDeepLinkArg(args) ?: return false
        return sendToPrimaryInstance(deepLink).also { forwarded ->
            AppDiagnostics.breadcrumb(
                event = "deeplink.desktop.forward",
                details = mapOf(
                    "forwarded" to forwarded.toString(),
                    "link" to deepLink,
                ),
            )
        }
    }

    fun install(args: Array<String>) {
        extractDeepLinkArg(args)?.let { deepLink ->
            AppDiagnostics.breadcrumb(
                event = "deeplink.desktop.startup",
                details = mapOf("link" to deepLink),
            )
            handleAppUrl(deepLink)
        }

        registerWindowsProtocolHandler()
        startPrimaryInstanceServer()
    }

    internal fun extractDeepLinkArg(args: Array<String>): String? =
        args.firstOrNull { arg ->
            arg.trim().startsWith("$PROTOCOL://", ignoreCase = true)
        }?.trim()

    private fun sendToPrimaryInstance(deepLink: String): Boolean =
        runCatching {
            Socket(HOST, PORT).use { socket ->
                socket.getOutputStream().write((deepLink + "\n").toByteArray(StandardCharsets.UTF_8))
                socket.getOutputStream().flush()
            }
            true
        }.getOrDefault(false)

    private fun startPrimaryInstanceServer() {
        thread(name = "Nuvio-Desktop-DeepLink", isDaemon = true) {
            val server = runCatching {
                ServerSocket(PORT, 8, InetAddress.getByName(HOST))
            }.onFailure { throwable ->
                AppDiagnostics.error(
                    event = "deeplink.desktop.server_start_failed",
                    throwable = throwable,
                    details = mapOf("port" to PORT.toString()),
                )
            }.getOrNull() ?: return@thread

            AppDiagnostics.breadcrumb(
                event = "deeplink.desktop.server_started",
                details = mapOf("port" to PORT.toString()),
            )

            server.use {
                while (!server.isClosed) {
                    runCatching {
                        server.accept().use(::handleClient)
                    }.onFailure { throwable ->
                        if (throwable !is SocketTimeoutException) {
                            AppDiagnostics.error(
                                event = "deeplink.desktop.server_client_failed",
                                throwable = throwable,
                                details = mapOf("port" to PORT.toString()),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        val line = socket.getInputStream()
            .bufferedReader(StandardCharsets.UTF_8)
            .readLine()
            ?.trim()
            .orEmpty()
        if (!line.startsWith("$PROTOCOL://", ignoreCase = true)) return

        AppDiagnostics.breadcrumb(
            event = "deeplink.desktop.received",
            details = mapOf("link" to line),
        )
        handleAppUrl(line)
    }

    private fun registerWindowsProtocolHandler() {
        if (!isWindows()) return

        val executable = currentExecutablePath() ?: return
        if (!executable.fileName.toString().endsWith(".exe", ignoreCase = true)) return
        if (executable.fileName.toString().equals("java.exe", ignoreCase = true)) return

        val writes = windowsProtocolRegistryWrites(executable)

        writes.forEach { write ->
            runCatching {
                val process = ProcessBuilder(write.executable, *write.args.toTypedArray())
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    AppDiagnostics.breadcrumb(
                        event = "deeplink.desktop.protocol_register_nonzero",
                        details = mapOf(
                            "step" to write.label,
                            "exitCode" to exitCode.toString(),
                            "output" to output.take(240),
                        ),
                    )
                } else {
                    AppDiagnostics.breadcrumb(
                        event = "deeplink.desktop.protocol_register_ok",
                        details = mapOf("step" to write.label),
                    )
                }
            }.onFailure { throwable ->
                AppDiagnostics.error(
                    event = "deeplink.desktop.protocol_register_failed",
                    throwable = throwable,
                    details = mapOf(
                        "step" to write.label,
                        "executable" to executable.toString(),
                    ),
                )
            }
        }
    }

    internal fun windowsProtocolRegistryWrites(executable: Path): List<RegistryWrite> {
        val command = "\"$executable\" \"%1\""
        return listOf(
            RegistryWrite(
                label = "root_default",
                executable = regExecutablePath(),
                args = listOf("add", "HKCU\\Software\\Classes\\$PROTOCOL", "/ve", "/t", "REG_SZ", "/d", "URL:Nuvio Protocol", "/f"),
            ),
            RegistryWrite(
                label = "url_protocol",
                executable = regExecutablePath(),
                args = listOf("add", "HKCU\\Software\\Classes\\$PROTOCOL", "/v", "URL Protocol", "/t", "REG_SZ", "/d", "", "/f"),
            ),
            RegistryWrite(
                label = "open_command",
                executable = "powershell.exe",
                args = listOf(
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    powershellSetDefaultRegistryValueScript(
                        keyPath = "Registry::HKEY_CURRENT_USER\\Software\\Classes\\$PROTOCOL\\shell\\open\\command",
                        value = command,
                    ),
                ),
            ),
        )
    }

    private fun powershellSetDefaultRegistryValueScript(
        keyPath: String,
        value: String,
    ): String =
        "\$key = ${keyPath.toPowerShellSingleQuotedString()}; " +
            "New-Item -Force -Path \$key | Out-Null; " +
            "Set-Item -Path \$key -Value ${value.toPowerShellSingleQuotedString()}"

    private fun String.toPowerShellSingleQuotedString(): String =
        "'" + replace("'", "''") + "'"

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("windows")

    private fun regExecutablePath(): String {
        val systemRoot = System.getenv("SystemRoot").orEmpty().ifBlank { "C:\\Windows" }
        val candidate = Path.of(systemRoot, "System32", "reg.exe")
        return if (Files.exists(candidate)) candidate.toString() else "reg.exe"
    }

    private fun currentExecutablePath(): Path? =
        ProcessHandle.current()
            .info()
            .command()
            .orElse(null)
            ?.let(Path::of)
            ?.takeIf { Files.exists(it) }
}
