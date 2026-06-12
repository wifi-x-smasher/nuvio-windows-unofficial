package com.nuvio.app.features.downloads

import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

object DownloadsRepository {
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val activeHandles = mutableMapOf<String, DownloadsTaskHandle>()
    private val progressSamples = mutableMapOf<String, DownloadProgressSample>()
    private var hasLoaded = false
    private var nextDownloadOrdinal = 0L

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        activeHandles.values.forEach(DownloadsTaskHandle::cancel)
        activeHandles.clear()
        progressSamples.clear()
        hasLoaded = false
        _uiState.value = DownloadsUiState()
        notifyLiveStatusPlatform()
    }

    fun findPlayableDownloadByVideoId(videoId: String?): DownloadItem? {
        ensureLoaded()
        val normalizedVideoId = videoId?.trim().orEmpty()
        if (normalizedVideoId.isBlank()) return null
        return _uiState.value.items.firstOrNull { item ->
            item.videoId == normalizedVideoId && item.hasPlayableLocalFile()
        }
    }

    fun findPlayableDownload(
        parentMetaId: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        videoId: String? = null,
    ): DownloadItem? {
        ensureLoaded()
        val items = _uiState.value.items
        val normalizedParentMetaId = parentMetaId.trim()

        findPlayableDownloadByVideoId(videoId)?.let { return it }

        return if (seasonNumber != null && episodeNumber != null) {
            items.firstOrNull { item ->
                item.parentMetaId == normalizedParentMetaId &&
                    item.seasonNumber == seasonNumber &&
                    item.episodeNumber == episodeNumber &&
                    item.hasPlayableLocalFile()
            }
        } else {
            items.firstOrNull { item ->
                item.parentMetaId == normalizedParentMetaId &&
                    item.seasonNumber == null &&
                    item.episodeNumber == null &&
                    item.hasPlayableLocalFile()
            }
        }
    }

    fun playableLocalFileUri(item: DownloadItem): String? {
        ensureLoaded()
        if (item.status != DownloadStatus.Completed) return null
        val resolvedUri = DownloadsPlatformDownloader.resolveLocalFileUri(
            localFileUri = item.localFileUri,
            destinationFileName = item.fileName,
        ) ?: return null

        if (resolvedUri != item.localFileUri) {
            mutateItem(item.id) { current ->
                if (current.fileName == item.fileName) {
                    current.copy(
                        localFileUri = resolvedUri,
                        updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                    )
                } else {
                    current
                }
            }
        }

        return resolvedUri
    }

    fun enqueueFromStream(
        contentType: String,
        videoId: String,
        parentMetaId: String,
        parentMetaType: String,
        title: String,
        logo: String?,
        poster: String?,
        background: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        episodeTitle: String?,
        episodeThumbnail: String?,
        stream: StreamItem,
    ): DownloadEnqueueResult {
        ensureLoaded()

        val sourceUrl = stream.playableDirectUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return DownloadEnqueueResult.MissingUrl

        if (!sourceUrl.isSupportedDownloadUrl()) {
            return DownloadEnqueueResult.UnsupportedFormat
        }

        val now = DownloadsClock.nowEpochMs()
        val logicalKey = buildLogicalKey(
            parentMetaId = parentMetaId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )

        var replacedExisting = false
        val currentItems = _uiState.value.items.toMutableList()
        val existing = currentItems.firstOrNull { it.logicalContentKey == logicalKey }
        if (existing != null) {
            replacedExisting = true
            activeHandles.remove(existing.id)?.cancel()
            DownloadsPlatformDownloader.removeFile(playableLocalFileUri(existing) ?: existing.localFileUri)
            DownloadsPlatformDownloader.removePartialFile(existing.fileName)
            currentItems.removeAll { it.id == existing.id }
        }

        val downloadId = nextDownloadId(now)
        val fileName = buildFileName(
            title = title,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            fallbackTitle = stream.streamLabel,
            sourceUrl = sourceUrl,
            nowEpochMs = now,
        )

        val item = DownloadItem(
            id = downloadId,
            contentType = contentType,
            parentMetaId = parentMetaId,
            parentMetaType = parentMetaType,
            videoId = videoId,
            title = title,
            logo = logo,
            poster = poster,
            background = background,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            episodeThumbnail = episodeThumbnail,
            streamTitle = stream.streamLabel,
            streamSubtitle = stream.streamSubtitle,
            providerName = stream.addonName,
            providerAddonId = stream.addonId,
            sourceUrl = sourceUrl,
            sourceHeaders = sanitizeRequestHeaders(stream.behaviorHints.proxyHeaders?.request),
            sourceResponseHeaders = sanitizeResponseHeaders(stream.behaviorHints.proxyHeaders?.response),
            localFileUri = null,
            fileName = fileName,
            status = DownloadStatus.Queued,
            downloadedBytes = 0L,
            totalBytes = null,
            bytesPerSecond = null,
            etaSeconds = null,
            errorMessage = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )

        currentItems.add(0, item)
        publish(currentItems)
        persist()
        scheduleQueuedDownloads()

        return if (replacedExisting) {
            DownloadEnqueueResult.Replaced
        } else {
            DownloadEnqueueResult.Started
        }
    }

    fun pauseDownload(downloadId: String) {
        ensureLoaded()
        val item = _uiState.value.items.firstOrNull { it.id == downloadId } ?: return
        if (item.status != DownloadStatus.Downloading && item.status != DownloadStatus.Queued) return

        activeHandles.remove(downloadId)?.cancel()
        progressSamples.remove(downloadId)
        mutateItem(downloadId) { current ->
            current.copy(
                status = DownloadStatus.Paused,
                bytesPerSecond = null,
                etaSeconds = null,
                updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                errorMessage = null,
            )
        }
        scheduleQueuedDownloads()
    }

    fun pauseActiveDownloads() {
        ensureLoaded()
        _uiState.value.items
            .filter { it.status == DownloadStatus.Downloading }
            .map { it.id }
            .forEach(::pauseDownload)
    }

    fun resumeDownload(downloadId: String) {
        ensureLoaded()
        val item = _uiState.value.items.firstOrNull { it.id == downloadId } ?: return
        if (item.status != DownloadStatus.Paused && item.status != DownloadStatus.Failed) return

        val reset = item.copy(
            status = DownloadStatus.Queued,
            errorMessage = null,
            localFileUri = null,
            bytesPerSecond = null,
            etaSeconds = null,
            updatedAtEpochMs = DownloadsClock.nowEpochMs(),
        )

        replaceItem(reset)
        persist()
        scheduleQueuedDownloads()
    }

    fun retryDownload(downloadId: String) {
        resumeDownload(downloadId)
    }

    fun cancelDownload(downloadId: String) {
        ensureLoaded()
        val item = _uiState.value.items.firstOrNull { it.id == downloadId } ?: return

        activeHandles.remove(downloadId)?.cancel()
        progressSamples.remove(downloadId)
        DownloadsPlatformDownloader.removeFile(playableLocalFileUri(item) ?: item.localFileUri)
        DownloadsPlatformDownloader.removePartialFile(item.fileName)

        publish(_uiState.value.items.filterNot { it.id == downloadId })
        persist()
        scheduleQueuedDownloads()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val payload = DownloadsStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) {
            _uiState.value = DownloadsUiState()
            notifyLiveStatusPlatform()
            return
        }

        var shouldPersistNormalized = false
        val normalized = DownloadsCodec.decodeItems(payload)
            .map { item ->
                val statusNormalized = if (item.status == DownloadStatus.Downloading) {
                    item.copy(
                        status = DownloadStatus.Paused,
                        bytesPerSecond = null,
                        etaSeconds = null,
                        errorMessage = null,
                    )
                } else if (item.status == DownloadStatus.Queued) {
                    item.copy(
                        bytesPerSecond = null,
                        etaSeconds = null,
                        errorMessage = null,
                    )
                } else {
                    item
                }

                val localUriNormalized = normalizeCompletedLocalFileUri(statusNormalized)
                if (localUriNormalized != item) {
                    shouldPersistNormalized = true
                }
                localUriNormalized
            }

        _uiState.value = DownloadsUiState(normalized)
        notifyLiveStatusPlatform()
        if (shouldPersistNormalized) {
            persist()
        }
        scheduleQueuedDownloads()
    }

    private fun startDownload(item: DownloadItem) {
        val latest = _uiState.value.items.firstOrNull { it.id == item.id } ?: return
        if (latest.status != DownloadStatus.Queued) return

        val startTime = DownloadsClock.nowEpochMs()
        progressSamples.remove(item.id)
        replaceItem(
            latest.copy(
                status = DownloadStatus.Downloading,
                bytesPerSecond = null,
                etaSeconds = null,
                errorMessage = null,
                updatedAtEpochMs = startTime,
            ),
        )
        persist()

        val request = DownloadPlatformRequest(
            sourceUrl = latest.sourceUrl,
            sourceHeaders = latest.sourceHeaders,
            destinationFileName = latest.fileName,
        )

        val handle = DownloadsPlatformDownloader.start(
            request = request,
            onProgress = { downloadedBytes, totalBytes ->
                val now = DownloadsClock.nowEpochMs()
                val safeDownloadedBytes = downloadedBytes.coerceAtLeast(0L)
                val normalizedTotalBytes = totalBytes?.takeIf { it > 0L }
                val stats = DownloadTransferStats.calculate(
                    previous = progressSamples[item.id],
                    currentDownloadedBytes = safeDownloadedBytes,
                    currentEpochMs = now,
                    totalBytes = normalizedTotalBytes,
                )
                progressSamples[item.id] = DownloadProgressSample(
                    downloadedBytes = safeDownloadedBytes,
                    epochMs = now,
                )
                mutateItem(item.id) { current ->
                    if (current.status != DownloadStatus.Downloading) {
                        current
                    } else {
                        current.copy(
                            downloadedBytes = safeDownloadedBytes,
                            totalBytes = normalizedTotalBytes,
                            bytesPerSecond = stats.bytesPerSecond ?: current.bytesPerSecond,
                            etaSeconds = stats.etaSeconds,
                            updatedAtEpochMs = now,
                            errorMessage = null,
                        )
                    }
                }
            },
            onSuccess = { localFileUri, totalBytes ->
                activeHandles.remove(item.id)
                progressSamples.remove(item.id)
                mutateItem(item.id) { current ->
                    current.copy(
                        status = DownloadStatus.Completed,
                        localFileUri = localFileUri,
                        downloadedBytes = if (totalBytes != null && totalBytes > 0L) {
                            totalBytes
                        } else {
                            current.downloadedBytes
                        },
                        totalBytes = totalBytes?.takeIf { it > 0L } ?: current.totalBytes,
                        bytesPerSecond = null,
                        etaSeconds = null,
                        errorMessage = null,
                        updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                    )
                }
                scheduleQueuedDownloads()
            },
            onFailure = { message ->
                activeHandles.remove(item.id)
                progressSamples.remove(item.id)
                mutateItem(item.id) { current ->
                    if (current.status != DownloadStatus.Downloading) {
                        current
                    } else {
                        current.copy(
                            status = DownloadStatus.Failed,
                            bytesPerSecond = null,
                            etaSeconds = null,
                            errorMessage = message.ifBlank { runBlocking { getString(Res.string.download_failed) } },
                            updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                        )
                    }
                }
                scheduleQueuedDownloads()
            },
        )

        activeHandles[item.id] = handle
    }

    private fun scheduleQueuedDownloads() {
        val nextIds = DownloadQueuePolicy.nextQueuedIds(
            items = _uiState.value.items,
            activeCount = activeHandles.size,
            maxConcurrent = DEFAULT_MAX_CONCURRENT_DOWNLOADS,
        )
        nextIds.forEach { downloadId ->
            _uiState.value.items.firstOrNull { it.id == downloadId }?.let(::startDownload)
        }
    }

    private fun mutateItem(downloadId: String, transform: (DownloadItem) -> DownloadItem) {
        var changed = false
        val updated = _uiState.value.items.map { item ->
            if (item.id == downloadId) {
                changed = true
                transform(item)
            } else {
                item
            }
        }

        if (changed) {
            publish(updated)
            persist()
        }
    }

    private fun replaceItem(item: DownloadItem) {
        val updated = _uiState.value.items.map { existing ->
            if (existing.id == item.id) item else existing
        }
        publish(updated)
    }

    private fun publish(items: List<DownloadItem>) {
        _uiState.value = DownloadsUiState(
            items = items,
        )
        notifyLiveStatusPlatform()
    }

    private fun notifyLiveStatusPlatform() {
        runCatching {
            DownloadsLiveStatusPlatform.onItemsChanged(_uiState.value.items)
        }
    }

    private fun persist() {
        DownloadsStorage.savePayload(
            DownloadsCodec.encodeItems(_uiState.value.items),
        )
    }

    private fun nextDownloadId(nowEpochMs: Long): String {
        nextDownloadOrdinal += 1L
        return buildString {
            append(nowEpochMs.toString(36))
            append('_')
            append(nextDownloadOrdinal.toString(36))
        }
    }

    private fun normalizeCompletedLocalFileUri(item: DownloadItem): DownloadItem {
        if (item.status != DownloadStatus.Completed) return item
        val resolvedUri = DownloadsPlatformDownloader.resolveLocalFileUri(
            localFileUri = item.localFileUri,
            destinationFileName = item.fileName,
        ) ?: return item
        return if (resolvedUri != item.localFileUri) {
            item.copy(localFileUri = resolvedUri)
        } else {
            item
        }
    }

    private fun DownloadItem.hasPlayableLocalFile(): Boolean =
        status == DownloadStatus.Completed &&
            DownloadsPlatformDownloader.resolveLocalFileUri(
                localFileUri = localFileUri,
                destinationFileName = fileName,
            ) != null
}

