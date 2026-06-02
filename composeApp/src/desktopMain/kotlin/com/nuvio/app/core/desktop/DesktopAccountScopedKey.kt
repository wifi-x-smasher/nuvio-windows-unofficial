package com.nuvio.app.core.desktop

internal object DesktopAccountScopedKey {
    fun of(baseKey: String, accountId: String, profileId: Int): String =
        "${baseKey}_${safePart(accountId)}_$profileId"

    private fun safePart(value: String): String =
        value.trim()
            .takeIf { it.isNotBlank() }
            ?.replace(Regex("[^A-Za-z0-9._-]+"), "_")
            ?: "signed_out"
}
