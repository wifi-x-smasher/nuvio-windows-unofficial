# Nuvio Windows Unofficial v0.2.2

This is one of the biggest updates since the first public Windows builds. It fixes a lot of rough edges from the previous releases, improves desktop playback, brings in several features from recent Android/mobile updates, and makes the Windows app feel much closer to a proper large-screen Nuvio experience.

This is still an unofficial community-maintained Windows port. It is not affiliated with or endorsed by the official Nuvio developers.

## New Additions

### Internal Playback

- Added an MPV-based internal desktop player path for Windows.
- Kept the Nuvio-style player controls as a Compose overlay above the video.
- Added bundled/discovered MPV runtime support for Windows playback.
- Kept external player support as both a Settings option and an in-player action.
- Added desktop keyboard controls for playback:
  - Space / Enter / K: play or pause
  - Left / Right: seek backward or forward
  - Up / Down: volume
  - M: mute
  - Backspace / Escape: close player or go back
- Routed player shortcuts through the desktop window dispatcher so controls continue working even when the native video surface has focus.
- Improved volume control integration for MPV-backed playback.

### Subtitle Controls

- Added subtitle delay/sync controls in the player.
- Added `-250 ms`, reset, and `+250 ms` subtitle sync actions.
- Applied subtitle delay to MPV through the player backend.
- Added readable subtitle delay formatting in the player UI.
- Improved subtitle track cleanup when switching between built-in and add-on subtitles.
- Improved preferred subtitle language matching, including regional variants such as `nl`, `nl-NL`, and `nl-BE`.

### Stream Badges And Recent Android Parity

- Added a setting to show or hide file size badges in stream results.
- Synced the size badge setting through the existing profile settings sync flow.
- Applied the size badge toggle in stream selection and in-player source panels.
- Added Android-style meta hero trailer playback on Windows when enabled in Settings.
- Added a hero trailer mute/volume toggle.
- Added catalog scroll position restore.
- Updated the meta/details action area to use the newer plus/check-style secondary action menu.
- Added a dedicated watched/unwatched action in the details action menu.
- Made the app language selection sheet scrollable.

### Desktop Navigation And Layout

- Added horizontal arrow controls to stream provider tabs.
- Added horizontal arrow controls in the player sources panel.
- Added horizontal arrow controls in the player episode stream source panel.
- Added horizontal arrow controls for season chips, season poster rows, and episode rows on the movie/series details page.
- Added horizontal arrow controls to season tabs in the in-player episode panel.
- Improved F11 and Alt+Enter fullscreen shortcut handling.
- Added dark Windows title-bar styling where the platform allows it.
- Improved 4K/high-resolution UI scaling for better readability on larger monitors.
- Improved Windows catalog/folder image decoding by decoding closer to measured card size.
- Improved collection/folder cover stability by using the measured card box instead of URL-only image sizing.
- Kept the Windows layout closer to the TV app rather than stretching the mobile layout.

### Sync And Continue Watching

- Added an add-on sync safety guard so local add-on state is not pushed back to the server until a successful server pull has completed.
- This reduces the risk of overwriting synced add-ons after fresh installs, logout/login edge cases, or incomplete local startup state.
- Audited Continue Watching behavior against the newer desktop/mobile direction.
- Confirmed support for wide TV-style Continue Watching cards, release/upcoming labels, new-season labels, hydrated details, and dropped-show filtering through the Trakt hidden/dropped progress path.

## Bug Fixes

### Player Fixes

- Fixed player controls rendering behind the video surface.
- Fixed the center play/pause control so pause no longer behaves like stop/restart.
- Fixed audio track switching during playback.
- Remembered manually selected audio tracks for the active video and reapplied the matching language/label choice when tracks refresh or the stream reloads.
- Improved player shutdown so video/audio does not continue after leaving playback or closing the app.
- Improved player lifecycle cleanup when backing out of playback.

### Subtitle Fixes

- Fixed add-on subtitle auto-selection so preferred subtitle language settings apply to add-on subtitles too.
- Fixed a case where a non-preferred built-in subtitle track could block a preferred add-on subtitle from being selected automatically.
- Fixed a state mismatch where choosing "None" could clear the UI selection without clearing an active external/add-on subtitle in the player.

### Add-ons, Sources, And Diagnostics

- Reworked Windows desktop add-on HTTP transport to use OkHttp, closer to Android behavior.
- Normalized encoded pipe characters in add-on request URLs so add-ons that rely on `|` in IDs or route parameters can respond correctly.
- Improved desktop add-on request headers, body handling, response limits, and failure handling.
- Added redacted diagnostics around add-on and stream-source failures to make bug reports easier to debug without leaking tokens or stream URLs.
- Improved stream-source scheduling so one slow or failing provider is less likely to block later providers.
- Added parallel completion handling in the main stream selection screen and in-player source/episode panels.
- Audited debrid cache/precheck handling; supported paths already annotate cache availability and filter unsupported uncached managed streams where the app has enough provider data.

### UI And Stability Fixes

- Fixed desktop hero trailer previews being dismissed immediately by normal no-error player state updates.
- Improved fullscreen/windowed transition handling to reduce crashes.
- Improved redaction for URLs and add-on diagnostics.
- Added desktop runtime/player logging to make crashes and playback failures easier to diagnose.
- Audited Trakt scrobble behavior after seeking; playback state handling now continues to restart scrobbling when playback resumes and flushes progress on pause/end/exit.

## Important Notes

### HDR In Internal Player

HDR/HDR10/HDR10+ playback in the internal player is still under debugging. Some HDR content may look dull or washed out compared with external players.

Workaround for now: use the in-player **Open in external player** option or set playback to external player in Settings for HDR-heavy content.

### Plugin Playback / Plugin Streams

Plugin stream results are still under debugging. Regular add-ons and connected-service add-ons should work, but some plugin-based sources may keep searching or return no usable streams.

This is not considered fully fixed yet and will be one of the main targets for the next debugging pass.

## Verification

- Desktop compile passed with JDK 21.
- Focused desktop tests passed for hero trailer selection, player keyboard shortcuts, subtitle selection, subtitle delay, debrid behavior, and desktop add-on transport.
- Local MSI packaging passed for the current candidate build.
