package com.nuvio.app.features.player

import com.nuvio.app.core.diagnostics.AppDiagnostics
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeBootstrap
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeLocator
import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal actual object InternalPlayerPlatform {
    actual fun isAvailable(): Boolean =
        DesktopMpvRuntime.isNativeRuntimeAvailable() || DesktopMpvRuntime.executablePath() != null

    actual fun unavailableMessage(): String? =
        if (isAvailable()) {
            null
        } else {
            "Internal player is unavailable because MPV was not found. Install mpv or use an external player."
        }
}

internal object DesktopMpvRuntime {
    private const val composeResourcesDirProperty = "compose.application.resources.dir"

    fun executablePath(): Path? =
        executablePath(
            env = System.getenv(),
            appResourcesDir = System.getProperty(composeResourcesDirProperty),
            executablePath = currentExecutablePath(),
            exists = { it.isRegularFile() },
        )

    fun isNativeRuntimeAvailable(): Boolean {
        val runtime = MpvRuntimeLocator.resolve()
        val bootstrap = MpvRuntimeBootstrap.apply(runtime)
        if (!bootstrap.success) {
            AppDiagnostics.breadcrumb(
                event = "player.mpv.native_runtime_unavailable",
                details = mapOf("diagnostics" to bootstrap.diagnostics.take(500)),
            )
        }
        return bootstrap.success
    }

    internal fun executablePath(
        env: Map<String, String>,
        appResourcesDir: String? = null,
        executablePath: Path? = null,
        exists: (Path) -> Boolean,
    ): Path? =
        runtimeCandidates(env, appResourcesDir, executablePath)
            .firstOrNull { path -> runCatching { exists(path) }.getOrDefault(false) }
            ?.also { path ->
                AppDiagnostics.breadcrumb(
                    event = "player.mpv.runtime.found",
                    details = mapOf("path" to path.toString()),
                )
            }

    private fun runtimeCandidates(
        env: Map<String, String>,
        appResourcesDir: String?,
        executablePath: Path?,
    ): List<Path> =
        buildList {
            appResourcesDir
                ?.takeIf(String::isNotBlank)
                ?.let {
                    add(Path.of(it, "mpv", "mpv.exe"))
                    add(Path.of(it, "mpv.exe"))
                }
            executablePath
                ?.parent
                ?.let { appDir ->
                    add(appDir.resolve("app").resolve("mpv").resolve("mpv.exe"))
                    add(appDir.resolve("app").resolve("resources").resolve("mpv").resolve("mpv.exe"))
                    add(appDir.resolve("resources").resolve("mpv").resolve("mpv.exe"))
                    add(appDir.resolve("mpv").resolve("mpv.exe"))
                    add(appDir.resolve("mpv.exe"))
                }
            env["LOCALAPPDATA"]?.let {
                add(Path.of(it, "Programs", "mpv", "mpv.exe"))
                add(Path.of(it, "mpv", "mpv.exe"))
            }
            env["ProgramFiles"]?.let { add(Path.of(it, "mpv", "mpv.exe")) }
            env["PROGRAMFILES"]?.let { add(Path.of(it, "mpv", "mpv.exe")) }
            env["ProgramFiles(x86)"]?.let { add(Path.of(it, "mpv", "mpv.exe")) }
            env["PROGRAMFILES(X86)"]?.let { add(Path.of(it, "mpv", "mpv.exe")) }
        }.distinct()

    private fun currentExecutablePath(): Path? =
        ProcessHandle.current()
            .info()
            .command()
            .orElse(null)
            ?.let(Path::of)
}
