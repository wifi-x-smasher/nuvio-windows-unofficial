package com.nuvio.app.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of what the internal player is currently showing. Published by the player and consumed
 * by platform integrations (currently desktop Discord Rich Presence). Position/duration are in ms.
 */
data class NowPlayingInfo(
    val title: String,
    val subtitle: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPaused: Boolean = false,
    /** Public https poster URL for the current title, shown as the Discord large image when present. */
    val posterUrl: String? = null,
)

/**
 * Platform-agnostic "now playing" channel. The player calls [update] while a stream is playing and
 * [clear] when it stops/closes. Consumers observe [state]; platforms that don't integrate simply
 * never read it, so this is a no-op cost on mobile.
 */
object NowPlayingBridge {
    private val _state = MutableStateFlow<NowPlayingInfo?>(null)
    val state: StateFlow<NowPlayingInfo?> = _state.asStateFlow()

    fun update(info: NowPlayingInfo) {
        _state.value = info
    }

    fun clear() {
        _state.value = null
    }
}
