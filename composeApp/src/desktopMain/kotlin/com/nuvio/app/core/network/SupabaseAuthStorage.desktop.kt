package com.nuvio.app.core.network

import com.nuvio.app.core.desktop.DesktopSecureStore
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

internal actual fun AuthConfig.configurePlatformAuthStorage() {
    sessionManager = DesktopSupabaseSessionManager
    codeVerifierCache = DesktopSupabaseCodeVerifierCache
}

private object DesktopSupabaseSessionManager : SessionManager {
    private const val sessionKey = "supabase.session"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun saveSession(session: UserSession) {
        DesktopSecureStore.writeString(
            sessionKey,
            json.encodeToString(UserSession.serializer(), session),
        )
    }

    override suspend fun loadSession(): UserSession? {
        val payload = DesktopSecureStore.readString(sessionKey) ?: return null
        return runCatching {
            json.decodeFromString(UserSession.serializer(), payload)
        }.getOrNull()
    }

    override suspend fun deleteSession() {
        DesktopSecureStore.remove(sessionKey)
    }
}

private object DesktopSupabaseCodeVerifierCache : CodeVerifierCache {
    private const val codeVerifierKey = "supabase.codeVerifier"

    override suspend fun saveCodeVerifier(codeVerifier: String) {
        DesktopSecureStore.writeString(codeVerifierKey, codeVerifier)
    }

    override suspend fun loadCodeVerifier(): String? =
        DesktopSecureStore.readString(codeVerifierKey)

    override suspend fun deleteCodeVerifier() {
        DesktopSecureStore.remove(codeVerifierKey)
    }
}
