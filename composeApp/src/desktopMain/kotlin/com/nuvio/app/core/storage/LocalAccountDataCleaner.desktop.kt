package com.nuvio.app.core.storage

import com.nuvio.app.core.desktop.DesktopAppPaths
import java.nio.file.Files
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

internal actual object PlatformLocalAccountDataCleaner {
    actual fun wipe() {
        wipeDirectoryContents(DesktopAppPaths.appDataDir)
        wipeDirectoryContents(DesktopAppPaths.localDataDir)
    }

    private fun wipeDirectoryContents(root: java.nio.file.Path) {
        if (!root.exists()) {
            root.createDirectories()
            return
        }

        Files.walk(root).use { paths ->
            paths
                .sorted(Comparator.reverseOrder())
                .filter { path -> path != root }
                .forEach { path -> runCatching { Files.deleteIfExists(path) } }
        }
        root.createDirectories()
    }
}
