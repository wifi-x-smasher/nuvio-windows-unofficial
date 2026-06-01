package com.nuvio.app

import java.awt.event.WindowEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowRecoveryTest {
    @Test
    fun repaintsWhenWindowReturnsToForeground() {
        assertTrue(isDesktopWindowRecoveryEvent(WindowEvent.WINDOW_ACTIVATED))
        assertTrue(isDesktopWindowRecoveryEvent(WindowEvent.WINDOW_DEICONIFIED))
        assertTrue(isDesktopWindowRecoveryEvent(WindowEvent.WINDOW_GAINED_FOCUS))
        assertFalse(isDesktopWindowRecoveryEvent(WindowEvent.WINDOW_CLOSED))
    }
}