@Serializable
private data class StoredDownloadsPayload(
    val items: List<DownloadItem> = emptyList(),
)

private object DownloadsCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decodeItems(payload: String): List<DownloadItem> =
        runCatching {
            json.decodeFromString<StoredDownloadsPayload>(payload).items
        }.getOrDefault(emptyList())

    fun encodeItems(items: Collection<DownloadItem>): String =
        json.encodeToString(
            StoredDownloadsPayload(
                items = items.toList(),
            ),
        )
}

private fun sanitizeRequestHeaders(headers: Map<String, String>?): Map<String, String> =
    headers
        .orEmpty()
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (
                normalizedKey.isBlank() ||
                normalizedValue.isBlank() ||
                normalizedKey.equals("Accept-Encoding", ignoreCase = true) ||
                normalizedKey.equals("Range", ignoreCase = true)
            ) {
                null
            } else {
                normalizedKey to normalizedValue
            }
        }
        .toMap()

private fun sanitizeResponseHeaders(headers: Map<String, String>?): Map<String, String> =
    headers
        .orEmpty()
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isBlank() || normalizedValue.isBlank()) {
                null
            } else {
                normalizedKey to normalizedValue
            }
        }
        .toMap()

private fun buildLogicalKey(
    parentMetaId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
): String = if (seasonNumber != null && episodeNumber != null) {
    "${parentMetaId.trim()}|$seasonNumber|$episodeNumber"
} else {
    "${parentMetaId.trim()}|movie"
}

