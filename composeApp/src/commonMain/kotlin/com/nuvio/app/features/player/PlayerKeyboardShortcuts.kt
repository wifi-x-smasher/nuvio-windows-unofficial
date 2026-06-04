package com.nuvio.app.features.player

import androidx.compose.ui.input.key.Key

internal enum class PlayerKeyboardShortcut {
    TogglePlayback,
    SeekBackward,
    SeekForward,
    VolumeUp,
    VolumeDown,
    ToggleMute,
    CloseOrBack,
}

internal fun playerKeyboardShortcutFor(key: Key): PlayerKeyboardShortcut? = when (key) {
    Key.Spacebar,
    Key.Enter,
    Key.K -> PlayerKeyboardShortcut.TogglePlayback
    Key.DirectionLeft,
    Key.J -> PlayerKeyboardShortcut.SeekBackward
    Key.DirectionRight,
    Key.L -> PlayerKeyboardShortcut.SeekForward
    Key.DirectionUp -> PlayerKeyboardShortcut.VolumeUp
    Key.DirectionDown -> PlayerKeyboardShortcut.VolumeDown
    Key.M -> PlayerKeyboardShortcut.ToggleMute
    Key.Escape,
    Key.Backspace -> PlayerKeyboardShortcut.CloseOrBack
    else -> null
}
