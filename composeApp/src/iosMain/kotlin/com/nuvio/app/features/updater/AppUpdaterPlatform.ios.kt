package com.nuvio.app.features.updater

actual object AppUpdaterPlatform {
    actual val isSupported: Boolean = false
    actual val releaseOwner: String = "NuvioMedia"
    actual val releaseRepository: String = "NuvioMobile"
    actual val releaseChannel: String = "cmp-rewrite"

    actual fun getSupportedAbis(): List<String> = emptyList()

    actual fun getSupportedAssetExtensions(): List<String> = emptyList()

    actual fun getIgnoredTag(): String? = null

    actual fun setIgnoredTag(tag: String?) = Unit

    actual suspend fun downloadApk(
        assetUrl: String,
        assetName: String,
        checksumAssetUrl: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = Result.failure(IllegalStateException("In-app updates are unavailable on this build."))

    actual fun canRequestPackageInstalls(): Boolean = false

    actual fun openUnknownSourcesSettings() = Unit

    actual fun installDownloadedApk(path: String): Result<Unit> =
        Result.failure(IllegalStateException("In-app updates are unavailable on this build."))
}
