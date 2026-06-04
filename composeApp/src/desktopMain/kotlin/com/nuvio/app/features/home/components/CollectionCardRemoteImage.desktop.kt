package com.nuvio.app.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import com.nuvio.app.core.diagnostics.AppDiagnostics
import java.awt.AlphaComposite
import java.awt.RenderingHints
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val DefaultGifDelayCentiseconds = 10
private const val DecodeSizeBucketPx = 32
private const val FallbackDecodeDimensionPx = 360
private const val MaxDecodedGifEntries = 3
private const val MaxDecodedDimensionPx = 1920
private const val MaxGifSourceBytes = 16L * 1024 * 1024
private const val MaxDecodedGifBytes = 64L * 1024 * 1024
private const val MaxDecodedGifBytesTotal = 128L * 1024 * 1024
private const val MaxLogicalGifPixels = 4096L * 4096L
private const val GifConnectTimeoutMs = 8_000
private const val GifReadTimeoutMs = 12_000

private data class DesktopGifCacheKey(
    val url: String,
    val widthPx: Int,
    val heightPx: Int,
)

private data class GifDecodeTarget(
    val widthPx: Int,
    val heightPx: Int,
)

private data class DecodedDesktopGif(
    val frames: List<ImageBitmap>,
    val delaysMs: IntArray,
    val approxBytes: Long,
)

private data class GifFrameMeta(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val delayCs: Int,
    val disposalMethod: String,
)

private object DesktopDecodedGifCache {
    private var totalBytes: Long = 0
    private val map = LinkedHashMap<DesktopGifCacheKey, DecodedDesktopGif>(16, 0.75f, true)

    @Synchronized
    fun get(key: DesktopGifCacheKey): DecodedDesktopGif? = map[key]

    @Synchronized
    fun put(key: DesktopGifCacheKey, gif: DecodedDesktopGif) {
        if (gif.approxBytes > MaxDecodedGifBytes) return
        map.remove(key)?.let { totalBytes -= it.approxBytes }
        map[key] = gif
        totalBytes += gif.approxBytes
        while ((map.size > MaxDecodedGifEntries || totalBytes > MaxDecodedGifBytesTotal) && map.isNotEmpty()) {
            val eldest = map.entries.first()
            map.remove(eldest.key)
            totalBytes -= eldest.value.approxBytes
        }
    }
}

private object DesktopGifInFlight {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = mutableMapOf<DesktopGifCacheKey, Deferred<DecodedDesktopGif?>>()

    suspend fun getOrDecode(
        key: DesktopGifCacheKey,
        decode: suspend () -> DecodedDesktopGif?,
    ): DecodedDesktopGif? {
        DesktopDecodedGifCache.get(key)?.let { return it }

        val request = synchronized(requests) {
            requests[key] ?: scope.async {
                DesktopDecodedGifCache.get(key) ?: decode()?.also { decoded ->
                    DesktopDecodedGifCache.put(key, decoded)
                }
            }.also { deferred ->
                requests[key] = deferred
                deferred.invokeOnCompletion {
                    synchronized(requests) {
                        if (requests[key] === deferred) {
                            requests.remove(key)
                        }
                    }
                }
            }
        }

        return request.await()
    }
}

private sealed interface DesktopGifState {
    data object Loading : DesktopGifState
    data class Ready(val gif: DecodedDesktopGif) : DesktopGifState
    data object UseStaticCoil : DesktopGifState
}

