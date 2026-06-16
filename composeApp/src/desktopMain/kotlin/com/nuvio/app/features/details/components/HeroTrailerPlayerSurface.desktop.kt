package com.nuvio.app.features.details.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.player.desktop.DesktopPlayerBackendFactory
import com.nuvio.app.features.player.desktop.DesktopPlayerSurfaceHost

@Composable
actual fun HeroTrailerPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    playWhenReady: Boolean,
    muted: Boolean,
    modifier: Modifier,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
) {
    var controller by remember(sourceUrl, sourceAudioUrl) { mutableStateOf<PlayerEngineController?>(null) }
    var readyReported by remember(sourceUrl, sourceAudioUrl) { mutableStateOf(false) }
    var endedReported by remember(sourceUrl, sourceAudioUrl) { mutableStateOf(false) }
    val latestOnReady = rememberUpdatedState(onReady)
    val latestOnEnded = rememberUpdatedState(onEnded)
    val latestOnError = rememberUpdatedState(onError)

    LaunchedEffect(controller, muted) {
        controller?.setVolume(if (muted) 0f else 1f)
    }

    DesktopPlayerSurfaceHost(
        sourceUrl = sourceUrl,
        sourceAudioUrl = sourceAudioUrl,
        sourceHeaders = emptyMap(),
        sourceResponseHeaders = emptyMap(),
        modifier = modifier,
        playWhenReady = playWhenReady,
        resizeMode = PlayerResizeMode.Zoom,
        onControllerReady = { nextController ->
            controller = nextController
            nextController.setVolume(if (muted) 0f else 1f)
        },
        onSnapshot = { snapshot ->
            if (!readyReported && (snapshot.isPlaying || (!snapshot.isLoading && snapshot.durationMs > 0L))) {
                readyReported = true
                latestOnReady.value()
            }
            if (!endedReported && snapshot.isEnded) {
                endedReported = true
                latestOnEnded.value()
            }
        },
        onError = { message ->
            if (!message.isNullOrBlank()) {
                latestOnError.value()
            }
        },
        backendFactory = { DesktopPlayerBackendFactory.createStableMpvBackend("hero-trailer") },
    )
}
