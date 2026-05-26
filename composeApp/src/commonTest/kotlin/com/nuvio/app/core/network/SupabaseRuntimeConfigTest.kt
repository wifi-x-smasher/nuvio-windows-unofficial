package com.nuvio.app.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseRuntimeConfigTest {
    @Test
    fun rejectsBlankOrLocalhostConfiguration() {
        assertFalse(isValidSupabaseRuntimeConfig(url = "", anonKey = "anon-key"))
        assertFalse(isValidSupabaseRuntimeConfig(url = "https://localhost", anonKey = "anon-key"))
        assertFalse(isValidSupabaseRuntimeConfig(url = "https://localhost/", anonKey = "anon-key"))
        assertFalse(isValidSupabaseRuntimeConfig(url = "https://nuvio.supabase.co", anonKey = ""))
    }

    @Test
    fun acceptsHttpsSupabaseProjectConfiguration() {
        assertTrue(
            isValidSupabaseRuntimeConfig(
                url = "https://nuvio-project.supabase.co",
                anonKey = "anon-key",
            ),
        )
    }

    @Test
    fun explainsMissingPackagedConfiguration() {
        val message = supabaseRuntimeConfigErrorMessage(url = "", anonKey = "")

        assertTrue(message.contains("SUPABASE_URL"))
        assertTrue(message.contains("SUPABASE_ANON_KEY"))
        assertTrue(message.contains("local.properties"))
    }
}
