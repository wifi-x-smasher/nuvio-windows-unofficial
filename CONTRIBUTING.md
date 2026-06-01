# Contributing

Thanks for taking an interest in Nuvio Windows Unofficial.

This repository is an unofficial Windows desktop port of Nuvio. It is not the upstream Nuvio Mobile or Nuvio TV project, so contributions here should stay focused on the Windows port.

## What Helps

- Reproducible Windows bugs with clear steps.
- Fixes for installer, updater, desktop storage, deep links, keyboard/mouse navigation, or Windows playback behavior.
- UI fixes that make the desktop app closer to the Nuvio TV experience without changing the Nuvio design language.
- Documentation corrections for Windows installation, releases, or self-building.

## Before Opening a PR

Please keep pull requests small and focused. Include:

- What changed.
- Why it was needed.
- How you tested it.
- Screenshots or short clips for visible UI changes.

Large feature work should be discussed in an issue first.

## Local Development

Useful commands on Windows:

```powershell
.\gradlew.bat :composeApp:desktopTest
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:packageMsi :composeApp:packageExe
```

Do not commit local secrets, generated installers, build output, keystores, tokens, or `local.properties`.

## Trakt and Other Credentials

`local.properties` is a private build-time file. It is not read by an installed public release.

If you self-build with Trakt or other private configuration, keep those values local to your machine or private CI secrets. Do not include personal credentials in public installers or pull requests.
