package com.nuvio.app.core.network

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseProvider {
    val configurationError: String?
        get() = validateSupabaseRuntimeConfig(
            url = SupabaseConfig.URL,
            anonKey = SupabaseConfig.ANON_KEY,
        ).takeUnless { it.isValid }?.message

    fun isConfigured(): Boolean =
        isValidSupabaseRuntimeConfig(
            url = SupabaseConfig.URL,
            anonKey = SupabaseConfig.ANON_KEY,
        )

    fun requireConfigured() {
        val validation = validateSupabaseRuntimeConfig(
            url = SupabaseConfig.URL,
            anonKey = SupabaseConfig.ANON_KEY,
        )
        require(validation.isValid) { validation.message }
    }

    val client by lazy {
        requireConfigured()
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL,
            supabaseKey = SupabaseConfig.ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Functions)
        }
    }
}
