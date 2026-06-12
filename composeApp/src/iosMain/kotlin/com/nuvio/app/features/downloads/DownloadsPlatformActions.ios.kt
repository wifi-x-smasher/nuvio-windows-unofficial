package com.nuvio.app.features.downloads

internal actual object DownloadsPlatformActions {
    actual fun openDownloadedFile(item: DownloadItem): Boolean = false
    actual fun openContainingFolder(item: DownloadItem): Boolean = false
}
