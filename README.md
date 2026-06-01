# Nuvio Windows Unofficial

Unofficial Windows desktop client for Nuvio, built as a community and experimental port. This project is not affiliated with, sponsored by, or endorsed by the official Nuvio developers.

Nuvio trademarks, branding, upstream code, and related assets belong to their respective owners. This repository exists to make the Windows desktop port easier to test, improve, and distribute.

## Status

This Windows port is under active development. Expect beta-level behavior until the release checklist is complete.

Current focus:

- Windows desktop packaging with MSI and EXE installers.
- TV-style desktop UI and keyboard/mouse navigation.
- Internal desktop playback with external player fallback.
- Shared Nuvio sync flows for accounts, profiles, add-ons, catalogs, collections, watched state, progress, and stream badge settings.
- Windows update checks through this repository's GitHub Releases.

## Downloads

When public releases are available, download the latest Windows installer from:

https://github.com/wifi-x-smasher/nuvio-windows-unofficial/releases/latest

Preferred installer: `.msi`

Release assets should include SHA-256 checksum sidecar files. Verify the checksum when possible before installing.

## Important Disclaimer

Nuvio Windows Unofficial is only a client interface. It does not host, store, provide, or distribute media content. Any media, metadata, catalogs, extensions, or streams are provided by user-installed add-ons, integrations, or user-provided sources.

Use the app only with content you own or are otherwise authorized to access. You are responsible for the add-ons and sources you install.

## First Launch

On a clean install, the app should ask each user to sign in or continue locally. If a user has configured a profile PIN, the app should request that PIN through the normal Nuvio profile flow.

User account data, Trakt tokens, debrid credentials, and local settings are stored per Windows user profile. They are not intended to be bundled in release installers.

## Development

Clone the repository:

```bash
git clone https://github.com/wifi-x-smasher/nuvio-windows-unofficial.git
cd nuvio-windows-unofficial
```

Create a local config file from the example:

```bash
cp local.example.properties local.properties
```

`local.properties` is private machine-local configuration. Do not commit it.

Required for account sync builds:

```properties
SUPABASE_URL=...
SUPABASE_ANON_KEY=...
```

Optional integrations:

```properties
TRAKT_REDIRECT_URI=nuvio://auth/trakt
TRAKT_CLIENT_ID=...
TRAKT_CLIENT_SECRET=...
PREMIUMIZE_CLIENT_ID=...
```

For public release builds, do not use personal credentials from your local machine. Public release builds intentionally do not include Trakt OAuth credentials unless the maintainer makes a separate release decision.

### Trakt for self-builders

Trakt authentication requires OAuth app credentials. To avoid shipping a shared client secret in public installers, the public Windows release workflow only requires Supabase configuration.

If you build the app yourself and want Trakt login, create your own Trakt API app and add its credentials to your private `local.properties`:

```properties
TRAKT_REDIRECT_URI=nuvio://auth/trakt
TRAKT_CLIENT_ID=your_trakt_client_id
TRAKT_CLIENT_SECRET=your_trakt_client_secret
```

Do not commit those values. Do not put personal Trakt credentials into public release builds.

### GitHub Actions release config

The Windows release workflow follows the upstream release pattern: GitHub Actions receives a base64-encoded `local.properties` file through one repository secret.

Required repository secret:

```text
LOCAL_PROPERTIES_BASE64
```

The decoded file must include at least:

```properties
SUPABASE_URL=...
SUPABASE_ANON_KEY=...
```

For the current public-release setup, the release `local.properties` should be minimal:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key
```

Create `LOCAL_PROPERTIES_BASE64` from that minimal release file, not from personal development credentials. Trakt OAuth credentials are intentionally left for self-builders or a future backend OAuth flow.

Useful Windows commands:

```powershell
.\gradlew.bat :composeApp:desktopTest
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:packageMsi :composeApp:packageExe
```

The packaged app version comes from:

```text
iosApp/Configuration/Version.xcconfig
```

## Project Structure

- `composeApp/` contains the Kotlin Multiplatform and Compose Multiplatform app.
- `composeApp/src/commonMain/` contains shared UI, feature logic, repositories, and models.
- `composeApp/src/desktopMain/` contains Windows desktop integrations, update support, desktop storage, deep links, and playback adapters.
- `composeApp/src/androidMain/` and `composeApp/src/iosMain/` are retained from the upstream shared codebase.
- `docs/` contains Windows parity, QA, and release notes.

## Updates

The Windows app checks this repository's GitHub Releases:

```text
wifi-x-smasher/nuvio-windows-unofficial
```

The updater supports `.msi` and `.exe` assets and verifies SHA-256 sidecar files when they are present. Releases must be published, not left as drafts, before installed apps can discover them.

## Security Notes

- Never commit `local.properties`, `.env` files, keystores, signing keys, API secrets, auth tokens, or generated build output.
- Release installers should be built from GitHub Actions or another clean release environment.
- Do not upload locally built installers if they were packaged with personal Trakt, Supabase, or other private credentials.
- Test public installers on a clean Windows user profile before publishing.

## License

This project is distributed under the GNU General Public License v3.0, matching the upstream Nuvio codebase license. See [LICENSE](./LICENSE).

## Credits

This project is derived from the open-source Nuvio Mobile and Nuvio TV projects. Credit belongs to the original Nuvio developers and contributors.
