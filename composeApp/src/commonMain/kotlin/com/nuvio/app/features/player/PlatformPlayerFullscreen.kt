package com.nuvio.app.features.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Bridge that lets the shared player UI trigger the platform window's fullscreen toggle.
 *
 * The desktop app registers a handler that drives its native fullscreen controller and keeps the
 * [isFullscreen] flag in sync. Platforms that don't register a handler (mobile, where the player is
 * already fullscreen) leave [toggleHandler] null, so the in-player fullscreen button stays hidden.
 *
 * Backed by Compose snapshot state so the player controls recompose when availability or state
 * changes.
 */
object PlatformPlayerFullscreen {
    var toggleHandler: (() -> Unit)? by mutableStateOf(null)
        private set

    var isFullscreen: Boolean by mutableStateOf(false)
        private set

    fun registerHandler(handler: (() -> Unit)?) {
        toggleHandler = handler
        if (handler == null) isFullscreen = false
    }

    fun updateFullscreen(value: Boolean) {
        isFullscreen = value
    }

    fun toggle() {
        toggleHandler?.invoke()
    }
}
