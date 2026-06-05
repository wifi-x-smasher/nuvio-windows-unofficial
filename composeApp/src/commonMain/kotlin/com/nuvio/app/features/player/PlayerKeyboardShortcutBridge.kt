package com.nuvio.app.features.player

internal object PlayerKeyboardShortcutBridge {
    private var handler: ((PlayerKeyboardShortcut) -> Boolean)? = null
    private val activeStateListeners = mutableSetOf<(Boolean) -> Unit>()

    val isActive: Boolean
        get() = handler != null

    fun register(handler: (PlayerKeyboardShortcut) -> Boolean) {
        this.handler = handler
        notifyActiveState()
    }

    fun unregister() {
        handler = null
        notifyActiveState()
    }

    fun dispatch(shortcut: PlayerKeyboardShortcut): Boolean =
        handler?.invoke(shortcut) ?: false

    fun observeActiveState(listener: (Boolean) -> Unit): () -> Unit {
        activeStateListeners += listener
        listener(isActive)
        return {
            activeStateListeners -= listener
        }
    }

    private fun notifyActiveState() {
        val active = isActive
        activeStateListeners.toList().forEach { listener ->
            listener(active)
        }
    }
}
