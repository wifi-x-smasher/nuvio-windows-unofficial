package com.nuvio.app.features.catalog

import com.nuvio.app.core.diagnostics.AppDiagnostics
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.toMetaPreview
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.filterReleasedItems
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

const val INTERNAL_LIBRARY_MANIFEST_URL = "nuvio://library"

object CatalogRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeRequest: CatalogRequest? = null
    private val scrollPositions = linkedMapOf<CatalogRequest, CatalogScrollPosition>()

    fun load(
        manifestUrl: String,
        type: String,
        catalogId: String,
        genre: String? = null,
        supportsPagination: Boolean = false,
        force: Boolean = false,
    ) {
        val request = catalogRequest(
            manifestUrl = manifestUrl,
            type = type,
            catalogId = catalogId,
            genre = genre,
            supportsPagination = supportsPagination,
        )
        if (!force && activeRequest == request && (_uiState.value.items.isNotEmpty() || _uiState.value.isLoading)) {
            AppDiagnostics.breadcrumb(
                event = "catalog.load.skip",
                details = request.diagnosticsDetails() + mapOf("reason" to "unchanged"),
            )
            return
        }
        AppDiagnostics.breadcrumb(
            event = "catalog.load.request",
            details = request.diagnosticsDetails() + mapOf("force" to force.toString()),
        )
        activeRequest = request
        if (manifestUrl == INTERNAL_LIBRARY_MANIFEST_URL) {
            fetchInternalLibrary(request)
            return
        }
        fetchPage(request = request, reset = true)
    }

    fun loadMore() {
        val request = activeRequest ?: return
        val current = _uiState.value
        if (current.isLoading || current.nextSkip == null) return
        fetchPage(request = request, reset = false)
    }

    fun clear() {
        activeJob?.cancel()
        activeRequest = null
        scrollPositions.clear()
        _uiState.value = CatalogUiState()
    }

    fun scrollPosition(
        manifestUrl: String,
        type: String,
        catalogId: String,
        genre: String? = null,
        supportsPagination: Boolean = false,
    ): CatalogScrollPosition = scrollPositions[
        catalogRequest(
            manifestUrl = manifestUrl,
            type = type,
            catalogId = catalogId,
            genre = genre,
            supportsPagination = supportsPagination,
        ),
    ] ?: CatalogScrollPosition()

    fun saveScrollPosition(
        manifestUrl: String,
        type: String,
        catalogId: String,
        genre: String? = null,
        supportsPagination: Boolean = false,
        position: CatalogScrollPosition,
    ) {
        scrollPositions[
            catalogRequest(
                manifestUrl = manifestUrl,
                type = type,
                catalogId = catalogId,
                genre = genre,
                supportsPagination = supportsPagination,
            ),
        ] = position
    }

    private fun fetchInternalLibrary(request: CatalogRequest) {
        activeJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
        )

        activeJob = scope.launch {
            AppDiagnostics.breadcrumb(
                event = "catalog.internal.start",
                details = request.diagnosticsDetails(),
            )
            runCatching {
                LibraryRepository.ensureLoaded()
                LibraryRepository.uiState.value.sections
                    .firstOrNull { it.type == request.catalogId }
                    ?.items
                    .orEmpty()
                    .map { it.toMetaPreview() }
                    .let(::dedupeCatalogItems)
            }.fold(
                onSuccess = { items ->
                    if (activeRequest != request) return@fold
                    AppDiagnostics.breadcrumb(
                        event = "catalog.internal.success",
                        details = request.diagnosticsDetails() + mapOf("items" to items.size.toString()),
                    )
                    _uiState.value = CatalogUiState(
                        items = items,
                        isLoading = false,
                        nextSkip = null,
                        errorMessage = null,
                    )
                },
                onFailure = { error ->
                    if (activeRequest != request) return@fold
                    AppDiagnostics.error(
                        event = "catalog.internal.failure",
                        throwable = error,
                        details = request.diagnosticsDetails(),
                    )
                    _uiState.value = CatalogUiState(
                        items = emptyList(),
                        isLoading = false,
                        nextSkip = null,
                        errorMessage = error.message ?: getString(Res.string.catalog_load_failed),
                    )
                },
            )
        }
    }

    private fun fetchPage(
        request: CatalogRequest,
        reset: Boolean,
    ) {
        activeJob?.cancel()
        val current = _uiState.value
        val requestedSkip = if (reset) 0 else current.nextSkip ?: return

        _uiState.value = current.copy(
            items = if (reset) emptyList() else current.items,
            isLoading = true,
            nextSkip = if (reset) null else current.nextSkip,
            errorMessage = null,
        )

        activeJob = scope.launch {
            AppDiagnostics.breadcrumb(
                event = "catalog.page.start",
                details = request.diagnosticsDetails() + mapOf(
                    "reset" to reset.toString(),
                    "skip" to requestedSkip.toString(),
                ),
            )
            runCatching {
                fetchCatalogPage(
                    manifestUrl = request.manifestUrl,
                    type = request.type,
                    catalogId = request.catalogId,
                    genre = request.genre,
                    skip = requestedSkip.takeIf { it > 0 },
                ).withUnreleasedFilter(request.hideUnreleasedContent)
            }.fold(
                onSuccess = { page ->
                    if (activeRequest != request) return@fold

                    val mergedItems = if (reset) {
                        dedupeCatalogItems(page.items)
                    } else {
                        mergeCatalogItems(_uiState.value.items, page.items)
                    }
                    val supportsPagination = request.supportsPagination || page.rawItemCount >= CATALOG_PAGE_SIZE
                    val loadedNewItems = reset || mergedItems.size > current.items.size
                    AppDiagnostics.breadcrumb(
                        event = "catalog.page.success",
                        details = request.diagnosticsDetails() + mapOf(
                            "reset" to reset.toString(),
                            "rawItems" to page.rawItemCount.toString(),
                            "pageItems" to page.items.size.toString(),
                            "mergedItems" to mergedItems.size.toString(),
                            "nextSkip" to page.nextSkip?.toString(),
                        ),
                    )
                    _uiState.value = CatalogUiState(
                        items = mergedItems,
                        isLoading = false,
                        nextSkip = if (supportsPagination && loadedNewItems) page.nextSkip else null,
                        errorMessage = null,
                    )
                },
                onFailure = { error ->
                    if (activeRequest != request) return@fold
                    AppDiagnostics.error(
                        event = "catalog.page.failure",
                        throwable = error,
                        details = request.diagnosticsDetails() + mapOf(
                            "reset" to reset.toString(),
                            "skip" to requestedSkip.toString(),
                        ),
                    )

                    _uiState.value = current.copy(
                        items = if (reset) emptyList() else current.items,
                        isLoading = false,
                        nextSkip = null,
                        errorMessage = error.message ?: getString(Res.string.catalog_load_failed),
                    )
                },
            )
        }
    }
}

