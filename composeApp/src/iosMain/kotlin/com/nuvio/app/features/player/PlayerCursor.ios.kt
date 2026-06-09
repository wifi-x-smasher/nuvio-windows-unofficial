package com.nuvio.app.features.player

import androidx.compose.ui.Modifier

// Touch platform — no mouse cursor to hide.
actual fun Modifier.playerHiddenCursor(hidden: Boolean): Modifier = this
