package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize

@Composable
actual fun LockPlayerToLandscape() = Unit

@Composable
actual fun EnterImmersivePlayerMode(keepScreenAwake: Boolean) = Unit

@Composable
actual fun ManagePlayerPictureInPicture(
    isPlaying: Boolean,
    playerSize: IntSize,
) = Unit

@Composable
actual fun rememberPlayerGestureController(): PlayerGestureController? =
    remember {
        object : PlayerGestureController {
            override fun currentBrightness(): Float? =
                null

            override fun setBrightness(level: Float): Float? =
                null

            override fun currentVolume(): PlayerAudioLevel? =
                DesktopVlcPlayerBridge.currentVolume()

            override fun setVolume(level: Float): PlayerAudioLevel? =
                DesktopVlcPlayerBridge.setVolume(level)
        }
    }
