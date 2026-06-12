package com.nuvio.app.core.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NuvioTvNavigationTest {
    @Test
    fun selectKeysMatchEnterNumpadEnterAndSpaceOnly() {
        assertTrue(Key.Enter.isNuvioTvSelectKey())
        assertTrue(Key.NumPadEnter.isNuvioTvSelectKey())
        assertTrue(Key.Spacebar.isNuvioTvSelectKey())

        assertFalse(Key.DirectionLeft.isNuvioTvSelectKey())
        assertFalse(Key.Backspace.isNuvioTvSelectKey())
        assertFalse(Key.Escape.isNuvioTvSelectKey())
    }

    @Test
    fun backKeysMatchBackspaceAndEscapeOnly() {
        assertTrue(Key.Backspace.isNuvioTvBackKey())
        assertTrue(Key.Escape.isNuvioTvBackKey())

        assertFalse(Key.Enter.isNuvioTvBackKey())
        assertFalse(Key.Spacebar.isNuvioTvBackKey())
        assertFalse(Key.DirectionLeft.isNuvioTvBackKey())
    }

    @Test
    fun directionKeysMapToComposeFocusDirections() {
        assertEquals(FocusDirection.Left, Key.DirectionLeft.nuvioTvFocusDirection())
        assertEquals(FocusDirection.Right, Key.DirectionRight.nuvioTvFocusDirection())
        assertEquals(FocusDirection.Up, Key.DirectionUp.nuvioTvFocusDirection())
        assertEquals(FocusDirection.Down, Key.DirectionDown.nuvioTvFocusDirection())

        assertEquals(null, Key.Enter.nuvioTvFocusDirection())
        assertEquals(null, Key.Spacebar.nuvioTvFocusDirection())
        assertEquals(null, Key.Backspace.nuvioTvFocusDirection())
    }

    @Test
    fun wheelScrollUsesVerticalWheelAsHorizontalIntent() {
        assertEquals(72f, nuvioHorizontalWheelPixels(deltaX = 0f, deltaY = 1f))
        assertEquals(-72f, nuvioHorizontalWheelPixels(deltaX = 0f, deltaY = -1f))
        assertEquals(36f, nuvioHorizontalWheelPixels(deltaX = 0.5f, deltaY = 0f))
    }

    @Test
    fun wheelScrollCanIgnoreVerticalWheelIntentForPosterShelves() {
        assertEquals(0f, nuvioHorizontalWheelPixels(deltaX = 0f, deltaY = 1f, allowVerticalWheel = false))
        assertEquals(0f, nuvioHorizontalWheelPixels(deltaX = 0f, deltaY = -1f, allowVerticalWheel = false))
        assertEquals(36f, nuvioHorizontalWheelPixels(deltaX = 0.5f, deltaY = 1f, allowVerticalWheel = false))
        assertEquals(-36f, nuvioHorizontalWheelPixels(deltaX = -0.5f, deltaY = 1f, allowVerticalWheel = false))
    }

    @Test
    fun wheelScrollConsumesOnlyWhenRowCanMoveInThatDirection() {
        assertTrue(nuvioShouldConsumeHorizontalWheel(deltaPixels = 72f, canScrollBackward = false, canScrollForward = true))
        assertTrue(nuvioShouldConsumeHorizontalWheel(deltaPixels = -72f, canScrollBackward = true, canScrollForward = false))

        assertFalse(nuvioShouldConsumeHorizontalWheel(deltaPixels = 72f, canScrollBackward = true, canScrollForward = false))
        assertFalse(nuvioShouldConsumeHorizontalWheel(deltaPixels = -72f, canScrollBackward = false, canScrollForward = true))
        assertFalse(nuvioShouldConsumeHorizontalWheel(deltaPixels = 0f, canScrollBackward = true, canScrollForward = true))
    }
}
