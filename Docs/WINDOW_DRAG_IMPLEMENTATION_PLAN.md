# Window Drag Implementation Plan

Goal: allow the undecorated Nuvio Windows app to be moved between monitors without touching the now-stable fullscreen, player, MPV, HDR, or plugin paths.

## Constraints

- Do not change MPV, MediaMP, VLC, subtitle rendering, HDR, stream loading, or plugin logic.
- Do not use `WindowPlacement.Fullscreen`.
- Do not use `GraphicsDevice.setFullScreenWindow`.
- Do not reintroduce native caption/title-bar hacks.
- Do not add raw AWT mouse listeners or Win32 hit-test handling.
- Do not allow drag UI to overlap player controls, app navigation, or hero trailer controls.

## Steps

- [x] Checkpoint the known-good player chrome overlap fix in GitHub before drag work.
- [x] Add a small Compose Desktop drag grip. `WindowDraggableArea` is unavailable in this Compose version, so use a constrained Compose pointer-input grip instead of native/AWT mouse hooks.
- [x] Show the drag grip only in normal browsing mode: `!isPlayerScreenActive && !isFullscreen`.
- [x] Keep the drag grip constrained near the custom desktop controls so it does not steal clicks from app navigation.
- [x] Evaluate snap-on-drop. Reuse `DesktopWindowChrome.applyNormalBounds(window)` after drag end; keep manual QA as the final confirmation on multi-monitor setups.
- [x] Run focused desktop tests.
- [x] Build a local MSI for final manual testing.

## Manual QA

- Drag the app from monitor 1 to monitor 2.
- Confirm the drag grip is absent inside the player.
- Confirm the drag grip is absent in fullscreen.
- Confirm F11 and Alt+Enter still enter/exit fullscreen cleanly after dragging.
- Confirm Esc still exits browsing fullscreen and still closes/goes back inside player.
- Confirm hero trailer controls do not overlap with desktop controls or drag grip.
- On different-DPI monitors, confirm the app does not jump off-screen or scale strangely.
