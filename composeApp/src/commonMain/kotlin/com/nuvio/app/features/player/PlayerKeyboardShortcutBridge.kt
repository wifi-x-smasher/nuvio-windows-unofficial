package com.nuvio.app.features.player

internal object PlayerKeyboardShortcutBridge {
    private var handler: ((PlayerKeyboardShortcut) -> Boolean)? = null

    fun register(handler: (PlayerKeyboardShortcut) -> Boolean) {
        this.handler = handler
    }

    fun unregister() {
        handler = null
    }

    fun dispatch(shortcut: PlayerKeyboardShortcut): Boolean =
        handler?.invoke(shortcut) ?: false
}
