<div align="center">

  <img src="composeApp/src/commonMain/composeResources/drawable/app_logo_wordmark.png" alt="Nuvio" width="300" />
  <br />
  <br />

  [![License][license-shield]][license-url]

  <p>
    An unofficial Windows desktop port of Nuvio.
    <br />
    Stremio addon ecosystem • Windows desktop • TV-style experience
  </p>

</div>

## About

Nuvio Windows Unofficial is a community-maintained Windows desktop port of Nuvio. It is built from the open-source Nuvio mobile codebase and shaped for a larger-screen desktop experience inspired by NuvioTV.

This project is not affiliated with, sponsored by, or endorsed by the official Nuvio developers. Nuvio trademarks, branding, upstream code, and related assets belong to their respective owners.

The app works as a client-side interface for Nuvio accounts, profiles, add-ons, catalogs, collections, watch progress, stream badges, and playback. It does not host or provide media content.

## Installation

Download the latest Windows installer from [GitHub Releases](https://github.com/wifi-x-smasher/nuvio-windows-unofficial/releases/latest).

Recommended installer: `.msi`

If a release is still marked as a draft, download the installer from the successful GitHub Actions run artifacts instead.

## Development

```bash
git clone https://github.com/wifi-x-smasher/nuvio-windows-unofficial.git
cd nuvio-windows-unofficial
```

Useful Windows commands:

```powershell
.\gradlew.bat :composeApp:desktopTest
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:packageMsi :composeApp:packageExe
```

The app version is read from `iosApp/Configuration/Version.xcconfig`.

### Project Structure

- `composeApp/` contains the Kotlin Multiplatform and Compose Multiplatform app.
- `composeApp/src/commonMain/` contains shared UI, feature logic, repositories, and models.
- `composeApp/src/desktopMain/` contains Windows-specific storage, deep links, updater support, and playback integration.
- `composeApp/src/androidMain/` and `composeApp/src/iosMain/` are retained from the upstream shared codebase.

### Maintainer Notes

Public Windows builds are created through GitHub Actions. The release workflow expects a repository secret named `LOCAL_PROPERTIES_BASE64`, which is a base64-encoded `local.properties` file used only during CI.

For the current public release flow, that file should only include the shared app backend configuration needed for sign-in and sync. Do not include personal account credentials, Trakt tokens, debrid tokens, or local testing values in release builds.

### Trakt

Public Windows installers do not currently include Trakt OAuth app credentials. Normal users do not need to create or place a `local.properties` file anywhere after installing the app; the installed app does not read configuration from the source repository.

For now, Trakt login only works in builds that were compiled with Trakt OAuth configuration already present at build time. Self-builders who want Trakt login can create their own Trakt API app and add the Trakt client ID, client secret, and redirect URI to their private build environment before compiling.

Do not publish public installers built with personal Trakt account tokens, debrid tokens, or other user credentials. A future Windows-safe Trakt flow should avoid shipping a shared client secret in the desktop app.

## Updates

The Windows app checks published releases from this repository:

```text
wifi-x-smasher/nuvio-windows-unofficial
```

Draft releases are not visible to installed apps. Publish a release after testing if you want the in-app updater to find it.

## Legal & DMCA

Nuvio Windows Unofficial functions solely as a client-side interface for browsing metadata and playing media provided by user-installed extensions and/or user-provided sources. It is intended for content the user owns or is otherwise authorized to access.

Nuvio Windows Unofficial is not affiliated with any third-party extensions, catalogs, sources, or content providers. It does not host, store, or distribute any media content.

For comprehensive legal information from the upstream project, including the full disclaimer, third-party extension policy, and DMCA/Copyright information, visit the [Legal & Disclaimer Page](https://nuvioapp.space/legal).

## Built With

- Kotlin Multiplatform
- Compose Multiplatform
- Kotlin
- VLCJ / LibVLC desktop playback integration
- GitHub Releases for Windows update checks

## License

This project is distributed under the GNU General Public License v3.0, matching the upstream Nuvio codebase license. See [LICENSE](./LICENSE).

## Credits

This project is derived from the open-source Nuvio Mobile and Nuvio TV projects. Credit belongs to the original Nuvio developers and contributors.

<!-- MARKDOWN LINKS & IMAGES -->
[license-shield]: https://img.shields.io/github/license/wifi-x-smasher/nuvio-windows-unofficial.svg?style=for-the-badge
[license-url]: https://github.com/wifi-x-smasher/nuvio-windows-unofficial/blob/main/LICENSE
