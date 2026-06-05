# Nuvio Windows Unofficial v0.2.4

Draft release notes for the next public build after v0.2.2.

This is a major stability release focused on the issues reported after the v0.2.2 build. The biggest fixes are the internal player, HDR playback, fullscreen behavior, and multi-monitor usability. The build has been tested locally and shared with users who previously reported playback/runtime problems.

## Major Highlights

- Internal player is finally usable on the reported Windows setups.
  - Fixed the "player runtime unavailable" path.
  - Fixed black-screen / endless-loading playback failures seen by multiple users.
  - Stabilized the MPV desktop runtime path so playback no longer silently falls into broken runtime states.
- HDR playback is finally corrected in the internal player for the reported dull/washed-out color cases.
  - The player now uses the stabilized MPV path that produced correct HDR color behavior in testing.
  - External player remains available from the player controls for hardware-specific edge cases.
- Fullscreen logic has been reworked.
  - Replaced the old unstable fullscreen path that could crash with "failed to launch JVM".
  - Added a borderless custom-window implementation so the white Windows title bar is gone.
  - F11, Alt+Enter, Esc, and the fullscreen button now use the same safer fullscreen state path.
- Multi-monitor use is improved.
  - Added a desktop drag grip for moving the borderless app between displays.
  - Added safe drag bounds so the app cannot vanish off-screen.
  - On drop, the app snaps to the target monitor work area instead of relying on stale monitor state.

## Player Fixes

- Fixed internal playback runtime detection and startup.
- Fixed HDR color output in the internal player.
- Fixed playback speed controls so speed changes apply instead of only updating the label.
- Fixed player window controls overlapping the playback UI.
- Hid desktop window controls while the player is active so playback controls remain clean.
- Kept the external-player option available inside the player.
- Improved runtime diagnostics around MPV playback and trailer audio selection.

## Fullscreen and Windowing

- Reworked desktop fullscreen around an undecorated window with custom controls.
- Fixed fullscreen crashes caused by the older fullscreen implementation.
- Fixed repeated F11 / Alt+Enter toggles through debounced fullscreen state handling.
- Added Esc fallback behavior:
  - During browsing, Esc exits fullscreen.
  - During playback, the player still handles Esc / Backspace first.
- Added a drag grip for borderless mode.
- Added multi-monitor-aware snap logic based on the window center point instead of stale `graphicsConfiguration` state.
- Added virtual-desktop clamping for negative-coordinate and multi-monitor setups.

## Trailer and Subtitle Fixes

- Fixed hero trailer playback.
- Fixed hero trailer audio mute/unmute behavior by attaching split trailer audio only after MPV has loaded the main video stream.
- Fixed subtitle style application for non-embedded subtitles.
- Fixed subtitle color/size/style changes not applying during playback.
- Improved built-in subtitle language display so users see readable language names instead of only short codes where possible.

## UI and Documentation

- Updated README screenshots with the latest UI captures.
- Refreshed README feature, requirements, playback, and known-limitation notes.
- Clarified that the app is a client-side interface and does not host or provide media content.

## Known Issue

- Plugin stream results are still under debugging.
  - Add-on-backed streams are working.
  - Some plugin-only sources may keep searching or return no streams.
  - This is the only major remaining issue planned for the next focused debugging pass.
