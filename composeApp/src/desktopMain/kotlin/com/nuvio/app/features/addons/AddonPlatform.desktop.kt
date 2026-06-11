package com.nuvio.app.features.addons

import com.nuvio.app.core.desktop.DesktopPreferences
import com.nuvio.app.core.network.IPv4FirstDns
import com.nuvio.app.desktop.DesktopRuntimeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets

internal actual object AddonStorage {
    private val preferences = DesktopPreferences("addons")
    private val json = Json { ignoreUnknownKeys = true }
    private val urlListSerializer = ListSerializer(String.serializer())
    private val enabledStatesSerializer = MapSerializer(String.serializer(), Boolean.serializer())

    actual fun loadInstalledAddonUrls(profileId: Int): List<String> =
        preferences.getString("installed_addon_urls_$profileId")
            ?.let { runCatching { json.decodeFromString(urlListSerializer, it) }.getOrNull() }
            ?: emptyList()

    actual fun saveInstalledAddonUrls(profileId: Int, urls: List<String>) {
        preferences.putString("installed_addon_urls_$profileId", json.encodeToString(urlListSerializer, urls))
    }

    actual fun loadAddonEnabledStates(profileId: Int): Map<String, Boolean> =
        preferences.getString("addon_enabled_states_$profileId")
            ?.let { runCatching { json.decodeFromString(enabledStatesSerializer, it) }.getOrNull() }
            ?: emptyMap()

    actual fun saveAddonEnabledStates(profileId: Int, states: Map<String, Boolean>) {
        preferences.putString("addon_enabled_states_$profileId", json.encodeToString(enabledStatesSerializer, states))
    }
}

private val addonHttpClient = OkHttpClient.Builder()
    // Mirror the Android addon client: prefer IPv4 to avoid IPv6-stall on hosts with broken AAAA
    // records, and bypass any system proxy/PAC that could silently break addon/plugin fetches.
    .dns(IPv4FirstDns())
    .proxy(java.net.Proxy.NO_PROXY)
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

private const val maxRawResponseBodyBytes = 1024 * 1024
private const val truncationSuffix = "\n...[truncated]"

internal fun normalizeDesktopAddonRequestUrl(url: String): String =
    url.trim().replace("%7C", "|", ignoreCase = true)

internal fun desktopAddonRequestAllowsBody(method: String): Boolean =
    when (method.uppercase()) {
        "POST", "PUT", "PATCH", "DELETE" -> true
        else -> false
    }

internal fun sanitizeDesktopAddonRequestHeaders(headers: Map<String, String>): Map<String, String> =
    headers.entries
        .filterNot { (key, _) -> key.equals("Accept-Encoding", ignoreCase = true) }
        .associate { (key, value) -> key.trim() to value.trim() }
        .filterKeys { it.isNotBlank() }
        .filterValues { it.isNotBlank() }

internal fun redactDesktopAddonUrl(url: String): String =
    url.replace(Regex("""(\bhttps?://[^\s?]+)\?[^}\s]+"""), "$1?<redacted>")

private fun Map<String, String>.getHeaderIgnoreCase(name: String): String? =
    entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value

private data class LimitedReadResult(
    val bytes: ByteArray,
    val truncated: Boolean,
)

private fun readAtMostBytes(stream: InputStream, maxBytes: Int): LimitedReadResult {
    val out = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
    val buffer = ByteArray(8 * 1024)
    var remaining = maxBytes
    var truncated = false

    while (remaining > 0) {
        val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
        if (read <= 0) break
        out.write(buffer, 0, read)
        remaining -= read
    }

    if (remaining == 0) {
        truncated = stream.read() != -1
    }

    return LimitedReadResult(out.toByteArray(), truncated)
}

private fun readResponseBodyLimited(body: ResponseBody?): String {
    if (body == null) return ""
    val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    val readResult = body.byteStream().use { stream ->
        readAtMostBytes(stream, maxRawResponseBodyBytes)
    }
    val decoded = runCatching { String(readResult.bytes, charset) }
        .getOrElse { String(readResult.bytes, Charsets.UTF_8) }
    return if (readResult.truncated) decoded + truncationSuffix else decoded
}

private fun readResponseBody(body: ResponseBody?): String {
    if (body == null) return ""
    val bytes = body.bytes()
    return runCatching {
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        String(bytes, charset)
    }.getOrElse {
        String(bytes, Charsets.UTF_8)
    }
}