@Composable
internal actual fun CollectionCardRemoteImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
    animateIfPossible: Boolean,
) {
    if (!animateIfPossible) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val targetWidthPx = maxWidth.value
            .takeIf { it.isFinite() && it > 0f }
            ?.let { with(density) { maxWidth.roundToPx() } }
            ?: FallbackDecodeDimensionPx
        val targetHeightPx = maxHeight.value
            .takeIf { it.isFinite() && it > 0f }
            ?.let { with(density) { maxHeight.roundToPx() } }
            ?: FallbackDecodeDimensionPx
        val decodeTarget = remember(targetWidthPx, targetHeightPx) {
            GifDecodeTarget(
                widthPx = targetWidthPx.roundUpToDecodeBucket().coerceIn(1, MaxDecodedDimensionPx),
                heightPx = targetHeightPx.roundUpToDecodeBucket().coerceIn(1, MaxDecodedDimensionPx),
            )
        }
        val cacheKey = remember(imageUrl, decodeTarget) {
            DesktopGifCacheKey(
                url = imageUrl,
                widthPx = decodeTarget.widthPx,
                heightPx = decodeTarget.heightPx,
            )
        }
        val cachedGif = remember(cacheKey) {
            DesktopDecodedGifCache.get(cacheKey)
        }
        var state by remember(cacheKey) {
            mutableStateOf<DesktopGifState>(
                cachedGif?.let(DesktopGifState::Ready) ?: DesktopGifState.Loading,
            )
        }

        LaunchedEffect(cacheKey) {
            cachedGif?.let {
                state = DesktopGifState.Ready(it)
                return@LaunchedEffect
            }

            state = DesktopGifState.Loading
            val decoded = DesktopGifInFlight.getOrDecode(cacheKey) {
                downloadAndDecodeGif(imageUrl, decodeTarget)
            }

            state = if (decoded != null) {
                DesktopGifState.Ready(decoded)
            } else {
                DesktopGifState.UseStaticCoil
            }
        }

        when (val currentState = state) {
            is DesktopGifState.Ready -> AnimatedComposeGif(
                gif = currentState.gif,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
            is DesktopGifState.Loading,
            is DesktopGifState.UseStaticCoil,
            -> AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

@Composable
private fun AnimatedComposeGif(
    gif: DecodedDesktopGif,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    if (gif.frames.isEmpty()) return
    var frameIndex by remember(gif) { mutableIntStateOf(0) }

    LaunchedEffect(gif) {
        if (gif.frames.size <= 1) return@LaunchedEffect
        var nextFrameAtNanos = System.nanoTime()
        while (isActive) {
            val delayMs = gif.delaysMs.getOrElse(frameIndex) { DefaultGifDelayCentiseconds * 10 }
                .coerceAtLeast(10)
            nextFrameAtNanos += delayMs * 1_000_000L
            val waitNanos = nextFrameAtNanos - System.nanoTime()
            if (waitNanos > 0L) {
                delay(max(1L, waitNanos / 1_000_000L))
            }
            frameIndex = (frameIndex + 1) % gif.frames.size
            if (System.nanoTime() - nextFrameAtNanos > 250_000_000L) {
                nextFrameAtNanos = System.nanoTime()
            }
        }
    }

    Image(
        bitmap = gif.frames[frameIndex],
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

private suspend fun downloadAndDecodeGif(
    imageUrl: String,
    target: GifDecodeTarget,
): DecodedDesktopGif? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = readGifBytes(imageUrl) ?: return@runCatching null
        decodeGifForCompose(bytes, target)
    }.onFailure { throwable ->
        AppDiagnostics.error(
            event = "home.collection.gif_load_failed",
            throwable = throwable,
            details = mapOf("urlHost" to runCatching { URI.create(imageUrl).host }.getOrNull()),
        )
    }.getOrNull()
}

private fun decodeGifForCompose(
    bytes: ByteArray,
    target: GifDecodeTarget,
): DecodedDesktopGif? {
    if (!bytes.isGifHeader()) return null
    val imageInputStream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
    imageInputStream.use { input ->
        val readers = ImageIO.getImageReadersByFormatName("gif")
        if (!readers.hasNext()) return null
        val reader = readers.next()
        try {
            reader.input = input

            val frameCount = reader.getNumImages(true)
            if (frameCount <= 0) return null

            val streamRoot = reader.streamMetadata?.getAsTree("javax_imageio_gif_stream_1.0") as? IIOMetadataNode
            val logicalDescriptor = streamRoot
                ?.getElementsByTagName("LogicalScreenDescriptor")
                ?.item(0) as? IIOMetadataNode
            val logicalWidth = logicalDescriptor
                ?.getAttribute("logicalScreenWidth")
                ?.toIntOrNull()
                ?.coerceAtLeast(1)
            val logicalHeight = logicalDescriptor
                ?.getAttribute("logicalScreenHeight")
                ?.toIntOrNull()
                ?.coerceAtLeast(1)

            val firstImage = reader.read(0) ?: return null
            val baseW = logicalWidth ?: firstImage.width
            val baseH = logicalHeight ?: firstImage.height
            if (baseW <= 0 || baseH <= 0) return null
            if (baseW.toLong() * baseH.toLong() > MaxLogicalGifPixels) return null

            val coverScale = max(
                target.widthPx.toDouble() / baseW.toDouble(),
                target.heightPx.toDouble() / baseH.toDouble(),
            )
            val scale = minOf(1.0, coverScale)
            val canvasW = max(1, (baseW * scale).toInt())
            val canvasH = max(1, (baseH * scale).toInt())
            val approxBytes = frameCount.toLong() * canvasW.toLong() * canvasH.toLong() * 4L
            if (approxBytes > MaxDecodedGifBytes) return null

            val logicalCanvas = BufferedImage(baseW, baseH, BufferedImage.TYPE_INT_ARGB)
            val previousLogicalCanvas = BufferedImage(baseW, baseH, BufferedImage.TYPE_INT_ARGB)
            val outputCanvas = BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB)

            val outFrames = ArrayList<ImageBitmap>(frameCount)
            val outDelays = IntArray(frameCount)

            val logicalGraphics = logicalCanvas.createGraphics().apply {
                composite = AlphaComposite.SrcOver
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            }
            val previousLogicalGraphics = previousLogicalCanvas.createGraphics().apply {
                composite = AlphaComposite.Src
            }
            val outputGraphics = outputCanvas.createGraphics().apply {
                composite = AlphaComposite.SrcOver
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            }

            try {
                for (index in 0 until frameCount) {
                    val frame = if (index == 0) firstImage else reader.read(index) ?: return null
                    val metadataRoot = reader.getImageMetadata(index)
                        .getAsTree("javax_imageio_gif_image_1.0") as? IIOMetadataNode
                        ?: return null
                    val meta = parseFrameMetadata(metadataRoot)

                    val needsRestorePrevious = meta.disposalMethod.equals("restoretoprevious", ignoreCase = true)
                    if (needsRestorePrevious) {
                        previousLogicalGraphics.drawImage(logicalCanvas, 0, 0, null)
                    }

                    val frameLeft = meta.left.coerceIn(0, baseW)
                    val frameTop = meta.top.coerceIn(0, baseH)
                    val frameRight = (meta.left + meta.width).coerceIn(0, baseW)
                    val frameBottom = (meta.top + meta.height).coerceIn(0, baseH)
                    val frameWidth = frameRight - frameLeft
                    val frameHeight = frameBottom - frameTop
                    if (frameWidth > 0 && frameHeight > 0) {
                        logicalGraphics.drawImage(
                            frame,
                            frameLeft,
                            frameTop,
                            frameRight,
                            frameBottom,
                            0,
                            0,
                            frame.width,
                            frame.height,
                            null,
                        )
                    }

                    val previousOutputComposite = outputGraphics.composite
                    outputGraphics.composite = AlphaComposite.Clear
                    outputGraphics.fillRect(0, 0, canvasW, canvasH)
                    outputGraphics.composite = previousOutputComposite
                    outputGraphics.drawImage(logicalCanvas, 0, 0, canvasW, canvasH, null)

                    outFrames.add(deepCopy(outputCanvas).toComposeImageBitmap())
                    outDelays[index] = max(1, meta.delayCs) * 10

                    when {
                        meta.disposalMethod.equals("restoretobackgroundcolor", ignoreCase = true) -> {
                            val clearX = frameLeft.coerceIn(0, baseW)
                            val clearY = frameTop.coerceIn(0, baseH)
                            val clearW = frameRight.coerceAtMost(baseW) - clearX
                            val clearH = frameBottom.coerceAtMost(baseH) - clearY
                            if (clearW > 0 && clearH > 0) {
                                val oldComposite = logicalGraphics.composite
                                logicalGraphics.composite = AlphaComposite.Clear
                                logicalGraphics.fillRect(clearX, clearY, clearW, clearH)
                                logicalGraphics.composite = oldComposite
                            }
                        }
                        meta.disposalMethod.equals("restoretoprevious", ignoreCase = true) -> {
                            val oldComposite = logicalGraphics.composite
                            logicalGraphics.composite = AlphaComposite.Src
                            logicalGraphics.drawImage(previousLogicalCanvas, 0, 0, null)
                            logicalGraphics.composite = oldComposite
                        }
                    }
                }
            } finally {
                logicalGraphics.dispose()
                previousLogicalGraphics.dispose()
                outputGraphics.dispose()
            }

            if (outFrames.isEmpty()) return null
            return DecodedDesktopGif(
                frames = outFrames,
                delaysMs = outDelays,
                approxBytes = approxBytes,
            )
        } finally {
            reader.dispose()
        }
    }
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
    if (declaredLength > MaxGifSourceBytes) return null

    return connection.getInputStream().use { input ->
        input.readBytesWithLimit(MaxGifSourceBytes)
    }
}

private fun Int.roundUpToDecodeBucket(): Int {
    if (this <= 0) return FallbackDecodeDimensionPx
    return (((this + DecodeSizeBucketPx - 1) / DecodeSizeBucketPx) * DecodeSizeBucketPx)
        .coerceAtLeast(DecodeSizeBucketPx)
}

private fun parseFrameMetadata(root: IIOMetadataNode): GifFrameMeta {
    val imageDescriptor = root.getElementsByTagName("ImageDescriptor").item(0) as? IIOMetadataNode
    val graphicControl = root.getElementsByTagName("GraphicControlExtension").item(0) as? IIOMetadataNode

    return GifFrameMeta(
        left = imageDescriptor?.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0,
        top = imageDescriptor?.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0,
        width = imageDescriptor?.getAttribute("imageWidth")?.toIntOrNull() ?: 1,
        height = imageDescriptor?.getAttribute("imageHeight")?.toIntOrNull() ?: 1,
        delayCs = graphicControl?.getAttribute("delayTime")?.toIntOrNull()?.coerceAtLeast(1)
            ?: DefaultGifDelayCentiseconds,
        disposalMethod = graphicControl?.getAttribute("disposalMethod") ?: "none",
    )
}

private fun deepCopy(source: BufferedImage): BufferedImage {
    val copy = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = copy.createGraphics()
    try {
        graphics.composite = AlphaComposite.Src
        graphics.drawImage(source, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    return copy
}

private fun ByteArray.isGifHeader(): Boolean =
    size >= 6 &&
        this[0] == 'G'.code.toByte() &&
        this[1] == 'I'.code.toByte() &&
        this[2] == 'F'.code.toByte() &&
        this[3] == '8'.code.toByte() &&
        (this[4] == '7'.code.toByte() || this[4] == '9'.code.toByte()) &&
        this[5] == 'a'.code.toByte()

private fun InputStream.readBytesWithLimit(maxBytes: Long): ByteArray? {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream()
    var totalBytes = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        totalBytes += read
        if (totalBytes > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
