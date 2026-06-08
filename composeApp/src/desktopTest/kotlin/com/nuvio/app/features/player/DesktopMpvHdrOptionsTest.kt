package com.nuvio.app.features.player

import com.nuvio.app.features.experimental.VideoUpscalerPreset
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

        assertEquals("auto-safe", options["hwdec"])
        assertEquals("rgba8", options["fbo-format"])
        assertEquals("no", options["dither-depth"])
        assertEquals("audio", options["video-sync"])
        assertEquals("0.0", options["video-timing-offset"])
    }

    @Test
    fun documentsCurrentRendererBackendLimitation() {
        assertFalse(DesktopMpvVideoOptionProfile.canRequestGpuNextRenderBackend)
        assertTrue(DesktopMpvVideoOptionProfile.rendererLimitationNote.contains("OpenGL/libmpv"))
    }

    @Test
    fun exposesFiveExperimentalUpscalerPresets() {
        assertEquals(5, DesktopMpvUpscalerOptions.presetCount)
        val highQuality = DesktopMpvVideoOptionProfile.optionsFor(VideoUpscalerPreset.HIGH_QUALITY)
        assertEquals("auto-safe", highQuality["hwdec"])
        assertEquals("ewa_lanczossharp", highQuality["scale"])
        assertEquals("yes", highQuality["sigmoid-upscaling"])
    }

    @Test
    fun offPresetResetsUpscalerRuntimeOptions() {
        val off = DesktopMpvVideoOptionProfile.optionsFor(VideoUpscalerPreset.OFF)
        assertEquals("lanczos", off["scale"])
        assertEquals("no", off["deband"])
        assertEquals("no", off["sigmoid-upscaling"])
    }
}