private suspend fun executeTextRequest(
    method: String,
    url: String,
    headers: Map<String, String> = emptyMap(),
    body: String = "",
): String = withContext(Dispatchers.IO) {
    val normalizedMethod = method.uppercase()
    val normalizedUrl = normalizeDesktopAddonRequestUrl(url)
    val sanitizedHeaders = sanitizeDesktopAddonRequestHeaders(headers)
    val builder = Request.Builder().url(normalizedUrl)
    sanitizedHeaders.forEach { (key, value) ->
        builder.header(key, value)
    }

    val request = if (desktopAddonRequestAllowsBody(normalizedMethod)) {
        val contentType = sanitizedHeaders.getHeaderIgnoreCase("Content-Type")
            ?: if (normalizedMethod == "POST") "application/x-www-form-urlencoded" else "application/json"
        val requestBody = body.toByteArray(Charsets.UTF_8).toRequestBody(contentType.toMediaType())
        builder.method(normalizedMethod, requestBody)
    } else {
        builder.method(normalizedMethod, null)
    }.build()

    addonHttpClient.newCall(request).execute().use { response ->
        val payload = readResponseBody(response.body)
        if (!response.isSuccessful) {
            DesktopRuntimeLog.warn(
                "Addon text request failed method=$normalizedMethod status=${response.code} url=${redactDesktopAddonUrl(normalizedUrl)}",
            )
            error("Request failed with HTTP ${response.code}")
        }
        if (payload.isBlank()) {
            DesktopRuntimeLog.warn(
                "Addon text request empty method=$normalizedMethod status=${response.code} url=${redactDesktopAddonUrl(normalizedUrl)}",
            )
            throw IllegalStateException("Empty response body")
        }
        payload
    }
}

actual suspend fun httpGetText(url: String): String =
    executeTextRequest(
        method = "GET",
        url = url,
        headers = mapOf("Accept" to "application/json"),
    )

actual suspend fun httpPostJson(url: String, body: String): String =
    executeTextRequest(
        method = "POST",
        url = url,
        headers = mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
        ),
        body = body,
    )

actual suspend fun httpGetTextWithHeaders(
    url: String,
    headers: Map<String, String>,
): String =
    executeTextRequest(
        method = "GET",
        url = url,
        headers = mapOf("Accept" to "application/json") + headers,
    )

actual suspend fun httpPostJsonWithHeaders(
    url: String,
    body: String,
    headers: Map<String, String>,
): String =
    executeTextRequest(
        method = "POST",
        url = url,
        headers = mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
        ) + headers,
        body = body,
    )

actual suspend fun httpRequestRaw(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: String,
    followRedirects: Boolean,
): RawHttpResponse =
    withContext(Dispatchers.IO) {
        val normalizedMethod = method.uppercase()
        val normalizedUrl = normalizeDesktopAddonRequestUrl(url)
        val sanitizedHeaders = sanitizeDesktopAddonRequestHeaders(headers)
        val builder = Request.Builder().url(normalizedUrl)
        sanitizedHeaders.forEach { (key, value) ->
            builder.header(key, value)
        }

        val request = if (desktopAddonRequestAllowsBody(normalizedMethod)) {
            val contentType = sanitizedHeaders.getHeaderIgnoreCase("Content-Type")
                ?: if (normalizedMethod == "POST") "application/x-www-form-urlencoded" else "application/json"
            val requestBody = body.toByteArray(Charsets.UTF_8).toRequestBody(contentType.toMediaType())
            builder.method(normalizedMethod, requestBody)
        } else {
            builder.method(normalizedMethod, null)
        }.build()

        val client = if (followRedirects) {
            addonHttpClient
        } else {
            addonHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }

        client.newCall(request).execute().use { response ->
            val payload = readResponseBodyLimited(response.body)
            if (!response.isSuccessful) {
                DesktopRuntimeLog.warn(
                    "Addon raw request nonSuccess method=$normalizedMethod status=${response.code} url=${redactDesktopAddonUrl(normalizedUrl)} bodyBytes=${payload.length}",
                )
            }
            RawHttpResponse(
                status = response.code,
                statusText = response.message,
                url = response.request.url.toString(),
                body = payload,
                headers = response.headers.toMultimap()
                    .mapValues { (_, values) -> values.joinToString(",") }
                    .mapKeys { (name, _) -> name.lowercase() },
            )
        }
    }
