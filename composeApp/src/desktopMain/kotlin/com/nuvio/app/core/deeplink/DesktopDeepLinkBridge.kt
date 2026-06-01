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

        val command = "\"$executable\" \"%1\""
        val writes = listOf(
            listOf("reg", "add", "HKCU\\Software\\Classes\\$PROTOCOL", "/ve", "/d", "URL:Nuvio Protocol", "/f"),
            listOf("reg", "add", "HKCU\\Software\\Classes\\$PROTOCOL", "/v", "URL Protocol", "/d", "", "/f"),
            listOf("reg", "add", "HKCU\\Software\\Classes\\$PROTOCOL\\shell\\open\\command", "/ve", "/d", command, "/f"),
        )

        writes.forEach { args ->
            runCatching {
                val exitCode = ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
                if (exitCode != 0) {
                    AppDiagnostics.breadcrumb(
                        event = "deeplink.desktop.protocol_register_nonzero",
                        details = mapOf("exitCode" to exitCode.toString()),
                    )
                }
            }.onFailure { throwable ->
                AppDiagnostics.error(
                    event = "deeplink.desktop.protocol_register_failed",
                    throwable = throwable,
                    details = mapOf("executable" to executable.toString()),
                )
            }
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("windows")

    private fun currentExecutablePath(): Path? =
        ProcessHandle.current()
            .info()
            .command()
            .orElse(null)
            ?.let(Path::of)
            ?.takeIf { Files.exists(it) }
}
