package com.nuvio.app.features.player.desktop.nativempv

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NativeMpvIpcClientTest {
    @Test
    fun encodesCommandArraysWithRequestId() {
        val client = clientWithInput("")

        val encoded = client.encodeCommand(
            command = listOf("set_property", "pause", true),
            requestId = 42,
        )
        val json = NativeMpvIpcJson.parseToJsonElement(encoded).jsonObject
        val command = json["command"]!!.jsonArray

        assertEquals("set_property", command[0].jsonPrimitive.content)
        assertEquals("pause", command[1].jsonPrimitive.content)
        assertEquals("true", command[2].jsonPrimitive.content)
        assertEquals("42", json["request_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun ignoresEventsAndUnrelatedResponsesUntilMatchingRequestId() {
        val client = clientWithInput(
            """
            {"event":"file-loaded"}
            {"request_id":1,"error":"success","data":"wrong"}
            {"event":"property-change","name":"time-pos","data":12.5}
            {"request_id":2,"error":"success","data":"ok"}
            """.trimIndent(),
        )

        val result = client.readResponseFor(requestId = 2)

        val success = assertIs<NativeMpvIpcResult.Success>(result)
        assertEquals(JsonPrimitive("ok"), success.data)
    }

    @Test
    fun mapsMpvErrorsToFailure() {
        val client = clientWithInput("""{"request_id":3,"error":"property unavailable"}""")

        val result = client.readResponseFor(requestId = 3)

        val failure = assertIs<NativeMpvIpcResult.Failure>(result)
        assertEquals("property unavailable", failure.error)
    }

    @Test
    fun writesCommandAndFlushesBeforeReadingResponse() {
        val output = StringWriter()
        val client = NativeMpvIpcClient(
            reader = BufferedReader(StringReader("""{"request_id":1,"error":"success"}""")),
            writer = BufferedWriter(output),
        )

        val result = client.sendCommand(listOf("get_property", "mpv-version"))

        assertIs<NativeMpvIpcResult.Success>(result)
        assertTrue(output.toString().contains("\"mpv-version\""))
        assertTrue(output.toString().endsWith(System.lineSeparator()))
    }

    private fun clientWithInput(input: String): NativeMpvIpcClient =
        NativeMpvIpcClient(
            reader = BufferedReader(StringReader(input)),
            writer = BufferedWriter(StringWriter()),
        )
}
