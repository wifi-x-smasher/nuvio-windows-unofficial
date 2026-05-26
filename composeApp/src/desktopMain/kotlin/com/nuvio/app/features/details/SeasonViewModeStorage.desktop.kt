package com.nuvio.app.features.details

import com.nuvio.app.core.desktop.DesktopPreferences

internal actual object SeasonViewModeStorage {
    private const val key = "season_view_mode"
    private val preferences = DesktopPreferences("season-view-mode")

    actual fun load(): SeasonViewMode? =
        preferences.getString(key)?.let(SeasonViewMode::parse)

    actual fun save(mode: SeasonViewMode) {
        preferences.putString(key, SeasonViewMode.persist(mode))
    }
}
