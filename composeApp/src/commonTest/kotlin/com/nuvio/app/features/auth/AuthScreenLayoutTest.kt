package com.nuvio.app.features.auth

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthScreenLayoutTest {
    @Test
    fun desktopWidthUsesTvSplitLayout() {
        val layout = authScreenLayoutFor(1280.dp)

        assertTrue(layout.useSplitLayout)
        assertTrue(layout.formMaxWidth <= 440.dp)
        assertTrue(layout.contentMaxWidth <= 1040.dp)
    }

    @Test
    fun compactWidthKeepsSingleColumnLayout() {
        val layout = authScreenLayoutFor(640.dp)

        assertFalse(layout.useSplitLayout)
        assertTrue(layout.formMaxWidth <= 460.dp)
    }
}
