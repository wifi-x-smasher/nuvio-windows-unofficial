package com.nuvio.app.features.player

internal actual object InternalPlayerPlatform {
    actual fun isAvailable(): Boolean = true
    actual fun unavailableMessage(): String? = null
}
