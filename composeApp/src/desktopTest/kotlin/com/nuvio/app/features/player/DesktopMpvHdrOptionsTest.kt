package com.nuvio.app.features.player

import com.nuvio.app.features.experimental.VideoUpscalerPreset
import com.nuvio.app.features.player.desktop.mpv.DesktopMpvDecoderOptions
import com.nuvio.app.features.player.desktop.mpv.DesktopMpvUpscalerOptions
import com.nuvio.app.features.player.desktop.mpv.DesktopMpvVideoOptionProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMpvHdrOptionsTest {
    @Test
    fun exposesAuditableWindowsHdrOptions() {
        val options = DesktopMpvVideoOptionProfile.options

        assertEquals("auto-copy-safe", options["hwdec"])
        assertEquals("yes", options["vd-lavc-software-fallback"])
        assertEquals("rgba8", options["fbo-format"])
        assertEquals("no", options["dither-depth"])
        assertEquals("audio", options["video-sync"])
        assertEquals("0.0", options["video-timing-offset"])
    }

    @Test
    fun displaySyncEnabledOverridesVideoSyncAndAddsInterpolation() {
        val options = DesktopMpvVideoOptionProfile.optionsFor(
            upscalerPreset = VideoUpscalerPreset.OFF,
            decoderPriority = 1,
            displaySyncEnabled = true,
        )

        assertEquals("display-resample", options["video-sync"])
        assertEquals("yes", options["interpolation"])
        assertEquals("oversample", options["tscale"])
    }

    @Test
    fun displaySyncDisabledKeepsAudioSync() {
        val options = DesktopMpvVideoOptionProfile.optionsFor(
            upscalerPreset = VideoUpscalerPreset.OFF,
            decoderPriority = 1,
            displaySyncEnabled = false,
        )

        assertEquals("audio", options["video-sync"])
        assertFalse(options.containsKey("interpolation"))
        assertFalse(options.containsKey("tscale"))
    }

    @Test
    fun documentsCurrentRendererBackendLimitation() {
        assertFalse(DesktopMpvVideoOptionProfile.canRequestGpuNextRenderBackend)
        assertTrue(DesktopMpvVideoOptionProfile.rendererLimitationNote.contains("OpenGL/libmpv"))
    }

    @Test
    fun exposesFiveExperimentalUpscalerPresets() {
        assertEquals(5, DesktopMpvUpscalerOptions.presetCount)
        val highQuality = DesktopMpvVideoOptionProfile.optionsFor(VideoUpscalerPreset.HIGH_QUALITY, 1)
        assertEquals("auto-copy-safe", highQuality["hwdec"])
        assertEquals("ewa_lanczossharp", highQuality["scale"])
        assertEquals("yes", highQuality["sigmoid-upscaling"])
    }

    @Test
    fun offPresetResetsUpscalerRuntimeOptions() {
        val off = DesktopMpvVideoOptionProfile.optionsFor(VideoUpscalerPreset.OFF, 1)
        assertEquals("lanczos", off["scale"])
        assertEquals("no", off["deband"])
        assertEquals("no", off["sigmoid-upscaling"])
    }

    @Test
    fun decoderPriorityMapsToWindowsMpvHwdecOptions() {
        val deviceOnly = DesktopMpvDecoderOptions.optionsFor(0)
        assertEquals("auto-copy", deviceOnly["hwdec"])
        assertEquals("yes", deviceOnly["vd-lavc-software-fallback"])

        val preferDevice = DesktopMpvDecoderOptions.optionsFor(1)
        assertEquals("auto-copy-safe", preferDevice["hwdec"])
        assertEquals("yes", preferDevice["vd-lavc-software-fallback"])

        val preferApp = DesktopMpvDecoderOptions.optionsFor(2)
        assertEquals("no", preferApp["hwdec"])
        assertEquals("yes", preferApp["vd-lavc-software-fallback"])
    }

    @Test
    fun upscalerPresetsDoNotOverrideDecoderPriority() {
        val preferAppHighQuality = DesktopMpvVideoOptionProfile.optionsFor(VideoUpscalerPreset.HIGH_QUALITY, 2)

        assertEquals("no", preferAppHighQuality["hwdec"])
        assertEquals("yes", preferAppHighQuality["vd-lavc-software-fallback"])
        assertEquals("ewa_lanczossharp", preferAppHighQuality["scale"])
    }
}
