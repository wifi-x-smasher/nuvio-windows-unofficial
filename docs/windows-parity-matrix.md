# Windows Parity Matrix

Last updated: 2026-05-26

This matrix tracks the Windows desktop port against the shared Mobile/KMP product layer and the TV large-screen reference. Status values are:

- `Pass`: implemented and covered by local verification or shared code path.
- `Partial`: implemented enough to use, but still needs UI polish, live backend QA, or device QA.
- `Pending`: not implemented yet.
- `Platform-only`: intentionally scoped to another platform.

## Core Product Parity

| Area | Status | Windows scope | Evidence / next action |
| --- | --- | --- | --- |
| App launch | Pass | Compose Desktop entry point opens the shared `App()` surface. | `:composeApp:desktopTest` and packaging compile desktop classes. |
| Auth gate | Partial | Reuses shared `AuthRepository` and desktop secure storage. | Needs live Supabase sign-in and linked-device QA. |
| Profiles and PIN | Partial | Reuses shared profile/PIN repositories with desktop storage/crypto actuals. | Needs manual profile switch, PIN lock, avatar, and primary add-on/plugin inheritance QA. |
| Home | Partial | Shared Mobile home compiles and runs on desktop. | TV-style hero rows, focus restoration, and wide browsing polish remain Task 9. |
| Search/discover | Partial | Shared search and add-on-backed discovery compile on desktop. | Needs live add-on QA and keyboard/focus pass. |
| Details | Partial | Shared details screen compiles on desktop. | TV-style hero/detail wide layout remains Task 9. |
| Stream selection | Partial | Shared stream repository can use add-ons, debrid, and enabled plugins on desktop. | Needs live provider QA and TV-style source panel polish. |
| Player | Partial | Embedded VLCJ backend and external player fallback are implemented. | TV-style overlay/shortcut pass remains Task 10. |
| Subtitles | Partial | VLCJ subtitle track selection and external subtitle URI hooks are implemented. | Needs live file/HLS subtitle QA and styling pass. |
| Audio tracks | Partial | VLCJ audio track discovery/selection is implemented. | Needs live multi-audio media QA. |
| Playback speed/seek | Partial | VLCJ controller supports play/pause/seek/speed snapshots. | Needs full keyboard shortcut layer and manual playback QA. |
| Next episode | Partial | Shared next-episode rules are present. | TV-style end prompt remains Task 10. |
| Skip intro | Partial | Shared skip-intro logic is present. | Needs player overlay QA on desktop. |
| Watched/progress sync | Pass | Shared repositories and Supabase sync paths are reused. | Live account conflict QA still required. |
| Library | Pass | Shared library repository and profile-scoped desktop storage are present. | Live account conflict QA still required. |
| Collections | Pass | Shared collection repository/sync and desktop storage are present. | Live account conflict QA still required. |
| Add-ons | Pass | Shared add-on repository, install/enable/order state, and `sync_push_addons` contract are reused. | See `docs/windows-sync-parity-audit.md`. |
| Plugins | Partial | Shared full plugin repository/runtime is enabled on desktop; repo sync includes `repo_type = NUVIO_JS`. | Per-scraper enablement/settings sync is not part of the current backend contract. |
| Debrid | Partial | Desktop stores API keys through DPAPI and syncs Mobile/KMP debrid settings. | TV-to-Windows debrid credential sync needs a new encrypted backend secret-sync design. |
| Trakt settings | Partial | Shared Trakt settings sync is present. | Trakt OAuth tokens remain per-device, matching current Mobile/TV behavior. |
| Downloads | Pass | Windows downloader supports resumable `.part` files and safe deletion boundaries. | `:composeApp:desktopTest` covers path/download safety. |
| Settings | Partial | Shared settings compile, including add-ons, plugins, playback, debrid, TMDB, MDBList, Trakt. | Needs desktop layout and keyboard/focus QA. |
| Updates | Pass | Windows updater queries `NuvioWindows`, selects `.msi`/`.exe`, verifies SHA-256 sidecar when present, launches installer, and exits. | `:composeApp:packageMsi :composeApp:packageExe` produced local installers. |
| Packaging | Pass | MSI and EXE native distributions are configured. | Local artifacts: `Nuvio-0.1.24.msi` and `Nuvio-0.1.24.exe`. |

## TV Feel Parity

| Area | Status | Windows scope | Next action |
| --- | --- | --- | --- |
| Sidebar-first navigation | Pending | Desktop currently uses the shared app shell. | Build wide layout shell from TV sidebar reference. |
| Poster rows and hero browsing | Pending | Shared rows are usable, not TV-polished. | Port TV concepts into Compose Multiplatform components. |
| Focus restoration | Pending | No dedicated desktop focus restoration yet. | Track row/item focus keys on navigation return. |
| Keyboard navigation | Partial | Compose controls are keyboard reachable where shared UI supports it. | Add desktop shortcuts: arrows, Enter, Esc, Space, J/K/L, F, M, S, A, I, N. |
| Mouse behavior | Partial | Shared clickable UI works. | Add hover parity for focus affordances and right-click actions where useful. |
| Gamepad/remote | Pending | No dedicated gamepad abstraction yet. | Add key mapping and test Xbox/Bluetooth remote devices. |
| Player overlays | Pending | Core player works, TV overlay model is not ported. | Convert audio/subtitle/info/episode controls into wide side panels. |
| Still watching/post-play | Pending | Shared rules exist, desktop prompt polish is pending. | Port TV prompt behavior after player shortcuts. |

## Platform-Specific Notes

| Feature | Windows decision |
| --- | --- |
| Android TV recommendations/channels | Platform-only to Android TV. Do not claim on Windows. |
| Android package install permissions | Platform-only to Android. Windows launches `.msi`/`.exe` installers instead. |
| Android frame-rate matching/audio tunneling/PiP | Platform-only to Android/TV until native Windows equivalents are designed. |
| P2P/TorrServer parity | Disabled for first Windows release due native process and security scope. |
| Downloads folder | Defaults to the Windows videos/downloads location configured by the desktop downloader. |
| Secrets | Windows uses DPAPI-backed `DesktopSecureStore`; cross-device secret sync needs explicit encrypted backend support. |

## Verification Snapshot

- `.\gradlew.bat :composeApp:desktopTest --no-daemon --stacktrace --no-configuration-cache`: passed on 2026-05-26 with JDK 17.
- `.\gradlew.bat :composeApp:packageMsi :composeApp:packageExe --no-daemon --stacktrace --no-configuration-cache`: passed on 2026-05-26 with JDK 17.
- Generated installers:
  - `composeApp/build/compose/binaries/main/msi/Nuvio-0.1.24.msi`
  - `composeApp/build/compose/binaries/main/exe/Nuvio-0.1.24.exe`
