package com.nuvio.app.features.streams

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.diagnostics.AppDiagnostics
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.debrid.DirectDebridStreamPreparer
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DebridStreamPresentation
import com.nuvio.app.features.debrid.LocalDebridAvailabilityService
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.pluginContentId
import com.nuvio.app.features.plugins.PluginsUiState
import com.nuvio.app.features.plugins.PluginRepositoryItem
import com.nuvio.app.features.plugins.PluginRuntimeResult
import com.nuvio.app.features.plugins.PluginScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.launch

object StreamsRepository {
    private val log = Logger.withTag("StreamsRepo")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(StreamsUiState())
    val uiState: StateFlow<StreamsUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeRequestKey: String? = null

    fun requestToken(
        type: String,
        videoId: String,
        season: Int? = null,
        episode: Int? = null,
        manualSelection: Boolean = false,
    ): String =
        "$type::$videoId::$season::$episode::$manualSelection"

    fun load(type: String, videoId: String, parentMetaId: String? = null, season: Int? = null, episode: Int? = null, manualSelection: Boolean = false) {
        load(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            season = season,
            episode = episode,
            manualSelection = manualSelection,
            forceRefresh = false,
        )
    }

    fun reload(type: String, videoId: String, parentMetaId: String? = null, season: Int? = null, episode: Int? = null, manualSelection: Boolean = false) {
        load(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            season = season,
            episode = episode,
            manualSelection = manualSelection,
            forceRefresh = true,
        )
    }

