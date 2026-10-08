# viit-tracker

Attendance tracking app (Kotlin Multiplatform + Jetpack Compose).

## Build variants

| | Debug | Release |
|---|---|---|
| Application id | `com.vignan.tracker.debug` | `com.vignan.tracker` |
| Version name | `1.0-debug` | `1.0` (or tag/CI value) |
| Launcher name | `viit-tracker (DEBUG)` | `viit-tracker` |
| Launcher icon | amber tile + "D" badge | pale-blue tile |
| Signing | Android debug key | `androidApp/key.properties` keystore |

Both install side-by-side on the same device:

```bash
./gradlew :androidApp:assembleDebug     # local dev build
./gradlew :androidApp:assembleRelease   # local release build
```

## Releases (CI/CD)

`.github/workflows/android-release.yml` builds APKs automatically.

**Trigger a release:**

```bash
git tag v1.1.0
git push origin v1.1.0
```

This builds (with `ENABLE_ABI_SPLITS=true`, `VERSION_NAME=1.1.0`, `VERSION_CODE=<run number>`):

- `androidApp-universal-release.apk` — all ABIs, use this when unsure
- `androidApp-arm64-v8a-release.apk`, `androidApp-armeabi-v7a-release.apk`, `androidApp-x86_64-release.apk` — per-ABI splits

and publishes them on a **GitHub Release** for the tag. Release notes are
auto-generated from the commits — the same notes are shown as the in-app
changelog.

**Manual build (no release):** Actions → *Build & release APKs* → *Run workflow*
— uploads APKs as workflow artifacts (`1.0-dev.<run number>`).

**Release signing:** add repository secrets so CI signs with your keystore:

```bash
gh secret set KEYSTORE_BAS64 < <(base64 -w0 ~/JKSKEYS/viit-tracker-release.jks)
gh secret set KEYSTORE_PASSWORD --body '...'
gh secret set KEY_ALIAS --body '...'
gh secret set KEY_PASSWORD --body '...'
```

Without the secrets CI still builds, debug-signed (sideload testing only).

## In-app updates

The app checks `https://api.github.com/repos/naveenxd/viit-tracker/releases/latest`
**every time it opens**:

- **Checking…** → a small pill floats at the bottom of the screen
- **No update** → the pill shows `✓ Up to date` and fades away
- **Update available** → popup with version, changelog (release notes) and an
  *Update* button: downloads the universal APK, then hands it to the system
  installer (falls back to the release page in the browser if anything fails)
- **Network error** → silent, nothing is shown

So: publish a tag → users see the update popup on next app open.
