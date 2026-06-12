package com.nuvio.app.features.updater

import com.nuvio.app.core.desktop.DesktopPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.deleteIfExists
import kotlin.system.exitProcess

actual object AppUpdaterPlatform {
    private const val ignoredTagKey = "ignored_release_tag"
    private val preferences = DesktopPreferences("nuvio_updater")

    actual val isSupported: Boolean = true
    actual val releaseOwner: String = "wifi-x-smasher"
    actual val releaseRepository: String = "nuvio-windows-unofficial"
    actual val releaseChannel: String = "main"

    actual fun getSupportedAbis(): List<String> = listOf("windows", "win", "x64", "desktop")

    actual fun getSupportedAssetExtensions(): List<String> = listOf("msi")

    actual fun getIgnoredTag(): String? =
        preferences.getString(ignoredTagKey)

    actual fun setIgnoredTag(tag: String?) {
        preferences.putString(ignoredTagKey, tag)
    }

    actual suspend fun downloadApk(
        assetUrl: String,
        assetName: String,
        checksumAssetUrl: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = sanitizeAssetName(assetName)
            val destination = updatesDir().resolve(safeName)
            destination.deleteIfExists()
            Files.createDirectories(destination.parent)

            downloadFile(assetUrl, destination, onProgress)

            val expectedChecksum = checksumAssetUrl
                ?.let(::downloadText)
                ?.let(::extractSha256)
            if (expectedChecksum != null) {
                val actualChecksum = sha256Hex(destination)
                check(actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
                    "Update checksum mismatch."
                }
            }

            destination.toAbsolutePath().toString()
        }
    }

    actual fun canRequestPackageInstalls(): Boolean = true

    actual fun openUnknownSourcesSettings() = Unit

    actual fun installDownloadedApk(path: String): Result<Unit> = runCatching {
        val installer = Path.of(path).toAbsolutePath().normalize()
        check(Files.isRegularFile(installer)) { "Downloaded update file is missing." }
        check(isSupportedInstaller(installer)) { "Downloaded update file is not a Windows installer." }

        ProcessBuilder(windowsInstallerCommand(installer))
            .directory(installer.parent.toFile())
            .start()
        exitProcess(0)
    }.map { Unit }

    private fun updatesDir(): Path =
        Path.of(System.getProperty("java.io.tmpdir"), "Nuvio", "updates").also(Files::createDirectories)

    private fun sanitizeAssetName(assetName: String): String =
        assetName
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeIf(String::isNotBlank)
            ?: "nuvio-update.msi"

    private fun isSupportedInstaller(path: Path): Boolean {
        val name = path.fileName.toString()
        return getSupportedAssetExtensions().any { extension -> name.endsWith(".$extension", ignoreCase = true) }
    }

    internal fun windowsInstallerCommand(installer: Path): List<String> =
        listOf("msiexec.exe", "/i", installer.toString())

    private fun downloadFile(
        assetUrl: String,
        destination: Path,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        val connection = openConnection(assetUrl)
        val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
        connection.inputStream.use { input ->
            Files.newOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    onProgress(downloadedBytes, totalBytes)
                }
                output.flush()
            }
        }
    }

    private fun downloadText(assetUrl: String): String =
        openConnection(assetUrl).inputStream.bufferedReader().use { it.readText() }

    private fun openConnection(assetUrl: String): HttpURLConnection =
        (URI(assetUrl).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 60_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NuvioWindows")
            val status = responseCode
            if (status !in 200..299) {
                error("Download failed with HTTP $status")
            }
        }

    private fun extractSha256(payload: String): String? =
        Regex("\\b[a-fA-F0-9]{64}\\b").find(payload)?.value

    private fun sha256Hex(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