private fun CatalogPage.withUnreleasedFilter(hideUnreleasedContent: Boolean): CatalogPage {
    if (!hideUnreleasedContent) return this
    val filteredItems = items.filterReleasedItems(CurrentDateProvider.todayIsoDate())
    return if (filteredItems.size == items.size) this else copy(items = filteredItems)
}

private fun catalogRequest(
    manifestUrl: String,
    type: String,
    catalogId: String,
    genre: String?,
    supportsPagination: Boolean,
): CatalogRequest = CatalogRequest(
    manifestUrl = manifestUrl,
    type = type,
    catalogId = catalogId,
    genre = genre,
    supportsPagination = supportsPagination,
    hideUnreleasedContent = HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent,
)

private data class CatalogRequest(
    val manifestUrl: String,
    val type: String,
    val catalogId: String,
    val genre: String?,
    val supportsPagination: Boolean,
    val hideUnreleasedContent: Boolean,
) {
    fun diagnosticsDetails(): Map<String, String?> =
        mapOf(
            "manifestUrl" to manifestUrl,
            "type" to type,
            "catalogId" to catalogId,
            "genre" to genre,
            "supportsPagination" to supportsPagination.toString(),
            "hideUnreleasedContent" to hideUnreleasedContent.toString(),
        )
}
