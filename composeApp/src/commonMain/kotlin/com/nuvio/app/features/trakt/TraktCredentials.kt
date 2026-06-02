package com.nuvio.app.features.trakt

private const val DefaultRedirectUri = "nuvio://auth/trakt"

data class TraktCredentials(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String = DefaultRedirectUri,
) {
    val isValid: Boolean
        get() = clientId.isNotBlank() && clientSecret.isNotBlank() && redirectUri.isNotBlank()
}

expect object TraktRuntimeCredentialsStorage {
    val isRuntimeConfigurable: Boolean
    fun load(): TraktCredentials?
    fun save(credentials: TraktCredentials)
    fun clear()
}

object TraktCredentialsProvider {
    val isRuntimeConfigurable: Boolean
        get() = TraktRuntimeCredentialsStorage.isRuntimeConfigurable

    fun current(): TraktCredentials? =
        TraktRuntimeCredentialsStorage.load()?.takeIf { it.isValid }
            ?: buildTimeCredentials()

    fun runtimeCredentials(): TraktCredentials? =
        TraktRuntimeCredentialsStorage.load()?.takeIf { it.isValid }

    fun saveRuntimeCredentials(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
    ): Boolean {
        val credentials = TraktCredentials(
            clientId = clientId.trim(),
            clientSecret = clientSecret.trim(),
            redirectUri = redirectUri.trim().ifBlank { DefaultRedirectUri },
        )
        if (!credentials.isValid) return false
        TraktRuntimeCredentialsStorage.save(credentials)
        return true
    }

    fun clearRuntimeCredentials() {
        TraktRuntimeCredentialsStorage.clear()
    }

    private fun buildTimeCredentials(): TraktCredentials? =
        TraktCredentials(
            clientId = TraktConfig.CLIENT_ID.trim(),
            clientSecret = TraktConfig.CLIENT_SECRET.trim(),
            redirectUri = TraktConfig.REDIRECT_URI.trim().ifBlank { DefaultRedirectUri },
        ).takeIf { it.isValid }
}
