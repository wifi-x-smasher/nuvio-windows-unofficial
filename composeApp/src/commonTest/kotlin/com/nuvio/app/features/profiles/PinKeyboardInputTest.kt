package com.nuvio.app.features.profiles

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinKeyboardInputTest {
    @Test
    fun supportsNumberRowAndNumpadDigits() {
        assertEquals("0", pinDigitForKey(Key.Zero))
        assertEquals("0", pinDigitForKey(Key.NumPad0))
        assertEquals("7", pinDigitForKey(Key.Seven))
        assertEquals("7", pinDigitForKey(Key.NumPad7))
        assertNull(pinDigitForKey(Key.DirectionLeft))
    }

    @Test
    fun supportsDesktopPinEditingKeys() {
        assertTrue(isPinBackspaceKey(Key.Backspace))
        assertTrue(isPinBackspaceKey(Key.Delete))
        assertTrue(isPinCancelKey(Key.Escape))
        assertTrue(isPinConfirmKey(Key.Enter))
        assertTrue(isPinConfirmKey(Key.NumPadEnter))
        assertFalse(isPinBackspaceKey(Key.Enter))
    }
}
