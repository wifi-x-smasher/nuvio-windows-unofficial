package com.nuvio.app.features.updater

actual object AppUpdaterPlatform {
    actual val isSupported: Boolean = true
    actual val releaseOwner: String = "NuvioMedia"
    actual val releaseRepository: String = "NuvioMobile"
    actual val releaseChannel: String = "cmp-rewrite"

    actual fun getSupportedAbis(): List<String> = AndroidAppUpdaterPlatform.getSupportedAbis()

    actual fun getSupportedAssetExtensions(): List<String> = listOf("apk")

    actual fun getIgnoredTag(): String? = AndroidAppUpdaterPlatform.getIgnoredTag()

    actual fun setIgnoredTag(tag: String?) {
        AndroidAppUpdaterPlatform.setIgnoredTag(tag)
    }

    actual suspend fun downloadApk(
        assetUrl: String,
        assetName: String,
        checksumAssetUrl: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = AndroidAppUpdaterPlatform.downloadApk(assetUrl, assetName, onProgress)

    actual fun canRequestPackageInstalls(): Boolean = AndroidAppUpdaterPlatform.canRequestPackageInstalls()

    actual fun openUnknownSourcesSettings() {
        AndroidAppUpdaterPlatform.openUnknownSourcesSettings()
    }

    actual fun installDownloadedApk(path: String): Result<Unit> = AndroidAppUpdaterPlatform.installDownloadedApk(path)
}
