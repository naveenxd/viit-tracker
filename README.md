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

`.github/workflows/android-release.yml` builds and publishes a release.

**Trigger a release:**

```bash
# optional: per-release bullets for the in-app update sheet
printf '* Faster dashboard loads\n* Fixed stale projections\n' > changelog/1.1.0.md
git add changelog/1.1.0.md
git commit -m "changelog: 1.1.0"

git tag v1.1.0
git push origin v1.1.0
```

This builds (with `ENABLE_ABI_SPLITS=true`, `VERSION_NAME=1.1.0`, `VERSION_CODE=<run number>`):

- `viit-tracker-1.1.0.apk` — universal, use this when unsure
- `viit-tracker-1.1.0-arm64-v8a.apk`, `…-armeabi-v7a.apk`, `…-x86_64.apk` — per-ABI splits

then:

1. publishes the APKs on a **GitHub Release** for the tag (auto-generated notes),
2. regenerates **`version.json`** on `main` — the updater manifest with each
   APK's URL, real versionCode and SHA-256 — via
   `tool/release/generate_version_json.py`.

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

Architecture (mirrors `naveenxd/mirai` + `mirai-releases`, minus the second
repo — this repo is public, so `version.json` lives here):

- **Manifest:** `version.json` on `main`, fetched **every app open** from
  `raw.githubusercontent.com` (CDN — no `api.github.com` calls, no rate
  limits), with a cache-busting query and one retry on failure.
- **Comparison:** each APK's *actual* versionCode is read at build time and
  published per ABI (`apk_builds`), so the app compares its own code against
  the code of the APK built for its ABI — no offset math, exact per flavor.
- **Download:** the device's primary ABI picks the matching split APK from
  `apk_urls` (falls back to the universal `apk_url`), verified against the
  manifest's SHA-256 before the system installer ever sees the file.
- **UI:** floating toast at the bottom (`Checking…` / `✓ Up to date` /
  `! Couldn't check`) and a bottom sheet with version badge, "what's new"
  bullets, download progress + cancel, checksum verify and install handoff.
  `force_update: true` makes the sheet non-dismissable.
- **Failure:** a bad/missing manifest only flashes the toast — the app never
  blocks or crashes over an update check.

So: publish a tag → CI updates `version.json` → users see the update sheet on
next app open.
