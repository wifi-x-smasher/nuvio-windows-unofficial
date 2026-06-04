package com.nuvio.app.features.addons

import com.nuvio.app.core.desktop.DesktopPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

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

private val addonHttpClient = HttpClient(CIO) {
    followRedirects = true
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
}

actual suspend fun httpGetText(url: String): String =
    httpGetTextWithHeaders(url, emptyMap())

actual suspend fun httpPostJson(url: String, body: String): String =
    httpPostJsonWithHeaders(url, body, emptyMap())

actual suspend fun httpGetTextWithHeaders(
    url: String,
    headers: Map<String, String>,
): String =
    httpRequestRaw("GET", url, headers, "").body

actual suspend fun httpPostJsonWithHeaders(
    url: String,
    body: String,
    headers: Map<String, String>,
): String =
    httpRequestRaw("POST", url, headers, body).body

actual suspend fun httpRequestRaw(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: String,
    followRedirects: Boolean,
): RawHttpResponse {
    val response = addonHttpClient.request(url) {
        this.method = when (method.uppercase()) {
            "POST" -> HttpMethod.Post
            "PUT" -> HttpMethod.Put
            "DELETE" -> HttpMethod.Delete
            "PATCH" -> HttpMethod.Patch
            "HEAD" -> HttpMethod.Head
            else -> HttpMethod.Get
        }
        headers.forEach { (name, value) -> header(name, value) }
        if (body.isNotEmpty()) {
            if (headers.keys.none { it.equals(HttpHeaders.ContentType, ignoreCase = true) }) {
                contentType(ContentType.Application.Json)
            }
            setBody(body)
        }
    }

    return RawHttpResponse(
        status = response.status.value,
        statusText = response.status.description,
        url = response.request.url.toString(),
        body = response.bodyAsText(),
        headers = response.headers.entries().associate { (name, values) -> name to values.joinToString(",") },
    )
}
