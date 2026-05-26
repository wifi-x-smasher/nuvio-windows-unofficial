package com.nuvio.app.core.network

internal data class SupabaseRuntimeConfigValidation(
    val isValid: Boolean,
    val message: String,
)

internal fun isValidSupabaseRuntimeConfig(url: String, anonKey: String): Boolean =
    validateSupabaseRuntimeConfig(url, anonKey).isValid

internal fun validateSupabaseRuntimeConfig(url: String, anonKey: String): SupabaseRuntimeConfigValidation {
    val trimmedUrl = url.trim().trimEnd('/')
    val normalizedUrl = trimmedUrl.lowercase()
    val normalizedHost = normalizedUrl
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')
    val key = anonKey.trim()

    val isInvalidUrl = trimmedUrl.isBlank() ||
        !normalizedUrl.startsWith("https://") ||
        normalizedHost == "localhost" ||
        normalizedHost == "127.0.0.1" ||
        normalizedHost == "::1" ||
        normalizedUrl.contains("your_supabase_url_here")
    val isInvalidKey = key.isBlank() || key.contains("your_supabase_anon_key_here", ignoreCase = true)

    return if (!isInvalidUrl && !isInvalidKey) {
        SupabaseRuntimeConfigValidation(isValid = true, message = "")
    } else {
        SupabaseRuntimeConfigValidation(
            isValid = false,
            message = supabaseRuntimeConfigErrorMessage(url, anonKey),
        )
    }
}

internal fun supabaseRuntimeConfigErrorMessage(url: String, anonKey: String): String {
    val missing = buildList {
        if (url.trim().isBlank() || url.contains("localhost", ignoreCase = true)) add("SUPABASE_URL")
        if (anonKey.trim().isBlank()) add("SUPABASE_ANON_KEY")
    }.ifEmpty {
        listOf("SUPABASE_URL", "SUPABASE_ANON_KEY")
    }

    return "Nuvio account sync is not configured for this Windows build. " +
        "Add ${missing.joinToString(" and ")} to local.properties or the build environment, then rebuild the installer."
}