    private fun load(type: String, videoId: String, parentMetaId: String?, season: Int?, episode: Int?, manualSelection: Boolean, forceRefresh: Boolean) {
        val pluginUiState = if (AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.initialize()
            PluginRepository.uiState.value
        } else {
            PluginsUiState(pluginsEnabled = false)
        }
        val requestToken = requestToken(
            type = type,
            videoId = videoId,
            season = season,
            episode = episode,
            manualSelection = manualSelection,
        )
        val requestKey = "$requestToken::pluginsGrouped=${pluginUiState.groupStreamsByRepository}"
        val currentState = _uiState.value
        if (
            !forceRefresh &&
            activeRequestKey == requestKey &&
            (currentState.groups.isNotEmpty() || currentState.emptyStateReason != null || currentState.isAnyLoading)
        ) {
            log.d { "Skipping stream reload for unchanged request type=$type id=$videoId" }
            AppDiagnostics.breadcrumb(
                event = "streams.load.skip",
                details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                    mapOf("reason" to "unchanged"),
            )
            return
        }

        AppDiagnostics.breadcrumb(
            event = "streams.load.start",
            details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                mapOf(
                    "forceRefresh" to forceRefresh.toString(),
                    "pluginsEnabled" to pluginUiState.pluginsEnabled.toString(),
                    "pluginGroupByRepository" to pluginUiState.groupStreamsByRepository.toString(),
                ),
        )
        activeRequestKey = requestKey
        activeJob?.cancel()
        _uiState.value = StreamsUiState(requestToken = requestToken)

        PlayerSettingsRepository.ensureLoaded()
        val playerSettings = PlayerSettingsRepository.uiState.value
        val debridSettings = DebridSettingsRepository.snapshot()
        val streamBadgeRules = StreamBadgeSettingsRepository.snapshot()
        fun presentStreamGroup(group: AddonStreamGroup): AddonStreamGroup {
            val badgeGroup = StreamBadgePresentation.apply(
                groups = listOf(group),
                rules = streamBadgeRules,
            ).firstOrNull() ?: group
            return DebridStreamPresentation.apply(
                groups = listOf(badgeGroup),
                settings = debridSettings,
            ).firstOrNull() ?: badgeGroup
        }
        val autoPlayMode = playerSettings.streamAutoPlayMode
        val isAutoPlayEnabled = !manualSelection && autoPlayMode != StreamAutoPlayMode.MANUAL &&
            !(autoPlayMode == StreamAutoPlayMode.REGEX_MATCH &&
                !StreamAutoPlayPolicy.isRegexSelectionConfigured(playerSettings.streamAutoPlayRegex))

        // Look up persisted binge group when both settings are enabled
        val persistedBingeGroup = if (
            playerSettings.streamAutoPlayPreferBingeGroup &&
            playerSettings.streamAutoPlayReuseBingeGroup
        ) {
            parentMetaId?.let { BingeGroupCacheRepository.get(it) }
        } else null

        // Enable direct auto-play flow if normal auto-play is enabled,
        // OR if we have a persisted binge group in MANUAL mode
        val bingeGroupDirectFlow = !manualSelection &&
            persistedBingeGroup != null &&
            autoPlayMode == StreamAutoPlayMode.MANUAL
        val isDirectAutoPlayFlow = isAutoPlayEnabled || bingeGroupDirectFlow

        if (isDirectAutoPlayFlow) {
            _uiState.value = StreamsUiState(
                requestToken = requestToken,
                isDirectAutoPlayFlow = true,
                showDirectAutoPlayOverlay = true,
            )
        }

        val embeddedStreams = MetaDetailsRepository.findEmbeddedStreams(videoId)
        if (embeddedStreams.isNotEmpty()) {
            log.d { "Using ${embeddedStreams.size} embedded streams for type=$type id=$videoId" }
            AppDiagnostics.breadcrumb(
                event = "streams.embedded.success",
                details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                    mapOf(
                        "streamCount" to embeddedStreams.size.toString(),
                        "addonName" to embeddedStreams.first().addonName,
                    ),
            )
            val group = AddonStreamGroup(
                addonName = embeddedStreams.first().addonName,
                addonId = "embedded",
                streams = embeddedStreams,
                isLoading = false,
            )
            val presentedGroup = presentStreamGroup(group)
            _uiState.value = StreamsUiState(
                requestToken = requestToken,
                groups = listOf(presentedGroup),
                activeAddonIds = setOf("embedded"),
                isAnyLoading = false,
            )
            return
        }

        val installedAddons = AddonRepository.uiState.value.addons.enabledAddons()
        val pluginScrapers = if (AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.getEnabledScrapersForType(type)
        } else {
            emptyList()
        }
        val pluginProviderGroups = pluginScrapers.toPluginProviderGroups(
            repositories = pluginUiState.repositories,
            groupByRepository = pluginUiState.groupStreamsByRepository,
        )

        if (installedAddons.isEmpty() && pluginProviderGroups.isEmpty()) {
            AppDiagnostics.breadcrumb(
                event = "streams.load.empty",
                details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                    mapOf("reason" to StreamsEmptyStateReason.NoAddonsInstalled.name),
            )
            _uiState.value = StreamsUiState(
                requestToken = requestToken,
                isAnyLoading = false,
                emptyStateReason = StreamsEmptyStateReason.NoAddonsInstalled,
            )
            return
        }

        val streamAddons = installedAddons
            .mapNotNull { addon ->
                val manifest = addon.manifest ?: return@mapNotNull null
                val supportsRequestedStream = manifest.resources.any { resource ->
                    resource.name == "stream" &&
                        resource.types.contains(type) &&
                        (resource.idPrefixes.isEmpty() ||
                            resource.idPrefixes.any { videoId.startsWith(it) })
                }
                if (!supportsRequestedStream) return@mapNotNull null

                InstalledStreamAddonTarget(
                    addonName = addon.displayTitle.ifBlank { manifest.name },
                    addonId = addon.streamAddonInstanceId(manifest.id),
                    manifest = manifest,
                )
            }

        log.d { "Found ${streamAddons.size} addons for stream type=$type id=$videoId" }
        AppDiagnostics.breadcrumb(
            event = "streams.providers.ready",
            details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                mapOf(
                    "installedAddonCount" to streamAddons.size.toString(),
                    "pluginGroupCount" to pluginProviderGroups.size.toString(),
                    "pluginScraperCount" to pluginProviderGroups.sumOf { it.scrapers.size }.toString(),
                ),
        )

        if (streamAddons.isEmpty() && pluginProviderGroups.isEmpty()) {
            AppDiagnostics.breadcrumb(
                event = "streams.load.empty",
                details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                    mapOf("reason" to StreamsEmptyStateReason.NoCompatibleAddons.name),
            )
            _uiState.value = StreamsUiState(
                requestToken = requestToken,
                isAnyLoading = false,
                emptyStateReason = StreamsEmptyStateReason.NoCompatibleAddons,
            )
            return
        }

        // Initialise loading placeholders
        val installedAddonOrder = streamAddons.map { it.addonName }
        val warmedAddonGroups = AddonStreamWarmupRepository
            .cachedGroups(type = type, videoId = videoId, season = season, episode = episode)
            .orEmpty()
            .associateBy { it.addonId }
        val warmedAddonIds = warmedAddonGroups.keys
        val initialGroups = StreamAutoPlaySelector.orderAddonStreams(streamAddons.map { addon ->
            warmedAddonGroups[addon.addonId]?.let(::presentStreamGroup) ?: AddonStreamGroup(
                addonName = addon.addonName,
                addonId = addon.addonId,
                streams = emptyList(),
                isLoading = true,
            )
        } + pluginProviderGroups.map { providerGroup ->
            AddonStreamGroup(
                addonName = providerGroup.addonName,
                addonId = providerGroup.addonId,
                streams = emptyList(),
                isLoading = true,
            )
        }, installedAddonOrder)
        val isInitiallyLoading = initialGroups.any { it.isLoading }
        _uiState.value = StreamsUiState(
            requestToken = requestToken,
            groups = initialGroups,
            activeAddonIds = initialGroups.map { it.addonId }.toSet(),
            isAnyLoading = isInitiallyLoading,
            emptyStateReason = null,
            isDirectAutoPlayFlow = isDirectAutoPlayFlow,
            showDirectAutoPlayOverlay = isDirectAutoPlayFlow,
        )

        activeJob = scope.launch {
            val pendingStreamAddons = streamAddons.filterNot { it.addonId in warmedAddonIds }
            val completions = Channel<StreamLoadCompletion>(capacity = Channel.BUFFERED)
            val pluginRemainingByAddonId = pluginProviderGroups
                .associate { it.addonId to it.scrapers.size }
                .toMutableMap()
            val pluginFirstErrorByAddonId = mutableMapOf<String, String>()
            val totalTasks = pendingStreamAddons.size +
                pluginProviderGroups.sumOf { it.scrapers.size }
            AppDiagnostics.breadcrumb(
                event = "streams.tasks.start",
                details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                    mapOf(
                        "pendingAddons" to pendingStreamAddons.size.toString(),
                        "warmedAddons" to warmedAddonIds.size.toString(),
                        "pluginTasks" to pluginProviderGroups.sumOf { it.scrapers.size }.toString(),
                        "totalTasks" to totalTasks.toString(),
                    ),
            )

            val installedAddonNames = installedAddonOrder.toSet()
            val installedAddonIds = streamAddons.map { it.addonId }.toSet()
            val debridAvailabilityJobs = mutableListOf<Job>()
            var autoSelectTriggered = false
            var timeoutElapsed = false
            fun publishCompletion(completion: StreamLoadCompletion) {
                if (completions.trySend(completion).isFailure) {
                    log.d { "Ignoring late stream load completion after channel close" }
                }
            }

            fun publishAddonGroup(group: AddonStreamGroup) {
                _uiState.update { current ->
                    val updated = StreamAutoPlaySelector.orderAddonStreams(
                        groups = current.groups.map { currentGroup ->
                            if (currentGroup.addonId == group.addonId) group else currentGroup
                        },
                        installedOrder = installedAddonOrder,
                    )
                    val anyLoading = updated.any { it.isLoading }
                    current.copy(
                        groups = updated,
                        isAnyLoading = anyLoading,
                        emptyStateReason = updated.toEmptyStateReason(anyLoading),
                    )
                }
            }

            fun launchDebridAvailability(group: AddonStreamGroup) {
                if (group.addonId !in installedAddonIds || group.streams.isEmpty()) return

                val eligibleGroupIds = setOf(group.addonId)
                val checkingGroup = LocalDebridAvailabilityService.markChecking(
                    groups = listOf(group),
                    eligibleGroupIds = eligibleGroupIds,
                ).firstOrNull() ?: group
                publishAddonGroup(checkingGroup)

                val availabilityJob = launch {
                    val availabilityGroup = LocalDebridAvailabilityService.annotateCachedAvailability(
                        groups = listOf(checkingGroup),
                        eligibleGroupIds = eligibleGroupIds,
                    ).firstOrNull() ?: checkingGroup
                    publishAddonGroup(presentStreamGroup(availabilityGroup))
                }
                debridAvailabilityJobs += availabilityJob
            }

            val timeoutJob = if (isDirectAutoPlayFlow) {
                val timeoutSeconds = playerSettings.streamAutoPlayTimeoutSeconds
                val isUnlimitedTimeout = timeoutSeconds == Int.MAX_VALUE
                // Timeout semantics:
                // - 0 (instant): timeoutElapsed immediately, full select on each response
                // - 1-30 (bounded): wait the configured delay, then full select
                // - unlimited (Int.MAX_VALUE): timeoutElapsed immediately, full select on each response,
                //   with 60s hard fallback to stream picker
                if (timeoutSeconds <= 0 || isUnlimitedTimeout) {
                    timeoutElapsed = true
                    // For unlimited: launch a hard 60s fallback to dismiss overlay
                    if (isUnlimitedTimeout) {
                        launch {
                            delay(60_000L)
                            if (!autoSelectTriggered) {
                                autoSelectTriggered = true
                                val allStreams = _uiState.value.groups.flatMap { it.streams }
                                if (allStreams.isNotEmpty()) {
                                    val selected = StreamAutoPlaySelector.selectAutoPlayStream(
                                        streams = allStreams,
                                        mode = autoPlayMode,
                                        regexPattern = playerSettings.streamAutoPlayRegex,
                                        source = playerSettings.streamAutoPlaySource,
                                        installedAddonNames = installedAddonNames,
                                        selectedAddons = playerSettings.streamAutoPlaySelectedAddons,
                                        selectedPlugins = playerSettings.streamAutoPlaySelectedPlugins,
                                        preferredBingeGroup = persistedBingeGroup,
                                        preferBingeGroupInSelection = persistedBingeGroup != null,
                                        bingeGroupOnly = false,
                                        debridEnabled = debridSettings.canResolvePlayableLinks,
                                        activeResolverProviderId = debridSettings.activeResolverProviderId,
                                    )
                                    _uiState.update { it.copy(autoPlayStream = selected) }
                                }
                                if (_uiState.value.autoPlayStream == null) {
                                    _uiState.update {
                                        it.copy(
                                            isDirectAutoPlayFlow = false,
                                            showDirectAutoPlayOverlay = false,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    }
                } else {
                    // Bounded timeout (1-30s)
                    launch {
                        delay(timeoutSeconds * 1_000L)
                        timeoutElapsed = true
                        if (!autoSelectTriggered) {
                            val allStreams = _uiState.value.groups.flatMap { it.streams }
                            if (allStreams.isNotEmpty()) {
                                val evaluation = StreamAutoPlaySelector.evaluateAutoPlayStream(
                                    streams = allStreams,
                                    mode = autoPlayMode,
                                    regexPattern = playerSettings.streamAutoPlayRegex,
                                    source = playerSettings.streamAutoPlaySource,
                                    installedAddonNames = installedAddonNames,
                                    selectedAddons = playerSettings.streamAutoPlaySelectedAddons,
                                    selectedPlugins = playerSettings.streamAutoPlaySelectedPlugins,
                                    preferredBingeGroup = persistedBingeGroup,
                                    preferBingeGroupInSelection = persistedBingeGroup != null,
                                    bingeGroupOnly = false,
                                    debridEnabled = debridSettings.canResolvePlayableLinks,
                                    activeResolverProviderId = debridSettings.activeResolverProviderId,
                                )
                                if (evaluation.stream != null || !evaluation.hasPendingDebridCandidate) {
                                    autoSelectTriggered = true
                                    _uiState.update {
                                        it.copy(
                                            autoPlayStream = evaluation.stream,
                                            autoPlayCandidates = evaluation.readyStreams,
                                        )
                                    }
                                }
                                if (evaluation.stream == null && !evaluation.hasPendingDebridCandidate) {
                                    _uiState.update {
                                        it.copy(
                                            isDirectAutoPlayFlow = false,
                                            showDirectAutoPlayOverlay = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                null
            }

            pendingStreamAddons.forEach { addon ->
                launch {
                    val url = buildAddonResourceUrl(
                        manifestUrl = addon.manifest.transportUrl,
                        resource = "stream",
                        type = type,
                        id = videoId,
                    )
                    log.d { "Fetching streams from: $url" }

                    val displayName = addon.addonName
                    val group = runCatchingUnlessCancelled {
                        val payload = httpGetText(url)
                        StreamParser.parse(
                            payload = payload,
                            addonName = displayName,
                            addonId = addon.addonId,
                        )
                    }.fold(
                        onSuccess = { streams ->
                            log.d { "Got ${streams.size} streams from ${displayName}" }
                            AppDiagnostics.breadcrumb(
                                event = "streams.addon.success",
                                details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                                    mapOf(
                                        "addonName" to displayName,
                                        "addonId" to addon.addonId,
                                        "streamCount" to streams.size.toString(),
                                    ),
                            )
                            AddonStreamGroup(
                                addonName = displayName,
                                addonId = addon.addonId,
                                streams = streams,
                                isLoading = false,
                            )
                        },
                        onFailure = { err ->
                            log.w(err) { "Failed to fetch streams from ${displayName}" }
                            AppDiagnostics.error(
                                event = "streams.addon.failure",
                                throwable = err,
                                details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                                    mapOf(
                                        "addonName" to displayName,
                                        "addonId" to addon.addonId,
                                    ),
                            )
                            AddonStreamGroup(
                                addonName = displayName,
                                addonId = addon.addonId,
                                streams = emptyList(),
                                isLoading = false,
                                error = err.message,
                            )
                        },
                    )
                    publishCompletion(StreamLoadCompletion.Addon(group))
                }
            }

            pluginProviderGroups.forEach { providerGroup ->
                val includeScraperNameInSubtitle = false
                providerGroup.scrapers.forEach { scraper ->
                    launch {
                        val completion = PluginRepository.executeScraper(
                            scraper = scraper,
                            tmdbId = pluginContentId(
                                videoId = videoId,
                                season = season,
                                episode = episode,
                            ),
                            mediaType = type,
                            season = season,
                            episode = episode,
                        ).fold(
                            onSuccess = { results ->
                                AppDiagnostics.breadcrumb(
                                    event = "streams.plugin.success",
                                    details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                                        mapOf(
                                            "providerName" to providerGroup.addonName,
                                            "providerId" to providerGroup.addonId,
                                            "scraperName" to scraper.name,
                                            "streamCount" to results.size.toString(),
                                        ),
                                )
                                StreamLoadCompletion.PluginScraper(
                                    addonId = providerGroup.addonId,
                                    streams = results.map { result ->
                                        result.toStreamItem(
                                            scraper = scraper,
                                            addonName = providerGroup.addonName,
                                            addonId = providerGroup.addonId,
                                            includeScraperNameInSubtitle = includeScraperNameInSubtitle,
                                        )
                                    },
                                    error = null,
                                )
                            },
                            onFailure = { error ->
                                AppDiagnostics.error(
                                    event = "streams.plugin.failure",
                                    throwable = error,
                                    details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                                        mapOf(
                                            "providerName" to providerGroup.addonName,
                                            "providerId" to providerGroup.addonId,
                                            "scraperName" to scraper.name,
                                        ),
                                )
                                StreamLoadCompletion.PluginScraper(
                                    addonId = providerGroup.addonId,
                                    streams = emptyList(),
                                    error = error.message ?: getString(Res.string.streams_failed_to_load_scraper, scraper.name),
                                )
                            },
                        )
                        publishCompletion(completion)
                    }
                }
            }

            repeat(totalTasks) {
                when (val completion = completions.receive()) {
                    is StreamLoadCompletion.Addon -> {
                        val result = completion.group
                        publishAddonGroup(presentStreamGroup(result))
                        launchDebridAvailability(result)
                    }

                    is StreamLoadCompletion.PluginScraper -> {
                        val remaining = (pluginRemainingByAddonId[completion.addonId] ?: 1) - 1
                        pluginRemainingByAddonId[completion.addonId] = remaining.coerceAtLeast(0)
                        if (!completion.error.isNullOrBlank() && pluginFirstErrorByAddonId[completion.addonId].isNullOrBlank()) {
                            pluginFirstErrorByAddonId[completion.addonId] = completion.error
                        }

                        _uiState.update { current ->
                            val updated = StreamAutoPlaySelector.orderAddonStreams(
                                groups = current.groups.map { group ->
                                    if (group.addonId != completion.addonId) {
                                        group
                                    } else {
                                        val mergedStreams = if (completion.streams.isEmpty()) {
                                            group.streams
                                        } else {
                                            group.streams + completion.streams
                                        }
                                        val stillLoading = remaining > 0
                                        val finalError = if (mergedStreams.isEmpty() && !stillLoading) {
                                            pluginFirstErrorByAddonId[completion.addonId]
                                        } else {
                                            null
                                        }
                                        val nextGroup = group.copy(
                                            streams = mergedStreams,
                                            isLoading = stillLoading,
                                            error = finalError,
                                        )
                                        presentStreamGroup(nextGroup)
                                    }
                                },
                                installedOrder = installedAddonOrder,
                            )
                            val anyLoading = updated.any { it.isLoading }
                            current.copy(
                                groups = updated,
                                isAnyLoading = anyLoading,
                                emptyStateReason = updated.toEmptyStateReason(anyLoading),
                            )
                        }
                    }

                }
            }

            for (availabilityJob in debridAvailabilityJobs) {
                availabilityJob.join()
            }
            launch {
                DirectDebridStreamPreparer.prepare(
                    streams = _uiState.value.groups
                        .filter { it.addonId in installedAddonIds }
                        .flatMap { it.streams },
                    season = season,
                    episode = episode,
                    playerSettings = playerSettings,
                    installedAddonNames = installedAddonNames,
                ) { original, prepared ->
                    _uiState.update { current ->
                        current.copy(
                            groups = DirectDebridStreamPreparer.replacePreparedStream(
                                groups = current.groups,
                                original = original,
                                prepared = prepared,
                                eligibleGroupIds = installedAddonIds,
                            ),
                        )
                    }
                }

                // Early match / timeout-elapsed auto-select on each addon response
                if (isDirectAutoPlayFlow && !autoSelectTriggered) {
                    val allStreams = _uiState.value.groups.flatMap { it.streams }
                    if (allStreams.isNotEmpty()) {
                        if (timeoutElapsed) {
                            // After timeout: full fallback (bingeGroupOnly = false)
                            val selected = StreamAutoPlaySelector.selectAutoPlayStream(
                                streams = allStreams,
                                mode = autoPlayMode,
                                regexPattern = playerSettings.streamAutoPlayRegex,
                                source = playerSettings.streamAutoPlaySource,
                                installedAddonNames = installedAddonNames,
                                selectedAddons = playerSettings.streamAutoPlaySelectedAddons,
                                selectedPlugins = playerSettings.streamAutoPlaySelectedPlugins,
                                preferredBingeGroup = persistedBingeGroup,
                                preferBingeGroupInSelection = persistedBingeGroup != null,
                                bingeGroupOnly = false,
                                debridEnabled = debridSettings.canResolvePlayableLinks,
                                activeResolverProviderId = debridSettings.activeResolverProviderId,
                            )
                            if (selected != null) {
                                autoSelectTriggered = true
                                _uiState.update { it.copy(autoPlayStream = selected) }
                            }
                        } else if (persistedBingeGroup != null) {
                            // Before timeout: try binge-group-only early match
                            val earlyMatch = StreamAutoPlaySelector.selectAutoPlayStream(
                                streams = allStreams,
                                mode = autoPlayMode,
                                regexPattern = playerSettings.streamAutoPlayRegex,
                                source = playerSettings.streamAutoPlaySource,
                                installedAddonNames = installedAddonNames,
                                selectedAddons = playerSettings.streamAutoPlaySelectedAddons,
                                selectedPlugins = playerSettings.streamAutoPlaySelectedPlugins,
                                preferredBingeGroup = persistedBingeGroup,
                                preferBingeGroupInSelection = true,
                                bingeGroupOnly = true,
                                debridEnabled = debridSettings.canResolvePlayableLinks,
                                activeResolverProviderId = debridSettings.activeResolverProviderId,
                            )
                            if (earlyMatch != null) {
                                autoSelectTriggered = true
                                _uiState.update { it.copy(autoPlayStream = earlyMatch) }
                            }
                        }
                    }
                }
            }

            // All addons finished — run final auto-select if not yet triggered
            if (isDirectAutoPlayFlow && !autoSelectTriggered) {
                autoSelectTriggered = true
                val allStreams = _uiState.value.groups.flatMap { it.streams }
                val evaluation = StreamAutoPlaySelector.evaluateAutoPlayStream(
                    streams = allStreams,
                    mode = autoPlayMode,
                    regexPattern = playerSettings.streamAutoPlayRegex,
                    source = playerSettings.streamAutoPlaySource,
                    installedAddonNames = installedAddonNames,
                    selectedAddons = playerSettings.streamAutoPlaySelectedAddons,
                    selectedPlugins = playerSettings.streamAutoPlaySelectedPlugins,
                    preferredBingeGroup = persistedBingeGroup,
                    preferBingeGroupInSelection = persistedBingeGroup != null,
                    bingeGroupOnly = false,
                    debridEnabled = debridSettings.canResolvePlayableLinks,
                    activeResolverProviderId = debridSettings.activeResolverProviderId,
                )
                _uiState.update {
                    it.copy(
                        autoPlayStream = evaluation.stream,
                        autoPlayCandidates = evaluation.readyStreams,
                    )
                }
            }
            if (isDirectAutoPlayFlow && _uiState.value.autoPlayStream == null) {
                _uiState.update {
                    it.copy(
                        isDirectAutoPlayFlow = false,
                        showDirectAutoPlayOverlay = false,
                    )
                }
            }
            AppDiagnostics.breadcrumb(
                event = "streams.tasks.complete",
                details = streamRequestDetails(type, videoId, parentMetaId, season, episode, manualSelection) +
                    mapOf(
                        "groupCount" to _uiState.value.groups.size.toString(),
                        "streamCount" to _uiState.value.groups.sumOf { it.streams.size }.toString(),
                        "emptyState" to _uiState.value.emptyStateReason?.name,
                        "autoPlaySelected" to (_uiState.value.autoPlayStream != null).toString(),
                    ),
            )
            timeoutJob?.cancel()
        }
    }

    fun selectFilter(addonId: String?) {
        _uiState.update { it.copy(selectedFilter = addonId) }
    }

    fun consumeAutoPlay() {
        activeRequestKey = null
        _uiState.update {
            it.copy(
                autoPlayStream = null,
                autoPlayCandidates = emptyList(),
                isDirectAutoPlayFlow = false,
                showDirectAutoPlayOverlay = false,
            )
        }
    }

    fun skipAutoPlayStream(stream: StreamItem): Boolean {
        var hasNext = false
        _uiState.update { current ->
            val failedIndex = current.autoPlayCandidates.indexOf(stream)
            val remaining = if (failedIndex >= 0) {
                current.autoPlayCandidates.drop(failedIndex + 1)
            } else {
                current.autoPlayCandidates.drop(1)
            }
            hasNext = remaining.isNotEmpty()
            current.copy(
                autoPlayStream = remaining.firstOrNull(),
                autoPlayCandidates = remaining,
                isDirectAutoPlayFlow = remaining.isNotEmpty(),
                showDirectAutoPlayOverlay = remaining.isNotEmpty(),
            )
        }
        return hasNext
    }

    fun cancelLoading() {
        AppDiagnostics.breadcrumb(
            event = "streams.loading.cancel",
            details = mapOf(
                "activeRequest" to activeRequestKey,
                "wasLoading" to _uiState.value.isAnyLoading.toString(),
            ),
        )
        activeJob?.cancel()
        activeJob = null
        _uiState.update { current ->
            if (!current.isAnyLoading && current.groups.none { it.isLoading }) {
                current
            } else {
                val updatedGroups = current.groups.map { group ->
                    if (group.isLoading) group.copy(isLoading = false) else group
                }
                current.copy(
                    groups = updatedGroups,
                    isAnyLoading = false,
                    emptyStateReason = if (updatedGroups.isEmpty()) {
                        current.emptyStateReason
                    } else {
                        updatedGroups.toEmptyStateReason(anyLoading = false)
                    },
                )
            }
        }
    }

    fun clear() {
        activeJob?.cancel()
        activeJob = null
        activeRequestKey = null
        _uiState.value = StreamsUiState()
    }
}

private fun streamRequestDetails(
    type: String,
    videoId: String,
    parentMetaId: String?,
    season: Int?,
    episode: Int?,
    manualSelection: Boolean,
): Map<String, String?> =
    mapOf(
        "type" to type,
        "videoId" to videoId,
        "parentMetaId" to parentMetaId,
        "season" to season?.toString(),
        "episode" to episode?.toString(),
        "manualSelection" to manualSelection.toString(),
    )

private data class InstalledStreamAddonTarget(
    val addonName: String,
    val addonId: String,
    val manifest: com.nuvio.app.features.addons.AddonManifest,
)

private fun com.nuvio.app.features.addons.ManagedAddon.streamAddonInstanceId(manifestId: String): String =
    "addon:$manifestId:$manifestUrl"

private data class PluginProviderGroup(
    val addonId: String,
    val addonName: String,
    val scrapers: List<PluginScraper>,
)

private sealed interface StreamLoadCompletion {
    data class Addon(val group: AddonStreamGroup) : StreamLoadCompletion
    data class PluginScraper(
        val addonId: String,
        val streams: List<StreamItem>,
        val error: String?,
    ) : StreamLoadCompletion
}

private fun List<PluginScraper>.toPluginProviderGroups(
    repositories: List<PluginRepositoryItem>,
    groupByRepository: Boolean,
): List<PluginProviderGroup> {
    if (!groupByRepository) {
        return map { scraper ->
            PluginProviderGroup(
                addonId = "plugin:${scraper.id}",
                addonName = scraper.name,
                scrapers = listOf(scraper),
            )
        }
    }

    val repoNameByUrl = repositories.associate { it.manifestUrl to it.name }
    return groupBy { it.repositoryUrl }
        .map { (repositoryUrl, scrapers) ->
            PluginProviderGroup(
                addonId = "plugin-repo:${repositoryUrl.lowercase()}",
                addonName = repoNameByUrl[repositoryUrl].orEmpty().ifBlank { repositoryUrl.fallbackRepositoryLabel() },
                scrapers = scrapers.sortedBy { it.name.lowercase() },
            )
        }
        .sortedBy { it.addonName.lowercase() }
}

private fun List<AddonStreamGroup>.toEmptyStateReason(anyLoading: Boolean): StreamsEmptyStateReason? {
    if (anyLoading || any { it.streams.isNotEmpty() }) {
        return null
    }

    return if (isNotEmpty() && all { !it.error.isNullOrBlank() }) {
        StreamsEmptyStateReason.StreamFetchFailed
    } else {
        StreamsEmptyStateReason.NoStreamsFound
    }
}

private suspend fun <T> runCatchingUnlessCancelled(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

internal fun PluginRuntimeResult.toStreamItem(
    scraper: PluginScraper,
    addonName: String = scraper.name,
    addonId: String = "plugin:${scraper.id}",
    includeScraperNameInSubtitle: Boolean = false,
): StreamItem {
    val baseTitle = title.takeIf { it.isNotBlank() }
    val baseName = name?.takeIf { it.isNotBlank() }
    val qualityLabel = quality?.takeIf { it.isNotBlank() }
    val displayName = buildString {
        append(baseName ?: baseTitle ?: scraper.name)
        if (!qualityLabel.isNullOrBlank() && !contains(qualityLabel, ignoreCase = true)) {
            append(" - ")
            append(qualityLabel)
        }
    }.takeIf { it.isNotBlank() }
    val displayTitle = (baseTitle ?: baseName ?: scraper.name).takeIf { it.isNotBlank() }
    val subtitleParts = listOfNotNull(
        scraper.name.takeIf { includeScraperNameInSubtitle && it.isNotBlank() },
        size?.takeIf { it.isNotBlank() },
        language?.takeIf { it.isNotBlank() },
        seeders?.takeIf { it > 0 }?.let { "S:$it" },
        peers?.takeIf { it > 0 }?.let { "P:$it" },
    )
    val requestHeaders = headers
        .orEmpty()
        .mapNotNull { (key, value) ->
            val headerName = key.trim()
            val headerValue = value.trim()
            if (headerName.isBlank() || headerValue.isBlank() || headerName.equals("Range", ignoreCase = true)) {
                null
            } else {
                headerName to headerValue
            }
        }
        .toMap()

    return StreamItem(
        name = displayName,
        title = displayTitle,
        description = subtitleParts.joinToString(" • ").ifBlank { null },
        url = url,
        infoHash = infoHash,
        sourceName = scraper.name,
        addonName = addonName,
        addonId = addonId,
        behaviorHints = if (requestHeaders.isEmpty()) {
            StreamBehaviorHints()
        } else {
            StreamBehaviorHints(
                notWebReady = true,
                proxyHeaders = StreamProxyHeaders(request = requestHeaders),
            )
        },
    )
}

private fun String.fallbackRepositoryLabel(): String {
    val withoutQuery = substringBefore("?")
    val withoutManifest = withoutQuery.removeSuffix("/manifest.json")
    val host = withoutManifest.substringAfter("://", withoutManifest).substringBefore('/')
    return host.ifBlank {
        withoutManifest.substringAfterLast('/').ifBlank { "Plugin repository" }
    }
}