private fun buildFileName(
    title: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    fallbackTitle: String,
    sourceUrl: String,
    nowEpochMs: Long,
): String {
    val baseTitle = if (seasonNumber != null && episodeNumber != null) {
        buildString {
            append(title)
            append(" S")
            append(seasonNumber.toString().padStart(2, '0'))
            append('E')
            append(episodeNumber.toString().padStart(2, '0'))
            if (!episodeTitle.isNullOrBlank()) {
                append(' ')
                append(episodeTitle)
            }
        }
    } else {
        title.ifBlank { fallbackTitle }
    }

    val extension = sourceUrl.fileExtensionFromUrl()
    return buildString {
        append(baseTitle.sanitizeFileName().ifBlank { "download" }.take(92))
        append('_')
        append(nowEpochMs.toString(36))
        append('.')
        append(extension)
    }
}

private fun String.sanitizeFileName(): String =
    trim().replace(Regex("[^A-Za-z0-9._ -]"), "_")

private fun String.fileExtensionFromUrl(): String {
    val withoutQuery = substringBefore('?').substringBefore('#')
    val suffix = withoutQuery.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .trim()

    return if (suffix.length in 2..5 && suffix.all { it.isLetterOrDigit() }) {
        suffix
    } else {
        "mp4"
    }
}

private fun String.isSupportedDownloadUrl(): Boolean {
    val normalized = trim().lowercase()
    if (normalized.startsWith("magnet:")) return false
    if (normalized.endsWith(".m3u8") || normalized.contains(".m3u8?")) return false
    if (normalized.endsWith(".mpd") || normalized.contains(".mpd?")) return false
    if (normalized.endsWith(".torrent") || normalized.contains(".torrent?")) return false
    return normalized.startsWith("http://") || normalized.startsWith("https://")
}
