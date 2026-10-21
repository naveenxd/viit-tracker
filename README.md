# viit-tracker

Attendance tracking app for VIIT students. Android-first Kotlin Multiplatform
app with a shared Compose UI.

[![Android release workflow](https://github.com/naveenxd/viit-tracker/actions/workflows/android-release.yml/badge.svg)](https://github.com/naveenxd/viit-tracker/actions/workflows/android-release.yml)

## Features

- Attendance dashboard with per-subject standings and a safe-bunk buffer
- Day-by-day register: mark holidays and bunks, page through months
- Attendance projections with an interactive mark sheet
- Timetable with day-to-day swiping
- Home-screen attendance widget
- Faculty profile and academic record view
- Light, dark, and AMOLED themes; 24-hour clock and motion options

## Built with

- Kotlin Multiplatform + Compose Multiplatform (Material 3)
- Ktor (OkHttp) + kotlinx.serialization
- AndroidX activity, lifecycle, security-crypto
- Gradle wrapper, AGP 9

## Requirements

- JDK 21
- Android SDK 36 (minSdk 24)
- Android Studio (latest stable recommended)

If `local.properties` is missing, create it:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Run

```bash
git clone https://github.com/naveenxd/viit-tracker.git
cd viit-tracker

./gradlew :androidApp:assembleGithubDebug
adb install -r androidApp/build/outputs/apk/github/debug/androidApp-github-debug.apk
```

Or open the project in Android Studio and run the `githubDebug` variant.

The debug package is `com.vignan.tracker.debug`, so it installs side-by-side
with the release app.

## Builds

There are two flavors:

| Flavor | What it is |
|---|---|
| `github` | Sideload APK. Updates itself in-app from `version.json`. |
| `play` | Play Store AAB. Updates through Google Play. |

Debug builds:

```bash
./gradlew :androidApp:assembleGithubDebug
./gradlew :androidApp:assemblePlayDebug
```

GitHub release APKs:

```bash
VERSION_CODE=4 VERSION_NAME=1.1.0 ENABLE_ABI_SPLITS=true \
  ./gradlew :androidApp:assembleGithubRelease
```

Play Store AAB:

```bash
VERSION_CODE=4 VERSION_NAME=1.1.0 ./gradlew :androidApp:bundlePlayRelease
```

AAB output:

```text
androidApp/build/outputs/bundle/playRelease/androidApp-play-release.aab
```

An AAB cannot be installed directly. Upload it to Play Console; use Play
internal testing to verify it.

## Releases

On a `v*` tag, CI:

1. Builds universal + per-ABI GitHub APKs and publishes them to GitHub Releases.
2. Refreshes `version.json` on `main`, so sideloaded apps can update in-app.
3. Builds the Play AAB as workflow artifact `aab-<run_number>` for upload to
   Play Console.

Manual workflow runs produce the same artifacts without creating a release.

```bash
git tag v1.1.0
git push origin v1.1.0
```

Keep `VERSION_CODE` strictly increasing and never reuse a code for Play.

## Project layout

```text
androidApp/   Android app, flavors, widget, update hooks
shared/       shared UI, domain logic, platform code
tool/release/ version.json generation
changelog/    per-release in-app update notes
version.json  in-app updater manifest (GitHub flavor)
```

## License

No `LICENSE` file is present yet. Add one before advertising the repository as
open source or accepting outside contributions.
