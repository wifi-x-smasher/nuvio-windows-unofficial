# Windows QA Checklist

Last updated: 2026-05-26

Use this checklist before tagging a Windows beta release. Run tests against a clean Windows user profile when possible so local desktop storage, secure storage, and updater behavior are not masked by previous runs.

## Preflight

- [ ] Confirm JDK 17 is active.
- [ ] Run `.\gradlew.bat :composeApp:desktopTest --no-daemon --stacktrace --no-configuration-cache`.
- [ ] Run `.\gradlew.bat :composeApp:packageMsi :composeApp:packageExe --no-daemon --stacktrace --no-configuration-cache`.
- [ ] Confirm `.msi` and `.exe` artifacts exist under `composeApp/build/compose/binaries/main`.
- [ ] Generate or verify `.sha256` sidecar files for release artifacts.
- [ ] Confirm no committed file contains local Supabase keys, Trakt secrets, debrid API keys, or user credentials.

## Environment Matrix

- [ ] Windows 11, 1920x1080, 100 percent scaling.
- [ ] Windows 11 laptop, 1366x768, 100 percent scaling.
- [ ] Windows 11, 4K display, 150 percent scaling.
- [ ] Mouse and trackpad.
- [ ] Keyboard only.
- [ ] Xbox controller or Bluetooth remote.
- [ ] Fresh install from `.msi`.
- [ ] Fresh install from `.exe`.
- [ ] Upgrade install over an older Windows build.

## Smoke Flow

- [ ] Launch app from Start menu.
- [ ] Launch app from installer completion action, if enabled.
- [ ] Close app with window close button.
- [ ] Relaunch and confirm local state persists.
- [ ] Toggle full screen/windowed/maximized where available.
- [ ] Confirm app data is written under the Windows desktop app data location.

## Auth And Profiles

- [ ] Signed-out launch reaches the expected auth/offline state.
- [ ] Anonymous/offline mode works without account-only affordance breakage.
- [ ] Full account sign-in succeeds.
- [ ] Profile list pulls from remote after sign-in.
- [ ] Create profile.
- [ ] Edit profile name/color/avatar.
- [ ] Enable and verify PIN lock.
- [ ] Switch profiles and verify profile-scoped data changes.
- [ ] Secondary profile with `usesPrimaryAddons` reads primary add-ons.
- [ ] Secondary profile with `usesPrimaryPlugins` reads primary plugin repositories.

## Sync

- [ ] Add an add-on on Android/mobile, then sign in on Windows and confirm it appears.
- [ ] Add an add-on on Windows, then confirm Android/mobile receives it.
- [ ] Add an add-on on TV, then confirm Windows receives it.
- [ ] Reorder add-ons on Windows and confirm the order syncs.
- [ ] Disable an add-on on Windows and confirm enabled state syncs.
- [ ] Add a JavaScript plugin repository on Windows and confirm TV sees it as `NUVIO_JS`.
- [ ] Add a JavaScript plugin repository on TV and confirm Windows sees it.
- [ ] Confirm Android-only external DEX repositories from TV are not presented as runnable Windows plugins.
- [ ] Add library item on Windows and confirm mobile/TV receives it.
- [ ] Add collection/folder on Windows and confirm mobile/TV receives it.
- [ ] Watch progress written on Windows appears on mobile/TV.
- [ ] Watched state written on mobile/TV appears on Windows.
- [ ] Confirm Trakt OAuth tokens are not silently copied between devices.
- [ ] Confirm debrid credentials sync between Mobile and Windows if present in the shared Mobile settings blob.
- [ ] Confirm TV debrid credentials remain local unless a future encrypted secret-sync feature is implemented.

## Browsing

