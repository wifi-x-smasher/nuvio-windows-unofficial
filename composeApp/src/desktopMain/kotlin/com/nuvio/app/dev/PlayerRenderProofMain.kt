package com.nuvio.app.dev

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.nuvio.app.features.player.DesktopVlcRuntime
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Image as SkiaImage
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

private const val SampleVideoUrl =
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

fun main() {
    System.setProperty("compose.layers.type", "COMPONENT")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Nuvio Player Render Proof",
        ) {
            MaterialTheme {
                PlayerRenderProofApp()
            }
        }
    }
}

@Composable
private fun PlayerRenderProofApp() {
    var frame by remember { mutableStateOf<ProofVideoFrame?>(null) }
    var status by remember { mutableStateOf("Idle") }
    var url by remember { mutableStateOf(SampleVideoUrl) }
    var headers by remember { mutableStateOf("User-Agent: NuvioWindowsProof/1.0") }
    val player = remember {
        CallbackVlcProofPlayer(
            onFrame = { nextFrame -> frame = nextFrame },
            onStatus = { nextStatus -> status = nextStatus },
        )
    }

    var playback by remember { mutableStateOf(ProofPlaybackSnapshot()) }

    LaunchedEffect(player) {
        while (isActive) {
            playback = player.snapshot()
            delay(500)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Milestone 1: Compose-owned video render proof",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    text = "If the overlay stays visible above moving video, the long-term player architecture is viable.",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Video or stream URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Optional HTTP headers, one per line") },
                    minLines = 2,
                    maxLines = 3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { player.play(url, headers) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00)),
                    ) {
                        Text("Play")
                    }
                    Button(onClick = player::pauseOrResume) {
                        Text(if (playback.isPlaying) "Pause" else "Resume")
                    }
                    Button(onClick = player::stop) {
                        Text("Stop")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF07050D), RoundedCornerShape(18.dp)),
            ) {
                val activeFrame = frame
                if (activeFrame != null) {
                    Image(
                        bitmap = remember(activeFrame.sequence) { activeFrame.toImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = "No video frame yet",
                        color = Color.White.copy(alpha = 0.52f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                PlayerProofOverlay(
                    status = status,
                    playback = playback,
                    onBack = player::stop,
                    onPause = player::pauseOrResume,
                    onSeekBackward = { player.seekBy(-10_000L) },
                    onSeekForward = { player.seekBy(10_000L) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PlayerProofOverlay(
    status: String,
    playback: ProofPlaybackSnapshot,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProofPill(text = "Nuvio overlay is Compose")
            ProofPill(text = status, maxLines = 1)
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ProofControlButton("-10", onSeekBackward)
            ProofControlButton(if (playback.isPlaying) "Pause" else "Play", onPause)
            ProofControlButton("+10", onSeekForward)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.52f))
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatMillis(playback.timeMs), color = Color.White, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = playback.position.coerceIn(0f, 1f),
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(formatMillis(playback.lengthMs), color = Color.White, fontSize = 13.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                ProofControlButton("Back / Stop", onBack)
            }
        }
    }
}

@Composable
private fun ProofPill(text: String, maxLines: Int = 2) {
    Text(
        text = text,
        color = Color.White,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ProofControlButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.92f),
            contentColor = Color.Black,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

private class CallbackVlcProofPlayer(
    private val onFrame: (ProofVideoFrame) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val frameSequence = AtomicLong(0)
    private var factory: MediaPlayerFactory? = null
    private var mediaPlayer: EmbeddedMediaPlayer? = null
    private var width = 0
    private var height = 0

    fun play(sourceUrl: String, headersText: String) {
        val sanitizedUrl = sourceUrl.trim()
        if (sanitizedUrl.isBlank()) {
            publishStatus("Paste a URL first")
            return
        }

        if (DesktopVlcRuntime.prepare().isFailure) {
            publishStatus("VLC runtime not available")
            return
        }

        val activePlayer = ensurePlayer()
        val options = parseVlcHttpOptions(headersText)
        publishStatus("Opening stream")
        activePlayer.controls().stop()
        val started = activePlayer.media().play(sanitizedUrl, *options.toTypedArray())
        publishStatus(if (started) "Playing via VLC callback renderer" else "VLC refused the stream")
    }

    fun pauseOrResume() {
        mediaPlayer?.controls()?.pause()
    }

    fun seekBy(deltaMs: Long) {
        mediaPlayer?.controls()?.skipTime(deltaMs)
    }

    fun stop() {
        mediaPlayer?.controls()?.stop()
        publishStatus("Stopped")
    }

    fun snapshot(): ProofPlaybackSnapshot {
        val activePlayer = mediaPlayer ?: return ProofPlaybackSnapshot()
        return runCatching {
            val length = activePlayer.status().length().coerceAtLeast(0L)
            val time = activePlayer.status().time().coerceAtLeast(0L)
            ProofPlaybackSnapshot(
                isPlaying = activePlayer.status().isPlaying,
                timeMs = time,
                lengthMs = length,
                position = activePlayer.status().position().takeIf { it.isFinite() } ?: 0f,
            )
        }.getOrDefault(ProofPlaybackSnapshot())
    }

    fun release() {
        runCatching { mediaPlayer?.controls()?.stop() }
        runCatching { mediaPlayer?.release() }
        runCatching { factory?.release() }
        mediaPlayer = null
        factory = null
    }

    private fun ensurePlayer(): EmbeddedMediaPlayer {
        mediaPlayer?.let { return it }

        val nextFactory = MediaPlayerFactory(
            "--no-video-title-show",
            "--no-snapshot-preview",
            "--quiet",
        )
        val nextPlayer = nextFactory.mediaPlayers().newEmbeddedMediaPlayer()
        val bufferFormatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                width = sourceWidth.coerceAtLeast(1)
                height = sourceHeight.coerceAtLeast(1)
                publishStatus("Video format ${width}x$height")
                return RV32BufferFormat(width, height)
            }

            override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
        }
        val renderCallback = RenderCallback { _: MediaPlayer, nativeBuffers: Array<ByteBuffer>, _: BufferFormat ->
            val activeWidth = width
            val activeHeight = height
            if (activeWidth <= 0 || activeHeight <= 0 || nativeBuffers.isEmpty()) return@RenderCallback

            val expectedSize = activeWidth * activeHeight * 4
            val buffer = nativeBuffers[0].duplicate()
            buffer.rewind()
            val bytes = ByteArray(minOf(expectedSize, buffer.remaining()))
            buffer.get(bytes)
            if (bytes.size != expectedSize) return@RenderCallback

            val nextFrame = ProofVideoFrame(
                width = activeWidth,
                height = activeHeight,
                bytes = bytes,
                sequence = frameSequence.incrementAndGet(),
            )
            SwingUtilities.invokeLater { onFrame(nextFrame) }
        }
        nextPlayer.videoSurface().set(
            nextFactory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true),
        )
        factory = nextFactory
        mediaPlayer = nextPlayer
        return nextPlayer
    }

    private fun publishStatus(message: String) {
        SwingUtilities.invokeLater { onStatus(message) }
    }
}

private data class ProofVideoFrame(
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
    val sequence: Long,
) {
    fun toImageBitmap() =
        SkiaImage.makeRaster(
            ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
            bytes,
            width * 4,
        ).toComposeImageBitmap()

    override fun equals(other: Any?): Boolean =
        other is ProofVideoFrame && sequence == other.sequence

    override fun hashCode(): Int = sequence.hashCode()
}

private data class ProofPlaybackSnapshot(
    val isPlaying: Boolean = false,
    val timeMs: Long = 0L,
    val lengthMs: Long = 0L,
    val position: Float = 0f,
)

private fun parseVlcHttpOptions(headersText: String): List<String> =
    headersText
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotBlank() && ':' in it }
        .map { header -> ":http-header=$header" }
        .toList()

private fun formatMillis(value: Long): String {
    val totalSeconds = (value / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
