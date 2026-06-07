package com.nuvio.app.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop-only "Show what I'm watching on Discord" toggle (Rich Presence).
 *
 * Lives in commonMain purely so the settings UI can bind to it, but it is only seeded, persisted,
 * and observed by the desktop Discord service (see DiscordRichPresence). On other platforms the
 * settings row is hidden, nothing seeds/observes this, and it stays at the default (false).
 *
 * Default is OFF (opt-in) by design — playing titles are not broadcast unless the user enables it.
 */
object DiscordPresenceSettings {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        if (_enabled.value == value) return
        _enabled.value = value
    }
}