- [ ] Home loads after auth/profile gate.
- [ ] Continue watching row appears with expected progress.
- [ ] Catalog rows load from installed add-ons.
- [ ] Search returns add-on results.
- [ ] Details screen opens from home, search, library, and collection.
- [ ] Seasons and episodes render correctly.
- [ ] Person/details navigation works.
- [ ] Back navigation returns to the expected previous screen.
- [ ] Wide desktop layout does not rely on phone-only bottom navigation at 900 dp or wider.

## Streams And Playback

- [ ] Stream list opens from a movie.
- [ ] Stream list opens from an episode.
- [ ] Add-on streams are grouped and selectable.
- [ ] Debrid stream preparation works when credentials are configured.
- [ ] Embedded VLCJ playback starts for a user-authorized local file.
- [ ] Embedded VLCJ playback starts for a user-authorized HLS stream.
- [ ] Play/pause works.
- [ ] Seek forward/back works.
- [ ] Playback speed changes.
- [ ] Audio track list appears for multi-audio media.
- [ ] Subtitle track list appears for subtitle media.
- [ ] External subtitle URI can be selected.
- [ ] Snapshot/progress updates persist after leaving the player.
- [ ] Next episode prompt appears where applicable.
- [ ] Skip-intro prompt appears where applicable.
- [ ] External VLC launch works when VLC is installed.
- [ ] External mpv launch works when mpv is installed.
- [ ] System default external player launch works for local files.
- [ ] Playback headers are sanitized before embedded or external playback.

## Downloads

- [ ] Start a direct download.
- [ ] Pause/cancel app and confirm `.part` resume behavior.
- [ ] Complete download and confirm final file path.
- [ ] Open downloaded file.
- [ ] Show downloaded file in Explorer.
- [ ] Delete downloaded file from within the app.
- [ ] Confirm delete refuses paths outside the managed downloads directory.

## Settings And Integrations

- [ ] Theme settings persist.
- [ ] Poster card style persists.
- [ ] Playback settings persist.
- [ ] Add-ons settings page supports add, remove, refresh, enable, disable, and reorder.
- [ ] Plugins settings page supports add, remove, refresh, enable, disable, and test where supported.
- [ ] TMDB settings persist.
- [ ] MDBList settings persist.
- [ ] Debrid settings persist and secrets are not present in plain JSON/properties files.
- [ ] Trakt connect/disconnect flow opens browser and returns via callback.
- [ ] Trakt library/watch progress modes behave as selected.
- [ ] Episode release notification settings do not expose unsupported Windows notification behavior unless implemented.

## Updater And Release

- [ ] GitHub release query uses `NuvioMedia/NuvioWindows`.
- [ ] Release filtering targets the `main` Windows channel.
- [ ] `.msi` and `.exe` assets are accepted.
- [ ] APK assets are not selected by Windows.
- [ ] SHA-256 sidecar is downloaded and verified when present.
- [ ] Checksum mismatch blocks install.
- [ ] Installer launches from the downloaded path.
- [ ] App exits after successful installer launch.
- [ ] Ignore-release action persists.
- [ ] GitHub Actions workflow uploads `.msi`, `.exe`, and `.sha256` artifacts.

## Accessibility And Input

- [ ] Keyboard focus is visible on all interactive controls.
- [ ] Tab/Shift+Tab traversal is sane in settings pages.
- [ ] Arrow-key navigation works in rows and stream lists.
- [ ] Enter activates focused items.
- [ ] Esc/back behavior is predictable.
- [ ] Text is readable at 100 percent and 150 percent scaling.
- [ ] No desktop-wide content is clipped at 1366x768.
- [ ] No overlapping text or controls in player overlays.

## Release Blockers

- [ ] No `Pending` MVP items remain in `docs/windows-parity-matrix.md`.
- [ ] No known crash on launch, auth, profile selection, stream selection, playback, or settings.
- [ ] No plaintext secret leakage in app data or committed files.
- [ ] Installer and updater paths have been verified on a clean Windows machine.
- [ ] GPL/license obligations and third-party native dependency notices are reviewed for distribution.
