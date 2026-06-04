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

        assertEquals("rgba16f", options["fbo-format"])
        assertEquals("bt.2390", options["tone-mapping"])
        assertEquals("yes", options["hdr-compute-peak"])
        assertEquals("yes", options["target-colorspace-hint"])
        assertEquals("yes", options["icc-profile-auto"])
    }

    @Test
    fun documentsCurrentRendererBackendLimitation() {
        assertFalse(DesktopMpvVideoOptionProfile.canRequestGpuNextRenderBackend)
        assertTrue(DesktopMpvVideoOptionProfile.rendererLimitationNote.contains("MPV_RENDER_PARAM_BACKEND"))
    }
}
