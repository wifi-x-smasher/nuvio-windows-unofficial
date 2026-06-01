package com.nuvio.app.features.player

internal expect object InternalPlayerPlatform {
    fun isAvailable(): Boolean
    fun unavailableMessage(): String?
}
