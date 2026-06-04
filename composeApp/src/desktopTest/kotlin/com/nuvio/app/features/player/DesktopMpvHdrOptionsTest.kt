package com.nuvio.app.features.player

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
}
