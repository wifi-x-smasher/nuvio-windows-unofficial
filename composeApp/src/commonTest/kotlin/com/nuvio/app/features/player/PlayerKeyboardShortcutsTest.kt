package com.nuvio.app.features.player

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerKeyboardShortcutsTest {
    @Test
    fun mapsDesktopPlaybackShortcuts() {
        assertEquals(PlayerKeyboardShortcut.TogglePlayback, playerKeyboardShortcutFor(Key.Spacebar))
        assertEquals(PlayerKeyboardShortcut.TogglePlayback, playerKeyboardShortcutFor(Key.Enter))
        assertEquals(PlayerKeyboardShortcut.TogglePlayback, playerKeyboardShortcutFor(Key.K))
        assertEquals(PlayerKeyboardShortcut.SeekBackward, playerKeyboardShortcutFor(Key.DirectionLeft))
        assertEquals(PlayerKeyboardShortcut.SeekBackward, playerKeyboardShortcutFor(Key.J))
        assertEquals(PlayerKeyboardShortcut.SeekForward, playerKeyboardShortcutFor(Key.DirectionRight))
        assertEquals(PlayerKeyboardShortcut.SeekForward, playerKeyboardShortcutFor(Key.L))
    }

    @Test
    fun mapsDesktopVolumeAndCloseShortcuts() {
        assertEquals(PlayerKeyboardShortcut.VolumeUp, playerKeyboardShortcutFor(Key.DirectionUp))
        assertEquals(PlayerKeyboardShortcut.VolumeDown, playerKeyboardShortcutFor(Key.DirectionDown))
        assertEquals(PlayerKeyboardShortcut.ToggleMute, playerKeyboardShortcutFor(Key.M))
        assertEquals(PlayerKeyboardShortcut.CloseOrBack, playerKeyboardShortcutFor(Key.Escape))
        assertEquals(PlayerKeyboardShortcut.CloseOrBack, playerKeyboardShortcutFor(Key.Backspace))
        assertNull(playerKeyboardShortcutFor(Key.Tab))
    }
}
