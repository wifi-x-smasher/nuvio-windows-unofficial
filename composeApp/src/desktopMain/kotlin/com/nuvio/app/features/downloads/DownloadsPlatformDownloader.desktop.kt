package com.nuvio.app.features.downloads

import com.nuvio.app.core.desktop.DesktopAppPaths
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
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
        val target = DesktopAppPaths.downloadsDir.resolve(sanitizeFileName(request.destinationFileName))
        val handle = ThreadDownloadTaskHandle()
        val worker = thread(name = "nuvio-download-${target.fileName}", isDaemon = true) {
            runCatching {
                val connection = URI(request.sourceUrl).toURL().openConnection() as HttpURLConnection
                request.sourceHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                val totalBytes = connection.contentLengthLong.takeIf { it >= 0 }
                connection.inputStream.use { input ->
                    Files.newOutputStream(target).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (!handle.cancelled) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, totalBytes)
                        }
                        if (handle.cancelled) {
                            target.deleteIfExists()
                            return@thread
                        }
                        onSuccess(target.toUri().toString(), totalBytes)
                    }
                }
            }.onFailure { error ->
                target.deleteIfExists()
                onFailure(error.message ?: "Download failed")
            }
        }
        handle.worker = worker
        return handle
    }

    actual fun removeFile(localFileUri: String?): Boolean =
        runCatching {
            localFileUri?.let { URI(it) }?.let { PathCompat.fromUri(it) }?.deleteIfExists() == true
        }.getOrDefault(false)

    actual fun removePartialFile(destinationFileName: String): Boolean =
        DesktopAppPaths.downloadsDir.resolve(sanitizeFileName(destinationFileName)).deleteIfExists()

    actual fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String? {
        val existing = runCatching {
            localFileUri?.let { URI(it) }?.let(PathCompat::fromUri)?.takeIf { it.exists() }
        }.getOrNull()
        return existing?.toUri()?.toString()
            ?: DesktopAppPaths.downloadsDir.resolve(sanitizeFileName(destinationFileName))
                .takeIf { it.exists() }
                ?.toUri()
                ?.toString()
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download.bin" }
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

private object PathCompat {
    fun fromUri(uri: URI) = java.nio.file.Path.of(uri)
}
