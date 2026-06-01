package com.nuvio.app.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val MaxCachedGifAnimations = 12
private const val MaxGifBytes = 15 * 1024 * 1024
private const val GifConnectTimeoutMs = 8_000
private const val GifReadTimeoutMs = 12_000
private const val DefaultGifFrameDelayMs = 90
private val gifDecodeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val gifCacheLock = Any()
private val gifAnimationCache = mutableMapOf<String, DesktopGifAnimation>()
private val gifAnimationCacheOrder = mutableListOf<String>()
private val gifAnimationInFlight = mutableMapOf<String, Deferred<DesktopGifAnimation?>>()

@Composable
internal actual fun CollectionCardRemoteImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
    animateIfPossible: Boolean,
) {
    if (animateIfPossible) {
        var animation by remember(imageUrl) { mutableStateOf(cachedGifAnimation(imageUrl)) }

        LaunchedEffect(imageUrl) {
            animation = loadGifAnimation(imageUrl)
        }

        animation?.takeIf { it.frames.isNotEmpty() }?.let { gifAnimation ->
            var frameIndex by remember(gifAnimation) { mutableIntStateOf(0) }
            LaunchedEffect(gifAnimation) {
                frameIndex = 0
                while (gifAnimation.frames.size > 1) {
                    val delayMs = gifAnimation.frameDelaysMs
                        .getOrElse(frameIndex) { DefaultGifFrameDelayMs }
                        .coerceAtLeast(30)
                    delay(delayMs.toLong())
                    frameIndex = (frameIndex + 1) % gifAnimation.frames.size
                }
            }
            Image(
                bitmap = gifAnimation.frames.getOrElse(frameIndex) { gifAnimation.frames.first() },
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
            )
            return
        }
    }

    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

private data class DesktopGifAnimation(
    val frames: List<ImageBitmap>,
    val frameDelaysMs: List<Int>,
)

private fun cachedGifAnimation(imageUrl: String): DesktopGifAnimation? =
    synchronized(gifCacheLock) {
        val animation = gifAnimationCache[imageUrl] ?: return@synchronized null
        gifAnimationCacheOrder.remove(imageUrl)
        gifAnimationCacheOrder.add(imageUrl)
        animation
    }

private fun storeGifAnimation(imageUrl: String, animation: DesktopGifAnimation) {
    synchronized(gifCacheLock) {
        gifAnimationCache[imageUrl] = animation
        gifAnimationCacheOrder.remove(imageUrl)
        gifAnimationCacheOrder.add(imageUrl)

        while (gifAnimationCacheOrder.size > MaxCachedGifAnimations) {
            val eldestKey = gifAnimationCacheOrder.removeFirstOrNull() ?: break
            gifAnimationCache.remove(eldestKey)
        }
    }
}

private suspend fun loadGifAnimation(imageUrl: String): DesktopGifAnimation? {
    cachedGifAnimation(imageUrl)?.let { return it }

    val request = synchronized(gifCacheLock) {
        gifAnimationInFlight[imageUrl] ?: gifDecodeScope.async {
            runCatching {
                readGifBytes(imageUrl)?.let(::decodeGifAnimation)
            }.getOrNull()
        }.also { gifAnimationInFlight[imageUrl] = it }
    }

    val animation = try {
        request.await()
    } finally {
        synchronized(gifCacheLock) {
            if (gifAnimationInFlight[imageUrl] === request) {
                gifAnimationInFlight.remove(imageUrl)
            }
        }
    }

    if (animation != null) {
        withContext(Dispatchers.Main.immediate) {
            storeGifAnimation(imageUrl, animation)
        }
    }
    return animation
}

private fun decodeGifAnimation(bytes: ByteArray): DesktopGifAnimation? {
    val reader = ImageIO.getImageReadersByFormatName("gif").asSequence().firstOrNull() ?: return null
    return ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
        reader.input = input
        try {
            val frameCount = reader.getNumImages(true)
            if (frameCount <= 0) return null
            val frames = mutableListOf<ImageBitmap>()
            val delays = mutableListOf<Int>()
            for (index in 0 until frameCount) {
                val bitmap = reader.read(index).toPngImageBitmap() ?: continue
                frames.add(bitmap)
                delays.add(reader.getImageMetadata(index).gifFrameDelayMs())
            }
            DesktopGifAnimation(
                frames = frames,
                frameDelaysMs = delays,
            ).takeIf { it.frames.isNotEmpty() }
        } finally {
            reader.dispose()
        }
    }
}

private fun BufferedImage.toPngImageBitmap(): ImageBitmap? {
    val output = ByteArrayOutputStream()
    return if (ImageIO.write(this, "png", output)) {
        output.toByteArray().decodeToImageBitmap()
    } else {
        null
    }
}

private fun javax.imageio.metadata.IIOMetadata.gifFrameDelayMs(): Int {
    val root = runCatching {
        getAsTree("javax_imageio_gif_image_1.0")
    }.getOrNull() as? IIOMetadataNode ?: return DefaultGifFrameDelayMs
    val graphicControl = root.findFirst("GraphicControlExtension") ?: return DefaultGifFrameDelayMs
    val delayHundredths = graphicControl.getAttribute("delayTime").toIntOrNull() ?: return DefaultGifFrameDelayMs
    return (delayHundredths * 10).takeIf { it > 0 } ?: DefaultGifFrameDelayMs
}

private fun IIOMetadataNode.findFirst(name: String): IIOMetadataNode? {
    if (nodeName == name) return this
    for (index in 0 until length) {
        val child = item(index) as? IIOMetadataNode ?: continue
        child.findFirst(name)?.let { return it }
    }
    return null
}

private fun readGifBytes(imageUrl: String): ByteArray? {
    val connection = URI.create(imageUrl).toURL().openConnection().apply {
        connectTimeout = GifConnectTimeoutMs
        readTimeout = GifReadTimeoutMs
    }
    val declaredLength = connection.contentLengthLong
    if (declaredLength > MaxGifBytes) return null

    return connection.getInputStream().use { input ->
        input.readBytesWithLimit(MaxGifBytes)
    }
}

private fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray? {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream()
    var totalBytes = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        totalBytes += read
        if (totalBytes > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
