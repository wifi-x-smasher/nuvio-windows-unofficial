package com.nuvio.app.core.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DesktopAccountScopedKeyTest {
    @Test
    fun `same profile in different accounts uses different keys`() {
        val first = DesktopAccountScopedKey.of(
            baseKey = "debrid_torbox_api_key",
            accountId = "account-alpha",
            profileId = 1,
        )
        val second = DesktopAccountScopedKey.of(
            baseKey = "debrid_torbox_api_key",
            accountId = "account-beta",
            profileId = 1,
        )

        assertNotEquals(first, second)
    }

    @Test
    fun `account and profile are preserved in a safe key`() {
        val key = DesktopAccountScopedKey.of(
            baseKey = "debrid_torbox_api_key",
            accountId = "user:id/with spaces",
            profileId = 3,
        )

        assertEquals("debrid_torbox_api_key_user_id_with_spaces_3", key)
    }
}
