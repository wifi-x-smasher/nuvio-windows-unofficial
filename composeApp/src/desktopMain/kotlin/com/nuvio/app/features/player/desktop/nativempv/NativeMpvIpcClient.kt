package com.nuvio.app.features.player.desktop.nativempv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.atomic.AtomicLong

internal sealed interface NativeMpvIpcResult {
    data class Success(val data: JsonElement?) : NativeMpvIpcResult
    data class Failure(val error: String) : NativeMpvIpcResult
}

internal class NativeMpvIpcClient(
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
    private val json: Json = NativeMpvIpcJson,
) {
    private val requestCounter = AtomicLong(0)

    fun sendCommand(command: List<Any?>): NativeMpvIpcResult {
        val requestId = requestCounter.incrementAndGet()
        writer.write(encodeCommand(command, requestId))
        writer.newLine()
        writer.flush()
        return readResponseFor(requestId)
    }

    fun readResponseFor(requestId: Long): NativeMpvIpcResult {
        while (true) {
            val line = reader.readLine() ?: return NativeMpvIpcResult.Failure("eof")
            val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            val responseId = obj["request_id"]?.jsonPrimitive?.doubleOrNull?.toLong()
            if (responseId != requestId) continue
            val error = obj["error"]?.jsonPrimitive?.content ?: "success"
            return if (error == "success") {
                NativeMpvIpcResult.Success(obj["data"])
            } else {
                NativeMpvIpcResult.Failure(error)
            }
        }
    }

    fun encodeCommand(command: List<Any?>, requestId: Long): String =
        json.encodeToString(
            JsonObject(
                mapOf(
                    "command" to JsonArray(command.map(::toJsonElement)),
                    "request_id" to JsonPrimitive(requestId),
                ),
            ),
        )

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is JsonElement -> value
        is List<*> -> JsonArray(value.map(::toJsonElement))
        else -> JsonPrimitive(value.toString())
    }
}

internal val NativeMpvIpcJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
