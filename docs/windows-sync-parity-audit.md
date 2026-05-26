# Windows Sync Parity Audit

Last audited: 2026-05-26

## Summary

The Windows app is built from the Nuvio Mobile Kotlin Multiplatform codebase, not from a separate fork of the TV app. This means Windows reuses the same common repositories, Supabase calls, profile model, add-on model, plugin repository model, library sync, watched/progress sync, collections sync, home catalog sync, and profile settings sync used by Mobile.

Nuvio TV remains a separate Android TV implementation. Windows should match its large-screen behavior, but sync parity is achieved through the shared backend contracts rather than by copying TV storage code.

## Current Status

| Area | Windows implementation | Mobile parity | TV parity |
| --- | --- | --- | --- |
| Profiles | Shared `ProfileRepository`; desktop storage actual writes the shared profile payload. | Same shared model and payload. | Same Supabase profile rows, including `uses_primary_addons` and `uses_primary_plugins`. |
| Add-ons | Shared `AddonRepository`; desktop `AddonStorage` persists installed URLs and enabled states per profile. | Same repository, same `sync_push_addons` RPC, same `addons` table. | Compatible with TV `AddonSyncService`; URL normalization handles base URLs and `/manifest.json` URLs. |
| Plugins | Shared full-distribution `PluginRepository`; desktop full build enables plugin UI/runtime. | Same repository when plugins are enabled. | Compatible with TV `PluginSyncService` through the `plugins` table and `sync_push_plugins` RPC. Windows now pushes `repo_type = NUVIO_JS` so TV can keep its type hint. |
| Library | Shared `LibraryRepository`; desktop profile-scoped JSON payload. | Same shared repository and Supabase sync. | Compatible backend table and profile scoping. |
| Watched/progress | Shared `WatchedRepository` and `WatchProgressRepository`; desktop profile-scoped payloads. | Same shared repositories and conflict behavior. | Compatible backend tables and profile scoping; Trakt-vs-Supabase mode remains user setting dependent. |
| Collections | Shared `CollectionSyncService`; desktop payload storage. | Same shared service. | Compatible collection blob sync shape. |
| Home catalogs | Shared `HomeCatalogSettingsSyncService`; Windows uses the Mobile/KMP settings platform. | Same shared service. | TV has a separate TV settings platform and layout-specific exclusions, so catalog data is compatible but TV-only layout flags are not expected on Windows. |
| Debrid settings | Desktop `DebridSettingsStorage` uses DPAPI-backed `DesktopSecureStore` for API keys and exports/imports the Mobile settings blob. | Synced with Mobile/iOS through `ProfileSettingsSync`. | TV's current repo keeps debrid settings in local `DebridSettingsDataStore` and does not include them in `ProfileSettingsSyncService`, so TV-to-Windows debrid credential sync is not implemented by the existing TV backend contract. |
| Trakt settings | Desktop `TraktSettingsStorage` syncs settings payload through `ProfileSettingsSync`. | Same shared settings payload. | TV syncs `trakt_settings` inside its TV platform settings blob. Trakt auth tokens are local-only in both codebases. |
| Trakt auth tokens | Desktop `TraktAuthStorage` uses DPAPI-backed secure storage. | Same auth repository semantics, platform secure storage actual. | Not cross-device synced. This avoids silently copying OAuth refresh tokens through plaintext settings blobs. |
| Downloads | Windows-native downloader stores files under the configured Windows downloads directory. | Metadata model shared, file transfer is platform-specific. | Download files themselves are device-local by design. |

## Backend Contracts Used By Windows

- Add-ons: `addons` table plus `sync_push_addons`.
- Plugins: `plugins` table plus `sync_push_plugins`.
- Profile settings: `sync_pull_profile_settings_blob` and `sync_push_profile_settings_blob` with the shared Mobile/KMP platform key.
- Library: shared Mobile/KMP library sync repository.
- Watch progress and watched state: shared Mobile/KMP sync repositories.
- Collections: shared collection blob sync.
- Home catalogs: shared home catalog settings sync.

## Notes From The TV Comparison

- TV canonicalizes add-on URLs to base URLs, while Mobile/Windows normalize to `/manifest.json`. Both sides normalize before use, so remote rows from either side can be loaded by the other.
- TV's plugin sync carries `repo_type` so it can distinguish native JavaScript repositories from Android-only external DEX repositories. Windows only supports the JavaScript plugin runtime and pushes `NUVIO_JS`.
- TV profile settings use `p_platform = "tv"` because several settings are Android TV-only. Windows intentionally uses the Mobile/KMP settings platform so Android, iOS, and Windows stay aligned.
- TV debrid credentials and Trakt auth tokens are not currently synced through the inspected TV sync services. Adding that would require a deliberate backend secret-sync design rather than writing secrets into generic settings blobs.

## Implementation Guarantees For Windows

- Windows does not create a separate add-on store or plugin store. It participates in the same profile-scoped backend rows as Mobile and TV.
- Windows honors secondary-profile inheritance for add-ons and plugins through `usesPrimaryAddons` and `usesPrimaryPlugins`.
- Windows keeps secrets in `DesktopSecureStore`, backed by Windows DPAPI where available.
- Windows enables plugins and in-app trailer playback in the desktop feature policy; P2P remains disabled for the first Windows release.

## Remaining Parity Work

- Add a backend-backed, encrypted cross-device secret sync if debrid API keys or Trakt OAuth tokens must move between TV, Mobile, and Windows automatically.
- Decide whether per-scraper plugin enablement and scraper settings should sync. The current shared contract syncs plugin repository lists, not every local scraper toggle.
- Add integration tests against a test Supabase project once credentials and fixtures are available. Local unit tests can verify serialization and storage behavior, but they cannot prove live cross-device sync without backend access.
