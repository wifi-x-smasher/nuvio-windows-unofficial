package com.nuvio.app.features.player

import com.nuvio.app.core.diagnostics.AppDiagnostics
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeBootstrap
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeBootstrapResult
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeLocator
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeResolution
import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal actual object InternalPlayerPlatform {
    actual fun isAvailable(): Boolean =
        DesktopMpvRuntime.isNativeRuntimeAvailable()

    actual fun unavailableMessage(): String? =
        if (isAvailable()) {
            null
        } else {
            "Internal player is unavailable because the bundled MPV runtime could not be loaded. Use an external player or send diagnostics logs."
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

    fun isNativeRuntimeAvailable(): Boolean =
        isNativeRuntimeAvailable(
            resolve = MpvRuntimeLocator::resolve,
            bootstrap = MpvRuntimeBootstrap::apply,
            logUnavailable = { diagnostics ->
                AppDiagnostics.breadcrumb(
                    event = "player.mpv.native_runtime_unavailable",
                    details = mapOf("diagnostics" to diagnostics.take(2_000)),
                )
            },
        )

    internal fun isNativeRuntimeAvailable(
        resolve: () -> MpvRuntimeResolution,
        bootstrap: (MpvRuntimeResolution) -> MpvRuntimeBootstrapResult,
        logUnavailable: (String) -> Unit = {},
    ): Boolean {
        val runtime = resolve()
        if (!runtime.available) {
            logUnavailable("MPV native runtime files unavailable. ${runtime.diagnostics}")
            return false
        }
        val bootstrapResult = bootstrap(runtime)
        if (!bootstrapResult.success) {
            logUnavailable(bootstrapResult.diagnostics)
        }
        return bootstrapResult.success
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
