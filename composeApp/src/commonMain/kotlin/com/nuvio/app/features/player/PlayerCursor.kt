package com.nuvio.app.features.player

import androidx.compose.ui.Modifier

/**
 * Hides the mouse cursor over the player while [hidden] is true (desktop only) — used to auto-hide
 * the pointer after a few idle seconds during playback. On touch platforms this is a no-op.
 */
expect fun Modifier.playerHiddenCursor(hidden: Boolean): Modifier
