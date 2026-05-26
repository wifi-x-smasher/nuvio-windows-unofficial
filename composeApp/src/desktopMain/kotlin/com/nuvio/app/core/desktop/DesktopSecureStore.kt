package com.nuvio.app.core.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.util.Base64
import java.util.Properties

internal object DesktopSecureStore {
    private val store = DesktopJsonStore(DesktopAppPaths.dataFile("secure-store.properties"))
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    @Synchronized
    fun readString(key: String): String? {
        val encoded = properties().getProperty(key) ?: return null
        val protectedBytes = runCatching { decoder.decode(encoded) }.getOrNull() ?: return null
        val clearBytes = runCatching { Crypt32Util.cryptUnprotectData(protectedBytes) }.getOrNull() ?: return null
        return clearBytes.decodeToString()
    }

    @Synchronized
    fun writeString(key: String, value: String) {
        val props = properties()
        val protectedBytes = Crypt32Util.cryptProtectData(value.encodeToByteArray())
        props.setProperty(key, encoder.encodeToString(protectedBytes))
        save(props)
    }

    @Synchronized
    fun remove(key: String) {
        val props = properties()
        props.remove(key)
        save(props)
    }

    @Synchronized
    fun clear() {
        store.clear()
    }

    private fun properties(): Properties {
        val props = Properties()
        store.readTextOrNull()?.byteInputStream()?.use(props::load)
        return props
    }

    private fun save(props: Properties) {
        val out = java.io.StringWriter()
        props.store(out, "Nuvio secure desktop store")
        store.writeText(out.toString())
    }
}
