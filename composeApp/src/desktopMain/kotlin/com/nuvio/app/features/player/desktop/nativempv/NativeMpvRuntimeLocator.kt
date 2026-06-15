package com.nuvio.app.features.player.desktop.nativempv

import java.io.File
import java.nio.file.Path

internal data class NativeMpvRuntimeResolution(
    val executable: Path?,
    val checkedPaths: List<String>,
) {
    val available: Boolean
        get() = executable?.toFile()?.isFile == true

    val diagnostics: String
        get() = "selected=${executable?.toAbsolutePath() ?: "none"} checked=${checkedPaths.joinToString(" | ")}"
}

internal object NativeMpvRuntimeLocator {
    fun resolve(): NativeMpvRuntimeResolution {
        val candidates = linkedMapOf<String, File>()
        fun add(label: String, file: File?) {
            if (file != null) candidates.putIfAbsent(label, file)
        }

        val resourcesDir = System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        val appDir = resourcesDir?.parentFile
        add("resourcesDir/mpv/mpv.exe", resourcesDir?.resolve("mpv/mpv.exe"))
        add("appDir/resources/mpv/mpv.exe", appDir?.resolve("resources/mpv/mpv.exe"))
        add("appDir/mpv/mpv.exe", appDir?.resolve("mpv/mpv.exe"))

        currentExecutableDirectory()?.let { exeDir ->
            add("exeDir/resources/mpv/mpv.exe", exeDir.resolve("resources/mpv/mpv.exe"))
            add("exeDir/app/resources/mpv/mpv.exe", exeDir.resolve("app/resources/mpv/mpv.exe"))
            add("exeDir/mpv/mpv.exe", exeDir.resolve("mpv/mpv.exe"))
        }

        add("env:NUVIO_NATIVE_MPV_EXE", System.getenv("NUVIO_NATIVE_MPV_EXE")?.toFileOrNull())
        add("property:nuvio.native.mpv.exe", System.getProperty("nuvio.native.mpv.exe")?.toFileOrNull())

        if (devLookupEnabled()) {
            System.getProperty("user.dir")?.takeIf { it.isNotBlank() }?.let { userDir ->
                val base = File(userDir)
                add("dev:compose resources", base.resolve("composeApp/src/desktopMain/resources/mpv/mpv.exe"))
                add("dev:processed resources", base.resolve("composeApp/build/processedResources/desktop/main/mpv/mpv.exe"))
                add("dev:runtime resources", base.resolve("composeApp/build/desktop-runtime-resources/mpv/mpv.exe"))
                add("dev:vendor mpv", base.resolve("vendor/mediamp-nuvio/mediamp-mpv/libmpv/lib/windows/x86_64/mpv.exe"))
            }
        }

        val checked = candidates.map { (label, file) ->
            "$label=${file.absolutePath.replace("\\", "/")} exists=${file.isFile}"
        }
        val selected = candidates.values.firstOrNull { it.isFile }
        return NativeMpvRuntimeResolution(
            executable = selected?.toPath(),
            checkedPaths = checked,
        )
    }

    private fun devLookupEnabled(): Boolean =
        System.getenv("NUVIO_DEV_PLAYER_LOOKUP").equals("true", ignoreCase = true) ||
            System.getProperty("nuvio.dev.player.lookup").equals("true", ignoreCase = true)

    private fun currentExecutableDirectory(): File? =
        ProcessHandle.current()
            .info()
            .command()
            .orElse(null)
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.parentFile

    private fun String.toFileOrNull(): File? =
        takeIf { it.isNotBlank() }?.let(::File)
}
