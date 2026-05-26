package com.nuvio.app.features.updater

expect object AppUpdaterPlatform {
    val isSupported: Boolean
    val releaseOwner: String
    val releaseRepository: String
    val releaseChannel: String

    fun getSupportedAbis(): List<String>

    fun getSupportedAssetExtensions(): List<String>

    fun getIgnoredTag(): String?

    fun setIgnoredTag(tag: String?)

    suspend fun downloadApk(
        assetUrl: String,
        assetName: String,
        checksumAssetUrl: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String>

    fun canRequestPackageInstalls(): Boolean

    fun openUnknownSourcesSettings()

    fun installDownloadedApk(path: String): Result<Unit>
}
