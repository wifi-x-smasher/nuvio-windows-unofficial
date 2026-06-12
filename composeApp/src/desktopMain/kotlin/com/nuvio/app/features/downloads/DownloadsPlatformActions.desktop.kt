package com.nuvio.app.features.downloads

import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists

internal actual object DownloadsPlatformActions {
    actual fun openDownloadedFile(item: DownloadItem): Boolean =
        item.managedDownloadPath()
            ?.takeIf { it.exists() }
            ?.let { path ->
                runCatching {
                    Desktop.getDesktop().open(path.toFile())
                }.isSuccess
            }
            ?: false

    actual fun openContainingFolder(item: DownloadItem): Boolean =
        item.managedDownloadPath()
            ?.takeIf { it.exists() }
            ?.parent
            ?.let { folder ->
                runCatching {
                    Desktop.getDesktop().open(folder.toFile())
                }.isSuccess
            }
            ?: false

    private fun DownloadItem.managedDownloadPath(): Path? {
        val fromUri = localFileUri
            ?.let { uri -> runCatching { URI(uri) }.getOrNull() }
            ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
            ?.let { uri -> runCatching { Path.of(uri).toAbsolutePath().normalize() }.getOrNull() }
            ?.takeIf(DesktopDownloadFiles::isManagedDownloadPath)
            ?.takeIf { it.exists() }
        if (fromUri != null) return fromUri

        return DesktopDownloadFiles.targetFile(fileName)
            .takeIf { it.exists() }
    }
}
