package com.nuvio.app.features.trakt

actual object TraktRuntimeCredentialsStorage {
    actual val isRuntimeConfigurable: Boolean = false
    actual fun load(): TraktCredentials? = null
    actual fun save(credentials: TraktCredentials) = Unit
    actual fun clear() = Unit
}
