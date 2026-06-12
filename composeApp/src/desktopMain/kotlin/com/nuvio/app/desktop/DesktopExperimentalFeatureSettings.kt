package com.nuvio.app.desktop

import com.nuvio.app.core.desktop.DesktopPreferences
import com.nuvio.app.features.experimental.ExperimentalFeatureSettings
import com.nuvio.app.features.experimental.VideoDecoderBackend
import com.nuvio.app.features.experimental.VideoUpscalerPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object DesktopExperimentalFeatureSettings {
    private const val PREF_UNIVERSAL_SEARCH_ENABLED = "universal_search_enabled"
    private const val PREF_VIDEO_UPSCALER_PRESET = "video_upscaler_preset"
    private const val PREF_VIDEO_DECODER_BACKEND = "video_decoder_backend"
    private const val PREF_DISPLAY_SYNC_ENABLED = "display_sync_enabled"

    private val prefs by lazy { DesktopPreferences("experimental_features") }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true

        ExperimentalFeatureSettings.seed(
            universalSearchEnabled = prefs.getBoolean(PREF_UNIVERSAL_SEARCH_ENABLED) ?: true,
            videoUpscalerPreset = VideoUpscalerPreset.fromId(
                prefs.getString(PREF_VIDEO_UPSCALER_PRESET),
            ),
            videoDecoderBackend = VideoDecoderBackend.fromId(
                prefs.getString(PREF_VIDEO_DECODER_BACKEND),
            ),
            displaySyncEnabled = prefs.getBoolean(PREF_DISPLAY_SYNC_ENABLED) ?: false,
        )

        scope.launch {
            ExperimentalFeatureSettings.universalSearchEnabled.collect { enabled ->
                prefs.putBoolean(PREF_UNIVERSAL_SEARCH_ENABLED, enabled)
            }
        }
        scope.launch {
            ExperimentalFeatureSettings.videoUpscalerPreset.collect { preset ->
                prefs.putString(PREF_VIDEO_UPSCALER_PRESET, preset.id)
            }
        }
        scope.launch {
            ExperimentalFeatureSettings.videoDecoderBackend.collect { backend ->
                prefs.putString(PREF_VIDEO_DECODER_BACKEND, backend.id)
            }
        }
        scope.launch {
            ExperimentalFeatureSettings.displaySyncEnabled.collect { enabled ->
                prefs.putBoolean(PREF_DISPLAY_SYNC_ENABLED, enabled)
            }
        }
    }
}
