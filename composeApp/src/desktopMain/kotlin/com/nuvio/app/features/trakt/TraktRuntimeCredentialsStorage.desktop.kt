package com.nuvio.app.features.trakt

import com.nuvio.app.core.desktop.DesktopSecureStore

actual object TraktRuntimeCredentialsStorage {
    private const val clientIdKey = "trakt_runtime_client_id"
    private const val clientSecretKey = "trakt_runtime_client_secret"
    private const val redirectUriKey = "trakt_runtime_redirect_uri"

    actual val isRuntimeConfigurable: Boolean = true

    actual fun load(): TraktCredentials? {
        val clientId = DesktopSecureStore.readString(clientIdKey)?.trim().orEmpty()
        val clientSecret = DesktopSecureStore.readString(clientSecretKey)?.trim().orEmpty()
        val redirectUri = DesktopSecureStore.readString(redirectUriKey)?.trim().orEmpty()
        return TraktCredentials(
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri.ifBlank { "nuvio://auth/trakt" },
        ).takeIf { it.isValid }
    }

    actual fun save(credentials: TraktCredentials) {
        DesktopSecureStore.writeString(clientIdKey, credentials.clientId)
        DesktopSecureStore.writeString(clientSecretKey, credentials.clientSecret)
        DesktopSecureStore.writeString(redirectUriKey, credentials.redirectUri)
    }

    actual fun clear() {
        DesktopSecureStore.remove(clientIdKey)
        DesktopSecureStore.remove(clientSecretKey)
        DesktopSecureStore.remove(redirectUriKey)
    }
}
