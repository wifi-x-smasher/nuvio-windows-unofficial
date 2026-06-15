package com.nuvio.app

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopRendererSelectionTest {
    @Test
    fun defaultsToOpenGlWhenNoRendererIsRequested() {
        assertEquals("OPENGL", selectDesktopRenderer(requested = null))
    }

    @Test
    fun acceptsDirect3dOnlyWhenExplicitlyRequested() {
        assertEquals("DIRECT3D", selectDesktopRenderer(requested = "DIRECT3D"))
        assertEquals("DIRECT3D", selectDesktopRenderer(requested = "direct3d"))
    }

    @Test
    fun acceptsSoftwareForDiagnosticsAndFallback() {
        assertEquals("SOFTWARE", selectDesktopRenderer(requested = "SOFTWARE"))
    }

    @Test
    fun unknownRendererFallsBackToOpenGl() {
        assertEquals("OPENGL", selectDesktopRenderer(requested = "METAL"))
        assertEquals("OPENGL", selectDesktopRenderer(requested = ""))
    }
}
