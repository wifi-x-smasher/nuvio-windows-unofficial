package com.nuvio.app.features.downloads

import com.nuvio.app.core.desktop.DesktopAppPaths
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import kotlin.concurrent.thread
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

internal actual object DownloadsPlatformDownloader {
    actual fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
    ): DownloadsTaskHandle {
        val target = DesktopDownloadFiles.targetFile(request.destinationFileName)
        val partial = DesktopDownloadFiles.partialFile(request.destinationFileName)
        val handle = ThreadDownloadTaskHandle()
        val worker = thread(name = "nuvio-download-${target.fileName}", isDaemon = true) {
            runCatching {
                Files.createDirectories(target.parent)
                val existingBytes = partial.takeIf { it.exists() }?.let(Files::size)?.coerceAtLeast(0L) ?: 0L
                val connection = (URI(request.sourceUrl).toURL().openConnection() as HttpURLConnection).apply {
                    request.sourceHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
                    if (existingBytes > 0L) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }

                val responseCode = connection.responseCode
                val canAppend = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
                if (existingBytes > 0L && !canAppend) {
                    partial.deleteIfExists()
                }
                val startingBytes = if (canAppend) existingBytes else 0L
                val reportedLength = connection.contentLengthLong.takeIf { it >= 0 }
                val totalBytes = reportedLength?.let { length ->
                    if (canAppend) startingBytes + length else length
                }

                connection.inputStream.use { input ->
                    Files.newOutputStream(
                        partial,
                        CREATE,
                        if (canAppend) APPEND else TRUNCATE_EXISTING,
                    ).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = startingBytes
                        if (downloaded > 0L) onProgress(downloaded, totalBytes)
                        while (!handle.cancelled) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, totalBytes)
                        }
                        if (handle.cancelled) {
                            return@thread
                        }
                    }
                }

                moveCompletedPartial(partial, target)
                onSuccess(target.toUri().toString(), totalBytes)
            }.onFailure { error ->
                if (partial.exists() && runCatching { Files.size(partial) == 0L }.getOrDefault(false)) {
                    partial.deleteIfExists()
                }
                onFailure(error.message ?: "Download failed")
            }
        }
        handle.worker = worker
        return handle
    }

    actual fun removeFile(localFileUri: String?): Boolean =
        DesktopDownloadFiles.managedPathFromUri(localFileUri)
            ?.let { path -> runCatching { path.deleteIfExists() }.getOrDefault(false) }
            ?: false

    actual fun removePartialFile(destinationFileName: String): Boolean =
        runCatching { DesktopDownloadFiles.partialFile(destinationFileName).deleteIfExists() }
            .getOrDefault(false)

    actual fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String? {
        val existing = DesktopDownloadFiles.managedPathFromUri(localFileUri)
            ?.takeIf { it.exists() }
        return existing?.toUri()?.toString()
            ?: DesktopDownloadFiles.targetFile(destinationFileName)
                .takeIf { it.exists() }
                ?.toUri()
                ?.toString()
    }

    private fun moveCompletedPartial(partial: Path, target: Path) {
        runCatching {
            Files.move(partial, target, REPLACE_EXISTING, ATOMIC_MOVE)
        }.getOrElse {
            Files.move(partial, target, REPLACE_EXISTING)
        }
    }
}

internal object DesktopDownloadFiles {
    fun targetFile(destinationFileName: String): Path =
        DesktopAppPaths.downloadsDir.resolve(sanitizeFileName(destinationFileName)).normalize()

    fun partialFile(destinationFileName: String): Path =
        targetFile(destinationFileName).resolveSibling("${targetFile(destinationFileName).fileName}.part")

    fun managedPathFromUri(localFileUri: String?): Path? {
        val uri = runCatching { localFileUri?.let(::URI) }.getOrNull() ?: return null
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        val path = runCatching { Path.of(uri).toAbsolutePath().normalize() }.getOrNull() ?: return null
        return path.takeIf(::isManagedDownloadPath)
    }

    fun isManagedDownloadPath(path: Path): Boolean {
        val normalizedRoot = DesktopAppPaths.downloadsDir.toAbsolutePath().normalize()
        val normalizedPath = path.toAbsolutePath().normalize()
        return normalizedPath != normalizedRoot && normalizedPath.startsWith(normalizedRoot)
    }

    fun sanitizeFileName(value: String): String {
        val sanitized = value
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "download.bin" }
            .take(140)
        return sanitized.takeUnless { it == "." || it == ".." } ?: "download.bin"
    }
}

private class ThreadDownloadTaskHandle : DownloadsTaskHandle {
    @Volatile
    var cancelled: Boolean = false
    var worker: Thread? = null

    override fun cancel() {
        cancelled = true
        worker?.interrupt()
    }
}
