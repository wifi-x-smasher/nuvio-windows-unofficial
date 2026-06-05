# Title

Nuvio Windows Unofficial v0.2.4 - major player, HDR, fullscreen and multi-monitor fixes

# Body

Hey everyone,

Quick follow-up to my earlier Nuvio Windows Unofficial post.

After the v0.2.2 release, a few people reported serious issues: internal playback not starting, black-screen loading, fullscreen behaving badly, HDR looking washed out, and the app being awkward on multi-monitor setups. A couple of comments were blunt and questioned whether this port should be trusted at all. That was not fun to read, but honestly, I understand why people reacted that way when the build did not work properly on their systems.

I spent most of today debugging this with logs, local testing, and feedback from users who were actually hitting the problems. The next build, v0.2.4, is mainly a stability release to fix those core issues instead of adding flashy new things.

What changed:

- Internal player runtime is fixed
- Black-screen / endless-loading playback issues are fixed for the users who tested the new build
- HDR colors in the internal player are now fixed in testing
- Fullscreen has been reworked with a new borderless window implementation
- White Windows title bar is gone
- F11, Alt+Enter, Esc, and the fullscreen button should now behave more consistently
- Added a drag grip so the borderless app can be moved to another monitor
- Added safer multi-monitor snap behavior so the app does not vanish off-screen while dragging
- Fixed playback speed controls
- Fixed hero trailer playback and trailer audio mute/unmute
- Fixed subtitle style changes not applying properly
- Updated the README screenshots and notes

The one major thing still not fixed:

- Plugin streams still do not return results properly in this Windows build. Add-on-backed streams are working, but plugin-only sources may still keep searching or show no streams. This is the next big debugging item.

As usual, this is an unofficial community-maintained Windows port and is not affiliated with or endorsed by the official Nuvio developers. It does not host or provide any media content.

If you had issues with v0.2.2, especially playback or fullscreen issues, please try v0.2.4 when it is available and let me know what changed for you. Logs are genuinely useful here, especially with your Windows version, GPU, and whether you are using one monitor or multiple monitors.

GitHub:
https://github.com/wifi-x-smasher/nuvio-windows-unofficial
