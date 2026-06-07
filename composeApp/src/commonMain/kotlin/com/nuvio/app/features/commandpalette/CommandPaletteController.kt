package com.nuvio.app.features.commandpalette

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Visibility state for the desktop command palette (Ctrl+K).
 *
 * Toggled by the global key dispatcher and the top-bar button. The overlay itself is rendered inside
 * `App()` so it can drive the real `NavController` and tab state. Desktop-only in practice — nothing
 * shows it on other platforms.
 */
object CommandPaletteController {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun show() {
        _visible.value = true
    }

    fun hide() {
        _visible.value = false
    }

    fun toggle() {
        _visible.value = !_visible.value
    }
}
