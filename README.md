# viit-tracker

Attendance tracking app (Kotlin Multiplatform + Jetpack Compose).

## Build variants

Two compile-time distribution flavors, so the two update mechanisms can never
run in the same APK/AAB:

| | `github` | `play` |
|---|---|---|
| Application id (release) | `com.vignan.tracker` | `com.vignan.tracker` |
| Application id (debug) | `com.vignan.tracker.debug` | `com.vignan.tracker.debug` |
| Updates | in-app updater (`version.json` + APK download) | Google Play |
| `REQUEST_INSTALL_PACKAGES` | yes | **no** |
| `FileProvider` | yes | **no** |
| Output | universal + per-ABI APKs | AAB |
| Settings label | `… · APK` | `… · Play Store` |

Both debug variants install side-by-side with the release app because of the
`.debug` suffix (the two debug flavors share that package, so they replace each
other). The release package is the same for both flavors: a device has either
the GitHub build or the Play build, not both.

Local debug builds:

```bash
./gradlew :androidApp:assembleGithubDebug   # in-app updater build
./gradlew :androidApp:assemblePlayDebug     # Play updater build
```

## Play Store AAB

```bash
VERSION_CODE=3 VERSION_NAME=1.1.0 ./gradlew :androidApp:bundlePlayRelease
```

Output:

```text
androidApp/build/outputs/bundle/playRelease/androidApp-play-release.aab
```

Upload that AAB to Play Console. Play generates the device-specific split APKs;
do not upload a universal APK for the Play flavor.

> **Warning:** if `androidApp/key.properties` is absent, the release AAB is
> debug-signed. Never upload a debug-signed AAB to Play — Play locks the app
> signing key at the first upload, and later release-key builds will be
> rejected as updates. Configure the keystore first (see **Signing** below).

Local AAB smoke test:

```bash
bundletool build-apks \
  --bundle=androidApp/build/outputs/bundle/playRelease/androidApp-play-release.aab \
  --output=/tmp/viit-play.apks \
  --mode=universal
bundletool install-apks --apks=/tmp/viit-play.apks
```

This tests install/runtime only. Play In-App Updates must be tested through a
Play internal-testing track.

## GitHub release APKs

```bash
VERSION_CODE=3 VERSION_NAME=1.1.0 ENABLE_ABI_SPLITS=true \
  ./gradlew :androidApp:assembleGithubRelease
```

Outputs:

```text
androidApp/build/outputs/apk/github/release/androidApp-github-universal-release.apk
androidApp/build/outputs/apk/github/release/androidApp-github-arm64-v8a-release.apk
androidApp/build/outputs/apk/github/release/androidApp-github-armeabi-v7a-release.apk
androidApp/build/outputs/apk/github/release/androidApp-github-x86_64-release.apk
```

Without `ENABLE_ABI_SPLITS=true` only the single APK is built.

## Releases (CI/CD)

`.github/workflows/android-release.yml` has two jobs:

1. **github** — builds universal + per-ABI APKs, publishes a GitHub Release,
   then regenerates `version.json` on `main`.
2. **play-bundle** — builds the Play AAB artifact and verifies its merged
   manifest has no `REQUEST_INSTALL_PACKAGES`. Upload to Play Console is manual
   until a service-account publishing step is added.

**Trigger a release:**

```bash
# optional: per-release bullets for the in-app update sheet
printf '* Faster dashboard loads\n* Fixed stale projections\n' > changelog/1.1.0.md
git add changelog/1.1.0.md
git commit -m "changelog: 1.1.0"

git tag v1.1.0
git push origin v1.1.0
```

The GitHub job publishes:

- `viit-tracker-1.1.0.apk` — universal, use this when unsure
- `viit-tracker-1.1.0-arm64-v8a.apk`, `…-armeabi-v7a.apk`, `…-x86_64.apk` — per-ABI splits

**Manual build (no release):** Actions → *Build & release APKs / Play AAB* → *Run workflow*
— uploads the APKs and Play AAB as workflow artifacts (`1.0-dev.<run number>`).

**Signing:** add repository secrets so CI signs with your release keystore:

```bash
gh secret set KEYSTORE_BAS64 < <(base64 -w0 ~/JKSKEYS/viit-tracker-release.jks)
gh secret set KEYSTORE_PASSWORD --body '...'
gh secret set KEY_ALIAS --body '...'
gh secret set KEY_PASSWORD --body '...'
```

Without the secrets CI still builds debug-signed artifacts for sideload testing.

## Play signing and package name

Keep the same package (`com.vignan.tracker`) and use the **same app signing
certificate** for both channels.

- Play App Signing signs delivered APKs with the app signing key.
- GitHub APKs must be signed with that same key to update Play installs.
- Best: enroll the existing release keystore as the app signing key on the
  first Play upload.
- If Play uses a different signing key, Android rejects cross-channel updates
  with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; switching channels requires an
  uninstall and loses app data.

Build both channels from the same tag with the same `VERSION_CODE`. Play
requires a strictly increasing versionCode that is never reused.

## In-app updates

The `github` flavor only:

- **Manifest:** `version.json` on `main`, fetched **every app open** from
  `raw.githubusercontent.com` (CDN — no `api.github.com` calls, no rate
  limits), with a cache-busting query and one retry on failure.
- **Comparison:** each APK's *actual* versionCode is read at build time and
  published per ABI (`apk_builds`), so the app compares its own code against
  the code of the APK built for its ABI. AGP 9 currently gives all ABI variants
  the same code; verify after changing build tooling.
- **Download:** the device's primary ABI picks the matching split APK from
  `apk_urls` (falls back to the universal `apk_url`), verified against the
  manifest's SHA-256 before the system installer ever sees the file.
- **UI:** floating toast at the bottom (`Checking…` / `✓ Up to date` /
  `! Couldn't check`) and a bottom sheet with version badge, "what's new"
  bullets, download progress + cancel, checksum verify and install handoff.
  `force_update: true` makes the sheet non-dismissable.
- **Failure:** a bad/missing manifest only flashes the toast — the app never
  blocks or crashes over an update check.

The `play` flavor has no GitHub updater code and no self-install permission. It
uses Google Play's in-app update API (flexible normally, immediate when the
Play update priority is 4+), and Play's normal auto-update remains the primary
updater.

So: publish a tag → CI updates `version.json` → GitHub-flavor users see the
update sheet on next app open. Play-flavor users update through Play.
