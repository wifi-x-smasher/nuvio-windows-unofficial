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

enum class VideoDecoderBackend(
    val id: String,
    val displayName: String,
    val description: String,
) {
    AUTO(
        id = "auto",
        displayName = "Auto",
        description = "Use Nuvio's current decoder priority behavior.",
    ),
    D3D11VA_COPY(
        id = "d3d11va_copy",
        displayName = "D3D11VA copy",
        description = "Use Windows Direct3D hardware decoding with copy-back.",
    ),
    NVDEC_COPY(
        id = "nvdec_copy",
        displayName = "NVDEC copy",
        description = "Use NVIDIA hardware decoding with copy-back.",
    ),
    SOFTWARE(
        id = "software",
        displayName = "Software",
        description = "Disable hardware decoding for compatibility testing.",
    );

    companion object {
        fun fromId(id: String?): VideoDecoderBackend =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

enum class WindowsInternalPlayerBackend(
    val id: String,
    val displayName: String,
    val description: String,
) {
    STABLE_MEDIAMP(
        id = "mediamp",
        displayName = "Stable MediaMP",
        description = "Use the current tested Windows internal player.",
    ),
    NATIVE_MPV(
        id = "native-mpv",
        displayName = "Experimental native MPV window",
        description = "Use a separate Windows video surface for Direct3D/HDR testing.",
    );

    companion object {
        fun fromId(id: String?): WindowsInternalPlayerBackend =
            entries.firstOrNull { it.id == id } ?: STABLE_MEDIAMP
    }
}

object ExperimentalFeatureSettings {
    private val _universalSearchEnabled = MutableStateFlow(true)
    val universalSearchEnabled: StateFlow<Boolean> = _universalSearchEnabled.asStateFlow()

    private val _videoUpscalerPreset = MutableStateFlow(VideoUpscalerPreset.OFF)
    val videoUpscalerPreset: StateFlow<VideoUpscalerPreset> = _videoUpscalerPreset.asStateFlow()

    private val _videoDecoderBackend = MutableStateFlow(VideoDecoderBackend.AUTO)
    val videoDecoderBackend: StateFlow<VideoDecoderBackend> = _videoDecoderBackend.asStateFlow()

    private val _displaySyncEnabled = MutableStateFlow(false)
    val displaySyncEnabled: StateFlow<Boolean> = _displaySyncEnabled.asStateFlow()

    private val _windowsInternalPlayerBackend = MutableStateFlow(WindowsInternalPlayerBackend.STABLE_MEDIAMP)
    val windowsInternalPlayerBackend: StateFlow<WindowsInternalPlayerBackend> =
        _windowsInternalPlayerBackend.asStateFlow()

    fun seed(
        universalSearchEnabled: Boolean,
        videoUpscalerPreset: VideoUpscalerPreset,
        videoDecoderBackend: VideoDecoderBackend = VideoDecoderBackend.AUTO,
        displaySyncEnabled: Boolean = false,
        windowsInternalPlayerBackend: WindowsInternalPlayerBackend = WindowsInternalPlayerBackend.STABLE_MEDIAMP,
    ) {
        _universalSearchEnabled.value = universalSearchEnabled
        _videoUpscalerPreset.value = videoUpscalerPreset
        _videoDecoderBackend.value = videoDecoderBackend
        _displaySyncEnabled.value = displaySyncEnabled
        _windowsInternalPlayerBackend.value = windowsInternalPlayerBackend
    }

    fun setUniversalSearchEnabled(enabled: Boolean) {
        if (_universalSearchEnabled.value == enabled) return
        _universalSearchEnabled.value = enabled
    }

    fun setVideoUpscalerPreset(preset: VideoUpscalerPreset) {
        if (_videoUpscalerPreset.value == preset) return
        _videoUpscalerPreset.value = preset
    }

    fun setVideoDecoderBackend(backend: VideoDecoderBackend) {
        if (_videoDecoderBackend.value == backend) return
        _videoDecoderBackend.value = backend
    }

    fun setDisplaySyncEnabled(enabled: Boolean) {
        if (_displaySyncEnabled.value == enabled) return
        _displaySyncEnabled.value = enabled
    }

    fun setWindowsInternalPlayerBackend(backend: WindowsInternalPlayerBackend) {
        if (_windowsInternalPlayerBackend.value == backend) return
        _windowsInternalPlayerBackend.value = backend
    }
}
