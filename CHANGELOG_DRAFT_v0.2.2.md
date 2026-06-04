# Nuvio Windows Unofficial v0.2.2 Draft Changelog

This is planned as a larger desktop stability and playback release after v0.2.1. It focuses on Windows internal playback, add-on/plugin compatibility, subtitle controls, high-resolution UI polish, source navigation, and recent Nuvio mobile parity items.

## Suggested Release Title

`v0.2.2 - MPV Playback, Add-on Fixes, Subtitle Sync, and Desktop Polish`

## Highlights

- Added an MPV-based internal desktop player path for Windows.
- Kept the Nuvio-style Compose player controls as the visible overlay above video.
- Added bundled/discovered MPV runtime support for Windows playback.
- Kept external player support as a manual setting and in-player option.
- Fixed internal player controls rendering above video instead of disappearing behind the video surface.
- Fixed the center play/pause control so pause no longer behaves like stop/restart.
- Fixed audio track selection so changing tracks applies correctly during playback.
- Improved player shutdown so video/audio does not continue after leaving playback or closing the app.
- Added desktop player keyboard controls for playback, seek, volume, mute, and back behavior.
- Routed player keyboard shortcuts through the desktop window dispatcher so Space, arrows, Backspace, Escape, K/J/L, and M still work when the MPV/native video surface has focus.
- Improved volume control integration for MPV-backed playback.

## Subtitle Improvements

- Added subtitle delay/sync controls in the player.
- Added `-250 ms`, reset, and `+250 ms` subtitle sync actions.
- Applied subtitle delay to MPV through the `sub-delay` property.
- Added readable subtitle delay formatting in the player UI.
- Improved subtitle track cleanup when switching between built-in and add-on subtitles.
- Fixed add-on subtitle auto-selection so preferred subtitle language settings apply to add-on subtitles too.
- Fixed a case where a non-preferred built-in subtitle track could block a preferred add-on subtitle from being selected automatically.
- Added generic language matching for add-on subtitle preferences, including regional variants such as `nl`, `nl-NL`, and `nl-BE`.
- Fixed a state mismatch where choosing "None" could clear the UI selection without clearing an active external/add-on subtitle in the player.

## Add-ons, Plugins, And Stream Sources

- Reworked Windows desktop add-on HTTP transport to use OkHttp, closer to the Android implementation and the working experimental Windows fork behavior.
- Normalized encoded pipe characters in add-on request URLs so add-ons that rely on `|` in IDs or route parameters can respond correctly.
- Improved desktop add-on request headers, body handling, response limits, and failure handling.
- Added redacted diagnostics around add-on and stream-source failures to make public bug reports easier to debug without leaking tokens or stream URLs.
- Improved plugin/add-on stream execution handling so plugin scrapers can return results more reliably.
- Reworked plugin scraper scheduling so Windows isolates plugin scrapers instead of letting one slow or failing scraper block every later provider.
- Applied parallel plugin scraper completion handling in both the main stream selection screen and the in-player source/episode panels.
- Improved diagnostics for plugin timeouts and scraper-level JavaScript failures so broken scrapers can be identified without hiding results from working scrapers.
- Added tests around desktop add-on URL normalization, header sanitization, request body handling, and redaction.
- Added horizontal arrow controls to stream provider tabs so users can reach off-screen add-on/plugin results without relying on drag gestures.
- Added horizontal arrow controls in the player sources panel.
- Added horizontal arrow controls in the player episode stream source panel.

## Stream Badges And Mobile Parity

- Added a setting to show or hide file size badges in stream results.
- Synced the size badge setting through the existing profile settings sync flow.
- Applied the size badge toggle in both stream selection and the player sources panel.
- Improved stream badge settings text so it is resource-backed and ready for localization.
- Added stream badge settings to Settings search.
- Added catalog scroll position restore, matching recent Nuvio mobile behavior.
- Updated the meta/details action area to use a compact plus/check-style secondary action menu.
- Added a dedicated watched/unwatched secondary action in the details action menu.
- Made the app language bottom sheet scrollable.

