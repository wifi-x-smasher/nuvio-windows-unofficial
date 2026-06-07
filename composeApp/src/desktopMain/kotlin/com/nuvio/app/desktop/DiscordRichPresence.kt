package com.nuvio.app.desktop

import com.nuvio.app.core.desktop.DesktopPreferences
import com.nuvio.app.features.player.DiscordPresenceSettings
import com.nuvio.app.features.player.NowPlayingBridge
import com.nuvio.app.features.player.NowPlayingInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Discord Rich Presence ("Watching X on Nuvio") for the desktop build.
 *
 * Talks to the locally-running Discord client over its IPC named pipe (\\.\pipe\discord-ipc-N) and
 * sends SET_ACTIVITY frames. Opt-in via [DiscordPresenceSettings] (default off), shown only while a
 * stream is actually playing. Fully self-contained: no SDK, no native deps — Java can open the
 * Discord named pipe directly. All failures are swallowed; if Discord isn't running it simply no-ops
 * and retries later. Touches nothing in the MPV/HDR/window path.
 */
internal object DiscordRichPresence {
    private const val APPLICATION_ID = "1513213258369204434"
    private const val LOGO_ASSET = "nuvio_logo" // Discord art-asset key for the Nuvio logo/badge
    private const val PREF_KEY = "rich_presence_enabled"
    private const val CONNECT_RETRY_MS = 15_000L

    private val prefs by lazy { DesktopPreferences("discord") }
    private val json = Json { encodeDefaults = false; explicitNulls = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processId: Long = runCatching { ProcessHandle.current().pid() }.getOrDefault(0L)

    @Volatile private var pipe: RandomAccessFile? = null
    @Volatile private var connected = false
    private var lastConnectAttemptMs = 0L
    private var lastActivityKey: String? = null
    private var started = false

    fun start() {
        if (started || !isWindows()) return
        started = true

        // Seed the persisted toggle so the settings UI reflects it.
        DiscordPresenceSettings.setEnabled(prefs.getBoolean(PREF_KEY) ?: false)

        // Persist toggle changes; tear down presence when turned off.
        scope.launch {
            DiscordPresenceSettings.enabled.collect { enabled ->
                prefs.putBoolean(PREF_KEY, enabled)
                if (!enabled) {
                    clearActivity()
                    disconnect()
                }
            }
        }

        // Drive presence from the toggle + now-playing state.
        scope.launch {
            combine(DiscordPresenceSettings.enabled, NowPlayingBridge.state) { enabled, info ->
                enabled to info
            }.collect { (enabled, info) ->
                if (!enabled) return@collect
                if (info == null) {
                    clearActivity()
                } else {
                    pushActivity(info)
                }
            }
        }
    }

    private fun pushActivity(info: NowPlayingInfo) {
        val nowMs = System.currentTimeMillis()
        val startMs = (nowMs - info.positionMs.coerceAtLeast(0L)).coerceAtMost(nowMs)
        // 5s buckets on the start time so normal playback doesn't re-push every second, but a seek
        // (which shifts start) or a pause/track change still triggers a single fresh update.
        val key = "${info.title}|${info.subtitle}|${info.isPaused}|${startMs / 5000}|${info.durationMs / 1000}"
        if (connected && key == lastActivityKey) return
        if (!ensureConnected()) return

        val activity = buildActivity(info, startMs)
        val ok = sendFrame(OP_FRAME, setActivityFrame(activity))
        if (ok) lastActivityKey = key else lastActivityKey = null
    }

    private fun clearActivity() {
        if (!connected) return
        lastActivityKey = null
        sendFrame(OP_FRAME, setActivityFrame(null))
    }

    private fun buildActivity(info: NowPlayingInfo, startMs: Long): JsonObject = buildJsonObject {
        put("type", 3) // "Watching"
        put("details", info.title.trim().take(128).ifBlank { "Watching" })
        info.subtitle?.trim()?.takeIf { it.isNotBlank() }?.let { put("state", it.take(128)) }
        if (!info.isPaused && info.durationMs > 0L) {
            putJsonObject("timestamps") {
                put("start", startMs / 1000)
                put("end", (startMs + info.durationMs) / 1000)
            }
        }
        putJsonObject("assets") {
            val posterUrl = info.posterUrl?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }
            if (posterUrl != null) {
                // Dynamic poster art: a raw https URL that Discord proxies via media.discordapp.net.
                // The Nuvio logo rides along as the small corner badge.
                put("large_image", posterUrl)
                put("large_text", info.title.trim().take(128).ifBlank { "Nuvio" })
                put("small_image", LOGO_ASSET)
                put("small_text", if (info.isPaused) "Nuvio · Paused" else "Nuvio")
            } else {
                // No poster available — fall back to the static Nuvio logo.
                put("large_image", LOGO_ASSET)
                put("large_text", "Nuvio")
                if (info.isPaused) put("small_text", "Paused")
            }
        }

    }

    private fun setActivityFrame(activity: JsonObject?): String {
        val frame = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", processId)
                if (activity == null) put("activity", JsonNull) else put("activity", activity)
            }
            put("nonce", UUID.randomUUID().toString())
        }
        return json.encodeToString(JsonObject.serializer(), frame)
    }

    private fun ensureConnected(): Boolean {
        if (connected) return true
        val now = System.currentTimeMillis()
        if (now - lastConnectAttemptMs < CONNECT_RETRY_MS) return false
        lastConnectAttemptMs = now
        for (i in 0..9) {
            val handle = runCatching { RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw") }.getOrNull()
                ?: continue
            pipe = handle
            val handshake = buildJsonObject {
                put("v", 1)
                put("client_id", APPLICATION_ID)
            }
            val ok = runCatching {
                writeFrame(OP_HANDSHAKE, json.encodeToString(JsonObject.serializer(), handshake))
                readFrame() // READY
                true
            }.getOrDefault(false)
            if (ok) {
                connected = true
                DesktopRuntimeLog.info("discord.presence.connected pipe=discord-ipc-$i")
                return true
            }
            disconnect()
        }
        return false
    }

    private fun sendFrame(op: Int, payload: String): Boolean =
        runCatching {
            writeFrame(op, payload)
            readFrame()
            true
        }.getOrElse {
            disconnect()
            false
        }

    private fun writeFrame(op: Int, payload: String) {
        val data = payload.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(op)
        buffer.putInt(data.size)
        buffer.put(data)
        val handle = pipe ?: throw IllegalStateException("Discord pipe not open")
        handle.write(buffer.array())
    }

    private fun readFrame(): String {
        val handle = pipe ?: throw IllegalStateException("Discord pipe not open")
        val header = ByteArray(8)
        handle.readFully(header)
        val length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(4)
        if (length <= 0) return ""
        val body = ByteArray(length)
        handle.readFully(body)
        return String(body, StandardCharsets.UTF_8)
    }

    private fun disconnect() {
        connected = false
        lastActivityKey = null
        runCatching { pipe?.close() }
        pipe = null
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

    private const val OP_HANDSHAKE = 0
    private const val OP_FRAME = 1
}
