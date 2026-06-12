package com.nuvio.app.features.downloads

internal expect object DownloadsPlatformActions {
    fun openDownloadedFile(item: DownloadItem): Boolean
    fun openContainingFolder(item: DownloadItem): Boolean
}
