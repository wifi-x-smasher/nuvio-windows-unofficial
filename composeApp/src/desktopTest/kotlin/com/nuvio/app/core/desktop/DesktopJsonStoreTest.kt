package com.nuvio.app.core.desktop

import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DesktopJsonStoreTest {
    @Test
    fun missingFileReadsAsNull() {
        val store = DesktopJsonStore(createTempDirectory("nuvio-store-test").resolve("missing.json"))

        assertNull(store.readTextOrNull())
    }

    @Test
    fun writeAndReadUsesUtf8Text() {
        val store = DesktopJsonStore(createTempDirectory("nuvio-store-test").resolve("nested/settings.json"))

        store.writeText("""{"enabled":true}""")

        assertEquals("""{"enabled":true}""", store.readTextOrNull())
    }

    @Test
    fun clearDeletesExistingStoreFile() {
        val file = createTempDirectory("nuvio-store-test").resolve("settings.json")
        val store = DesktopJsonStore(file)

        store.writeText("""{"enabled":true}""")
        store.clear()

        assertFalse(file.exists())
    }
}
