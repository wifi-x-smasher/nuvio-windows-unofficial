# Nuvio Windows Unofficial v0.2.2 Draft Changelog

This is planned as a larger stability and desktop-experience release after v0.2.1. It focuses on the internal player, fullscreen behavior, high-resolution Windows UI polish, plugin/source reliability, and recent Nuvio mobile feature parity.

## Suggested Release Title

`v0.2.2 - MPV Playback, Fullscreen, Scaling, Stream Badges, and UI Polish`

## Highlights

- Added a new MPV-based internal desktop player backend for Windows.
- Kept the Nuvio-style Compose player controls as the visible overlay above video.
- Added bundled/discovered MPV runtime support for Windows playback.
- Kept external player support as a manual setting and in-player option.
- Fixed internal player controls rendering above video instead of disappearing behind the video surface.
- Fixed the center play/pause control so pause no longer behaves like stop/restart.
- Fixed audio track selection so changing tracks applies correctly during playback.
- Improved subtitle track handling and cleanup of external subtitle state.
- Improved player shutdown so video/audio does not continue after leaving playback or closing the app.
- Added desktop player keyboard controls, including pause and seek-style controls.
- Improved volume control integration for MPV-backed playback.
- Improved HDR/HDR10 tone-mapping quality by switching MPV render output from `rgba8` to `rgba16f` and restoring automatic dithering.

## Desktop And Fullscreen

- Improved desktop fullscreen behavior with proper fullscreen placement.
- Added F11 fullscreen support.
- Improved fullscreen toggle behavior to reduce crashes when switching between fullscreen and windowed modes.
- Added dark Windows title-bar styling to reduce the white title-bar issue.
- Improved 4K/high-resolution UI scaling behavior for better readability on large monitors.
- Added tests around desktop window/fullscreen behavior.

## Stream Results And Sources

- Added a setting to show or hide file size badges in stream results.
- Synced the size badge setting through the existing profile settings sync flow.
- Applied the size badge toggle in both stream selection and the player sources panel.
- Improved stream badge settings text so it is resource-backed and ready for localization.
- Added stream badge settings to Settings search.
- Improved plugin/addon stream execution handling so plugin scrapers can return results more reliably.
- Added tests around plugin execution behavior.
- Added horizontal arrow controls to stream provider tabs so users can reach off-screen addon/plugin results without relying on drag gestures.
- Added horizontal arrow controls in the player sources panel.
- Added horizontal arrow controls in the player episode stream source panel.

## Details, Catalogs, And Navigation

- Added catalog scroll position restore, matching recent Nuvio mobile behavior.
- Added horizontal arrow controls for season chips, season poster rows, and episode rows on the movie/series details page.
- Added horizontal arrow controls to season tabs in the in-player episode panel.
- Updated the meta/details action area to use a compact plus/check-style secondary action menu.
- Added a dedicated watched/unwatched secondary action in the details action menu.
- Made the app language bottom sheet scrollable.
- Kept the TV-style layout direction for Windows rather than stretching the mobile layout.

## Diagnostics And Stability

- Added desktop runtime/player logging to make crashes and playback failures easier to diagnose.
- Improved player lifecycle cleanup when backing out of playback or closing the app.
- Fixed cases where fullscreen/windowed fullscreen transitions could crash the app.
- Fixed app/window chrome behavior that caused poor desktop presentation.

## Technical Notes

- MPV is now the preferred Windows internal playback path.
- VLC/external playback remains available as fallback/manual playback.
- Current bundled MediaMP/libmpv still uses the OpenGL render API path, so true native HDR passthrough through `gpu-next` is not fully available yet.
- HDR/HDR10 rendering should look better than before, but full native HDR passthrough may require a newer libmpv/render integration or a native `vo=gpu-next` architecture.
- The current local build still outputs as `Nuvio-0.2.1.msi` until the app version is bumped for the v0.2.2 release.

## Verification So Far

- Desktop compile passed.
- Desktop tests passed.
- MSI package build passed.

## Suggested Release Notes Body

Nuvio Windows Unofficial v0.2.2 is a larger desktop-focused update. The main change is a reworked internal playback path using MPV, while keeping the Nuvio-style player controls as a Compose overlay. This should make playback feel much closer to the Android/mobile player while still keeping external player support available when needed.

This release also improves fullscreen handling, high-resolution UI scaling, stream source navigation, stream badge settings, catalog scroll restore, and details-page actions. Several horizontal rows now include visible arrow controls so Windows users are not forced to rely on drag gestures to reach off-screen seasons, episodes, or addon/plugin source tabs.

Notes: HDR/HDR10 rendering has been improved, but full native HDR passthrough is not completely solved yet because the current bundled MediaMP/libmpv render path still uses OpenGL rather than `gpu-next`.
