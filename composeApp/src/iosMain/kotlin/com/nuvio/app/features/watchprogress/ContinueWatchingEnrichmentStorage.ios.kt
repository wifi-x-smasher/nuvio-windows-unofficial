package com.nuvio.app.features.watchprogress

import platform.Foundation.NSUserDefaults

actual object ContinueWatchingEnrichmentStorage {
    actual fun loadPayload(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(key)

    actual fun savePayload(key: String, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = key)
    }
}
