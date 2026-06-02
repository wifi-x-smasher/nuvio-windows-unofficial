package com.nuvio.app.core.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

internal object DesktopAppPaths {
    val appDataDir: Path by lazy {
        val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        val base = if (appData != null) {
            Path.of(appData)
        } else {
            Path.of(System.getProperty("user.home"), ".nuvio")
        }

        base.resolve("Nuvio").also { it.createDirectories() }
    }

    val downloadsDir: Path by lazy {
        Path.of(System.getProperty("user.home"), "Videos", "Nuvio Downloads")
            .also { Files.createDirectories(it) }
    }

    val localDataDir: Path by lazy {
        val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        val base = if (localAppData != null) {
            Path.of(localAppData)
        } else {
            Path.of(System.getProperty("java.io.tmpdir"))
        }

        base.resolve("Nuvio").also { it.createDirectories() }
    }

    fun dataFile(name: String): Path = appDataDir.resolve(name)
}
