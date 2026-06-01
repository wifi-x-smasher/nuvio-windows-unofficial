package com.nuvio.app.features.player

import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse

class DesktopPlayerSettingsStorageSyncTest {
    @Test
    fun desktopExternalPlayerPreferenceIsNotExportedToCrossDeviceSync() {
        val previousEnabled = PlayerSettingsStorage.loadExternalPlayerEnabled()
        val previousPlayerId = PlayerSettingsStorage.loadExternalPlayerId()

        try {
            PlayerSettingsStorage.saveExternalPlayerEnabled(true)
            PlayerSettingsStorage.saveExternalPlayerId("vlc")

            val payload = PlayerSettingsStorage.exportToSyncPayload()

            assertFalse("external_player_enabled" in payload)
            assertFalse("external_player_id" in payload)
            assertFalse("desktop_external_player_enabled" in payload)
            assertFalse("desktop_external_player_id" in payload)
        } finally {
            restoreDesktopExternalPlayerPreference(previousEnabled, previousPlayerId)
        }
    }

    @Test
    fun desktopIgnoresSyncedMobileExternalPlayerPreference() {
        val previousEnabled = PlayerSettingsStorage.loadExternalPlayerEnabled()
        val previousPlayerId = PlayerSettingsStorage.loadExternalPlayerId()

        try {
            PlayerSettingsStorage.saveExternalPlayerEnabled(false)
            PlayerSettingsStorage.saveExternalPlayerId(null)

            PlayerSettingsStorage.replaceFromSyncPayload(
                buildJsonObject {
                    put("external_player_enabled", encodeSyncBoolean(true))
                    put("external_player_id", encodeSyncString("vlc"))
                },
            )

            assertFalse(PlayerSettingsStorage.loadExternalPlayerEnabled() ?: false)
            assertFalse(PlayerSettingsStorage.loadExternalPlayerId() == "vlc")
        } finally {
            restoreDesktopExternalPlayerPreference(previousEnabled, previousPlayerId)
        }
    }

    private fun restoreDesktopExternalPlayerPreference(enabled: Boolean?, playerId: String?) {
        PlayerSettingsStorage.saveExternalPlayerEnabled(enabled ?: false)
        PlayerSettingsStorage.saveExternalPlayerId(playerId)
    }
}
