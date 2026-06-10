package com.nuvio.app.features.experimental

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VideoUpscalerPreset(
    val id: String,
    val displayName: String,
    val description: String,
) {
    OFF(
        id = "off",
        displayName = "Off",
        description = "Use the default Windows player scaling profile.",
    ),
    HIGH_QUALITY(
        id = "high_quality",
        displayName = "High Quality",
        description = "Best general-purpose detail for 1080p and lower sources.",
    ),
    SHARP_DETAIL(
        id = "sharp_detail",
        displayName = "Sharp Detail",
        description = "Crisper edges for soft sources. May reveal compression artifacts.",
    ),
    SOFT_FILM(
        id = "soft_film",
        displayName = "Soft Film",
        description = "Smoother live-action scaling with light debanding.",
    ),
    ANIMATION(
        id = "animation",
        displayName = "Animation",
        description = "Sharper line-art scaling for animation and anime-style content.",
    ),
    LOW_GPU(
        id = "low_gpu",
        displayName = "Low GPU",
        description = "Lower-cost scaling for older GPUs or battery use.",
    );

    companion object {
        fun fromId(id: String?): VideoUpscalerPreset =
            entries.firstOrNull { it.id == id } ?: OFF
    }
}

object ExperimentalFeatureSettings {
    private val _universalSearchEnabled = MutableStateFlow(true)
    val universalSearchEnabled: StateFlow<Boolean> = _universalSearchEnabled.asStateFlow()

    private val _videoUpscalerPreset = MutableStateFlow(VideoUpscalerPreset.OFF)
    val videoUpscalerPreset: StateFlow<VideoUpscalerPreset> = _videoUpscalerPreset.asStateFlow()

    private val _displaySyncEnabled = MutableStateFlow(false)
    val displaySyncEnabled: StateFlow<Boolean> = _displaySyncEnabled.asStateFlow()

    fun seed(
        universalSearchEnabled: Boolean,
        videoUpscalerPreset: VideoUpscalerPreset,
        displaySyncEnabled: Boolean = false,
    ) {
        _universalSearchEnabled.value = universalSearchEnabled
        _videoUpscalerPreset.value = videoUpscalerPreset
        _displaySyncEnabled.value = displaySyncEnabled
    }

    fun setUniversalSearchEnabled(enabled: Boolean) {
        if (_universalSearchEnabled.value == enabled) return
        _universalSearchEnabled.value = enabled
    }

    fun setVideoUpscalerPreset(preset: VideoUpscalerPreset) {
        if (_videoUpscalerPreset.value == preset) return
        _videoUpscalerPreset.value = preset
    }

    fun setDisplaySyncEnabled(enabled: Boolean) {
        if (_displaySyncEnabled.value == enabled) return
        _displaySyncEnabled.value = enabled
    }
}
