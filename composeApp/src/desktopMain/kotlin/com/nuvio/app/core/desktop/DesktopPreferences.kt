package com.nuvio.app.core.desktop

import java.io.StringWriter
import java.util.Base64
import java.util.Properties

// Settings files are only ever a few KB. Anything past this is corruption (see issue #21, where a
// ballooned debrid settings file OOM-crashed the app on startup); the store quarantines it instead.
private const val MAX_PREFS_FILE_BYTES = 8L * 1024 * 1024

internal class DesktopPreferences(
    fileName: String,
) {
    private val store = DesktopJsonStore(DesktopAppPaths.dataFile("$fileName.properties"))
    private val base64Encoder = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    @Synchronized
    fun getString(key: String): String? =
        properties().getProperty(key)

    @Synchronized
    fun putString(key: String, value: String?) {
        mutate { props ->
            if (value == null) {
                props.remove(key)
            } else {
                props.setProperty(key, value)
            }
        }
    }

    @Synchronized
    fun getBoolean(key: String): Boolean? =
        getString(key)?.toBooleanStrictOrNull()

    @Synchronized
    fun putBoolean(key: String, value: Boolean) {
        putString(key, value.toString())
    }

    @Synchronized
    fun getInt(key: String): Int? =
        getString(key)?.toIntOrNull()

    @Synchronized
    fun putInt(key: String, value: Int) {
        putString(key, value.toString())
    }

    @Synchronized
    fun getFloat(key: String): Float? =
        getString(key)?.toFloatOrNull()

    @Synchronized
    fun putFloat(key: String, value: Float) {
        putString(key, value.toString())
    }

    @Synchronized
    fun getStringSet(key: String): Set<String>? =
        getString(key)
            ?.let { stored ->
                if (stored.isEmpty()) {
                    emptySet()
                } else {
                    stored.lineSequence()
                        .mapNotNull { encoded ->
                            runCatching { base64Decoder.decode(encoded).decodeToString() }.getOrNull()
                        }
                        .toSet()
                }
            }

    @Synchronized
    fun putStringSet(key: String, values: Set<String>) {
        val encoded = values
            .sorted()
            .joinToString("\n") { value ->
                base64Encoder.encodeToString(value.encodeToByteArray())
            }
        putString(key, encoded)
    }

    @Synchronized
    fun remove(key: String) {
        mutate { it.remove(key) }
    }

    @Synchronized
    fun clear() {
        store.clear()
    }

    private fun properties(): Properties {
        val props = Properties()
        store.readTextOrNull(MAX_PREFS_FILE_BYTES)?.byteInputStream()?.use(props::load)
        return props
    }

    private fun mutate(block: (Properties) -> Unit) {
        val props = properties()
        block(props)
        val out = StringWriter()
        props.store(out, null)
        store.writeText(out.toString())
    }
}

internal fun desktopPayloadStore(fileName: String): DesktopJsonStore =
    DesktopJsonStore(DesktopAppPaths.dataFile(fileName))

internal fun desktopSafeFilePart(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._-]"), "_")
