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
import com.nuvio.app.core.diagnostics.AppDiagnostics
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
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

        return
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

private data class GifFrameMetadata(
    val delayMs: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val disposalMethod: String,
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
            }.onFailure { throwable ->
                AppDiagnostics.error(
                    event = "home.collection.gif_load_failed",
                    throwable = throwable,
                    details = mapOf("urlHost" to runCatching { URI.create(imageUrl).host }.getOrNull()),
                )
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
            val canvasSize = reader.gifCanvasSize(frameCount)
                ?: return null
            val canvas = BufferedImage(canvasSize.first, canvasSize.second, BufferedImage.TYPE_INT_ARGB)
            val frames = mutableListOf<ImageBitmap>()
            val delays = mutableListOf<Int>()
            for (index in 0 until frameCount) {
                val frame = reader.read(index).toArgbImage()
                val metadata = reader.getImageMetadata(index).gifFrameMetadata(frame)
                val previousCanvas = if (metadata.disposalMethod.equals("restoreToPrevious", ignoreCase = true)) {
                    canvas.deepCopy()
                } else {
                    null
                }

                canvas.createGraphics().use { graphics ->
                    graphics.drawImage(frame, metadata.left, metadata.top, null)
                }

                canvas.deepCopy().toPngImageBitmap()?.let { bitmap ->
                    frames.add(bitmap)
                    delays.add(metadata.delayMs)
                }

                when {
                    metadata.disposalMethod.equals("restoreToBackgroundColor", ignoreCase = true) -> {
                        canvas.createGraphics().use { graphics ->
                            graphics.composite = AlphaComposite.Clear
                            graphics.fillRect(metadata.left, metadata.top, metadata.width, metadata.height)
                        }
                    }
                    metadata.disposalMethod.equals("restoreToPrevious", ignoreCase = true) && previousCanvas != null -> {
                        previousCanvas.copyInto(canvas)
                    }
                }
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

private fun javax.imageio.ImageReader.gifCanvasSize(frameCount: Int): Pair<Int, Int>? {
    streamGifCanvasSize()?.let { return it }
    var width = 0
    var height = 0
    for (index in 0 until frameCount) {
        val frame = runCatching { read(index) }.getOrNull() ?: continue
        val metadata = getImageMetadata(index).gifFrameMetadata(frame)
        width = maxOf(width, metadata.left + metadata.width)
        height = maxOf(height, metadata.top + metadata.height)
    }
    return if (width > 0 && height > 0) width to height else null
}

private fun javax.imageio.ImageReader.streamGifCanvasSize(): Pair<Int, Int>? {
    val root = runCatching {
        streamMetadata?.getAsTree("javax_imageio_gif_stream_1.0")
    }.getOrNull() as? IIOMetadataNode ?: return null
    val descriptor = root.findFirst("LogicalScreenDescriptor") ?: return null
    val width = descriptor.getAttribute("logicalScreenWidth").toIntOrNull() ?: return null
    val height = descriptor.getAttribute("logicalScreenHeight").toIntOrNull() ?: return null
    return if (width > 0 && height > 0) width to height else null
}

private fun BufferedImage.toPngImageBitmap(): ImageBitmap? {
    val output = ByteArrayOutputStream()
    return if (ImageIO.write(this, "png", output)) {
        output.toByteArray().decodeToImageBitmap()
    } else {
        null
    }
}

private fun javax.imageio.metadata.IIOMetadata.gifFrameMetadata(frame: BufferedImage): GifFrameMetadata {
    val root = runCatching {
        getAsTree("javax_imageio_gif_image_1.0")
    }.getOrNull() as? IIOMetadataNode
    val descriptor = root?.findFirst("ImageDescriptor")
    val graphicControl = root?.findFirst("GraphicControlExtension")
    val delayHundredths = graphicControl
        ?.getAttribute("delayTime")
        ?.toIntOrNull()
    return GifFrameMetadata(
        delayMs = (delayHundredths?.times(10))?.takeIf { it > 0 } ?: DefaultGifFrameDelayMs,
        left = descriptor?.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0,
        top = descriptor?.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0,
        width = descriptor?.getAttribute("imageWidth")?.toIntOrNull() ?: frame.width,
        height = descriptor?.getAttribute("imageHeight")?.toIntOrNull() ?: frame.height,
        disposalMethod = graphicControl?.getAttribute("disposalMethod").orEmpty(),
    )
}

private fun BufferedImage.toArgbImage(): BufferedImage {
    if (type == BufferedImage.TYPE_INT_ARGB) return this
    val converted = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    converted.createGraphics().use { graphics ->
        graphics.drawImage(this, 0, 0, null)
    }
    return converted
}

private fun BufferedImage.deepCopy(): BufferedImage {
    val copy = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    copyInto(copy)
    return copy
}

private fun BufferedImage.copyInto(target: BufferedImage) {
    target.createGraphics().use { graphics ->
        graphics.composite = AlphaComposite.Clear
        graphics.fillRect(0, 0, target.width, target.height)
        graphics.composite = AlphaComposite.SrcOver
        graphics.drawImage(this, 0, 0, null)
    }
}

private inline fun java.awt.Graphics2D.use(block: (java.awt.Graphics2D) -> Unit) {
    try {
        block(this)
    } finally {
        dispose()
    }
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
        setRequestProperty("User-Agent", "NuvioWindows")
    }
    if (connection is HttpURLConnection) {
        connection.instanceFollowRedirects = true
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
