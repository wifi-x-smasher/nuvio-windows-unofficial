package com.nuvio.app.features.player

import com.nuvio.app.core.diagnostics.AppDiagnostics
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

internal actual object InternalPlayerPlatform {
    actual fun isAvailable(): Boolean =
        DesktopVlcRuntime.compatibleRuntimeDirectory() != null

    actual fun unavailableMessage(): String? =
        if (isAvailable()) {
            null
        } else {
            "Internal player is unavailable because a compatible 64-bit VLC runtime was not found."
        }
}

internal object DesktopVlcRuntime {
    private const val jnaLibraryPathProperty = "jna.library.path"
    private const val jnaNoSysProperty = "jna.nosys"
    private const val composeResourcesDirProperty = "compose.application.resources.dir"

    fun compatibleRuntimeDirectory(): Path? =
        compatibleRuntimeDirectory(
            env = System.getenv(),
            property = System.getProperty(jnaLibraryPathProperty),
            appResourcesDir = System.getProperty(composeResourcesDirProperty),
            executablePath = currentExecutablePath(),
            exists = { path -> path.isDirectory() },
            hasLibrary = { path -> path.resolve("libvlc.dll").isRegularFile() },
        )

    fun prepare(): Result<Path> {
        AppDiagnostics.breadcrumb(
            event = "player.vlc.runtime.prepare.start",
            details = mapOf(
                "jnaLibraryPathSet" to (!System.getProperty(jnaLibraryPathProperty).isNullOrBlank()).toString(),
                "appResourcesDirSet" to (!System.getProperty(composeResourcesDirProperty).isNullOrBlank()).toString(),
                "vlcHomeSet" to (!System.getenv("VLC_HOME").isNullOrBlank()).toString(),
            ),
        )
        val runtimeDirectory = compatibleRuntimeDirectory()
            ?: run {
                AppDiagnostics.error(
                    event = "player.vlc.runtime.prepare.missing",
                    throwable = null,
                    details = mapOf(
                        "programFiles" to System.getenv("ProgramFiles"),
                        "vlcHomeSet" to (!System.getenv("VLC_HOME").isNullOrBlank()).toString(),
                    ),
                )
                return Result.failure(
                IllegalStateException(
                    "Compatible 64-bit VLC runtime not found. Install 64-bit VLC or use an external player.",
                ),
            )
            }
        val current = System.getProperty(jnaLibraryPathProperty).orEmpty()
        val runtimePath = runtimeDirectory.toString()
        val paths = current
            .split(File.pathSeparatorChar)
            .filter(String::isNotBlank)
        if (runtimePath !in paths) {
            System.setProperty(
                jnaLibraryPathProperty,
                (listOf(runtimePath) + paths).joinToString(File.pathSeparator),
            )
        }
        System.setProperty(jnaNoSysProperty, "true")
        AppDiagnostics.breadcrumb(
            event = "player.vlc.runtime.prepare.success",
            details = mapOf("runtimeDirectory" to runtimePath),
        )
        return Result.success(runtimeDirectory)
    }

    internal fun compatibleRuntimeDirectory(
        env: Map<String, String>,
        property: String?,
        appResourcesDir: String? = null,
        executablePath: Path? = null,
        exists: (Path) -> Boolean,
        hasLibrary: (Path) -> Boolean,
    ): Path? =
        runtimeCandidates(env, property, appResourcesDir, executablePath).firstOrNull { path ->
            runCatching {
                exists(path) && hasLibrary(path) && !path.isKnown32BitProgramFilesPath()
            }.getOrDefault(false)
        }

    private fun runtimeCandidates(
        env: Map<String, String>,
        property: String?,
        appResourcesDir: String?,
        executablePath: Path?,
    ): List<Path> =
        buildList {
            appResourcesDir
                ?.takeIf(String::isNotBlank)
                ?.let { add(Path.of(it, "vlc")) }
            executablePath
                ?.parent
                ?.let { appDir ->
                    add(appDir.resolve("app").resolve("vlc"))
                    add(appDir.resolve("app").resolve("resources").resolve("vlc"))
                    add(appDir.resolve("resources").resolve("vlc"))
                    add(appDir.resolve("vlc"))
                }
            property
                ?.split(File.pathSeparatorChar)
                ?.mapNotNull { it.takeIf(String::isNotBlank)?.let(Path::of) }
                ?.let(::addAll)
            env["VLC_HOME"]?.let { add(Path.of(it)) }
            env["ProgramFiles"]?.let { add(Path.of(it, "VideoLAN", "VLC")) }
            env["PROGRAMFILES"]?.let { add(Path.of(it, "VideoLAN", "VLC")) }
        }.distinct()

    private fun Path.isKnown32BitProgramFilesPath(): Boolean =
        toString().contains("Program Files (x86)", ignoreCase = true)

    private fun currentExecutablePath(): Path? =
        ProcessHandle.current()
            .info()
            .command()
            .orElse(null)
            ?.let(Path::of)
}