## Desktop And Navigation

- Added Backspace as a player back/close shortcut alongside Escape.
- Kept existing player shortcuts: Space/Enter/K for play-pause, left/right for seek, up/down for volume, and M for mute.
- Improved F11 and Alt+Enter fullscreen shortcut handling.
- Improved fullscreen/windowed transition handling to reduce crashes.
- Added dark Windows title-bar styling to reduce the white title-bar issue where the platform allows it.
- Improved 4K/high-resolution UI scaling behavior for better readability on large monitors.
- Added horizontal arrow controls for season chips, season poster rows, and episode rows on the movie/series details page.
- Added horizontal arrow controls to season tabs in the in-player episode panel.
- Kept the TV-style layout direction for Windows rather than stretching the mobile layout.

## HDR / MPV Improvements

- Centralized Windows MPV HDR/color options so they can be audited and tested.
- Applied MPV color/HDR options earlier during player initialization.
- Aligned the Windows MPV/OpenGL render options with the working CreepsoOff/MediaMP baseline, including `vo=libmpv`, `fbo-format=rgba8`, `dither-depth=no`, `video-sync=audio`, and `video-timing-offset=0.0`.
- Added diagnostics and tests that document the current MediaMP/libmpv renderer limitation.
- Added tests around the selected MPV HDR option profile.

## Diagnostics And Stability

- Added desktop runtime/player logging to make crashes and playback failures easier to diagnose.
- Improved redaction for URLs and add-on diagnostics so sensitive query parameters are not written to logs.
- Improved player lifecycle cleanup when backing out of playback or closing the app.
- Added desktop window recovery tests for foreground/focus repaint behavior.
- Added tests for fullscreen shortcut handling and desktop window placement decisions.
- Desktop compile verification passed before local MSI packaging.
- Local MSI packaging passed with the current candidate build.

## Technical Notes

- MPV is now the preferred Windows internal playback path.
- VLC/external playback remains available as fallback/manual playback.
- Current bundled MediaMP/libmpv still uses the OpenGL render API path, so true native HDR passthrough through `gpu-next` is not fully available yet.
- HDR/HDR10 rendering now follows the working CreepsoOff-style MediaMP baseline, but full native HDR passthrough may still require a newer libmpv/render integration or a native `vo=gpu-next` architecture.
- Fullscreen shortcuts and transition stability are improved, but a dedicated borderless fullscreen architecture is still a future follow-up if users continue seeing title-bar/platform issues.
- The current local test build still outputs as `Nuvio-0.2.1.msi` until the app version is bumped for the v0.2.2 release.

## Verification So Far

- `:composeApp:compileKotlinDesktop` passed with JDK 21.
- Targeted `:composeApp:desktopTest` passed for hero trailer selection, player keyboard shortcuts, subtitle selection, and subtitle delay.
- `:composeApp:packageMsi --no-daemon --no-configuration-cache` passed in an earlier local candidate and should be rerun for the final v0.2.2 release candidate.
- Latest local MSI path after packaging:
  `S:\Nuvio\NuvioWindows\composeApp\build\compose\binaries\main\msi\`
- Add the final MSI SHA-256 here after the release candidate is packaged.

## Suggested Release Notes Body

Nuvio Windows Unofficial v0.2.2 is a desktop-focused stability and playback update. The main change is a reworked internal playback path using MPV, while keeping the Nuvio-style player controls as a Compose overlay. External player support remains available as a setting and from inside the player.

This release also improves add-on/plugin source compatibility on Windows, subtitle sync controls, preferred add-on subtitle language selection, fullscreen shortcuts, high-resolution UI scaling, stream source navigation, stream badge settings, catalog scroll restore, and details-page actions.

Notes: HDR/HDR10 rendering has been improved, but full native HDR passthrough is not completely solved yet because the current bundled MediaMP/libmpv render path still uses OpenGL rather than `gpu-next`. Fullscreen shortcut handling is improved, but a deeper borderless fullscreen architecture may still be needed later for some Windows setups.
