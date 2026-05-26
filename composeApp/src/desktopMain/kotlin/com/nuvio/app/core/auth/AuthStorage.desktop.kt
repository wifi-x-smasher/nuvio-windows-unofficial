package com.nuvio.app.core.auth

import com.nuvio.app.core.desktop.DesktopSecureStore

internal actual object AuthStorage {
    private const val anonymousUserIdKey = "auth.anonymousUserId"

    actual fun loadAnonymousUserId(): String? =
        DesktopSecureStore.readString(anonymousUserIdKey)

    actual fun saveAnonymousUserId(userId: String) {
        DesktopSecureStore.writeString(anonymousUserIdKey, userId)
    }

    actual fun clearAnonymousUserId() {
        DesktopSecureStore.remove(anonymousUserIdKey)
    }
}
