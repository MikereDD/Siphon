<p align="center">
  <img src="ic_launcher-web.png" alt="Siphon icon" width="128">
</p>

# Siphon

**Extract audio. Keep what matters.**

Siphon is an Android audio extractor for local video files and supported media links. It combines a focused Jetpack Compose interface with resilient background extraction, format and quality controls, metadata tagging, MediaStore export, and an updateable yt-dlp extractor.

**Current development build:** `0.3-dev.5` (`versionCode 7`)
**Stable release line:** `0.2.0`

## Features

- **Local video extraction** — browse searchable videos discovered through Android MediaStore.
- **Link extraction** — paste a supported URL or send one to Siphon through Android's Share menu.
- **Format control** — keep the original audio stream when possible, or convert to MP3, M4A/AAC, Opus, FLAC, or WAV.
- **Quality presets** — choose bitrate/quality settings for lossy output formats.
- **Metadata tagging** — title, artist, album, album artist, genre, year, track, and comment, with optional source metadata and link thumbnail artwork.
- **Foreground extraction** — WorkManager keeps long jobs visible and resilient while the app is backgrounded.
- **Safe cancellation** — active extraction can be cancelled without leaving a successful duplicate or a false failure state.
- **MediaStore export** — completed files are transactionally written to `Music/Siphon` and verified before private staging data is removed.
- **History and Library** — completed jobs remain visible in Siphon and exported audio can be opened directly.
- **Storage cleanup** — review legacy private output and abandoned staging files before deletion.
- **Extractor maintenance** — update yt-dlp stable or nightly without reinstalling Siphon.
- **Premium OLED interface** — violet identity, four-section bottom navigation, live extraction status, and dedicated utility screens.

## How extraction works

Siphon uses [`youtubedl-android`](https://github.com/yausername/youtubedl-android) with the `io.github.junkfood02` Maven coordinates. The dependency bundles yt-dlp, a Python runtime, FFmpeg, and optional aria2 support.

The two source types use different execution paths:

- **Links** are processed through yt-dlp. Siphon builds yt-dlp arguments for format, quality, metadata, cookies, extractor client, and post-processing.
- **Local videos** bypass yt-dlp and are passed directly to the bundled FFmpeg binary. Scoped-storage content is read through a live file descriptor instead of copying the complete source video into app cache.

Both paths stage output privately and use the same verified MediaStore export path.

## Project layout

```text
app/
  src/main/java/com/typezero/siphon/
    data/        MediaStore and data models
    di/          lightweight application container
    engine/      yt-dlp, FFmpeg, cookies, output resolution
    ui/          Compose UI and ViewModel
    work/        foreground WorkManager extraction
  src/test/      extraction argument unit tests

docs/
  UPDATER-PLAN.md

gradle/wrapper/
  gradle-wrapper.jar
  gradle-wrapper.properties
```

## Requirements

- Android 8.0+ (`minSdk 26`)
- `arm64-v8a` device for the current APK build configuration
- Android Studio with JDK 17+ support, or JDK 17+ for command-line builds

Current build stack:

- Android Gradle Plugin `8.7.2`
- Gradle `8.9`
- Kotlin `2.0.21`
- Jetpack Compose BOM `2024.10.01`
- `compileSdk 35`
- `targetSdk 35`
- Java/Kotlin target `17`

## Build

Siphon now uses the standard checked-in Gradle wrapper.

### Windows

```powershell
.\gradlew.bat assembleDebug
```

### Linux / macOS

```bash
./gradlew assembleDebug
```

Run unit tests with:

```powershell
.\gradlew.bat testDebugUnitTest
```

or:

```bash
./gradlew testDebugUnitTest
```

For a release build:

```powershell
.\gradlew.bat assembleRelease
```

Release signing and publishing are separate from compilation. Generated APK/AAB files do **not** belong in source history; distributable builds and checksums belong in Forgejo Releases.

### ABI note

The current Gradle configuration builds only `arm64-v8a` to keep the package manageable with the bundled Python and FFmpeg runtime. To support additional architectures, update the ABI split configuration in `app/build.gradle.kts` and verify the resulting package on each target architecture.

## Permissions

Siphon requests only permissions needed for its current features:

- `INTERNET` — link extraction and extractor updates.
- `ACCESS_NETWORK_STATE` — network-aware operations.
- `READ_MEDIA_VIDEO` — local video discovery on Android 13+.
- `READ_EXTERNAL_STORAGE` — local video access on Android 12L and older.
- `WRITE_EXTERNAL_STORAGE` — shared Music export on Android 8/9 only.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` — resilient long-running extraction.
- `POST_NOTIFICATIONS` — foreground extraction status where required by Android.

## Link extractor notes

Websites change frequently, so a bundled yt-dlp version can become stale even when Siphon itself is working correctly.

Use **Settings → Update extractor** to install the current stable yt-dlp data. The nightly option can provide newer site fixes at the cost of increased regression risk.

For sites that require authentication, Siphon can import a Netscape-format `cookies.txt`. Cookie data is stored in app-private storage and extraction logging is designed to redact private cookie paths and source URLs.

Some sites may still require mechanisms that cannot be satisfied on-device. Those failures are upstream/site limitations rather than local-video extraction failures.

## Application updater

The in-app **extractor updater** and the planned **Siphon application updater** are separate systems.

The application updater is intentionally not active yet. Its design requires package validation, SHA-256 verification, pinned signing-certificate verification, channel separation, safe private downloads, and Android's normal package-installer confirmation.

See [`docs/UPDATER-PLAN.md`](docs/UPDATER-PLAN.md).

## Repository policy

- Source code and documentation belong in Git.
- Build output, APKs, AABs, IDE state, local SDK paths, signing material, and caches do not.
- Release binaries belong in Forgejo Releases rather than the repository history.
- The checked-in Gradle wrapper is the canonical command-line build entry point.

## License

Siphon's original source code is made available under the **Apache License 2.0**. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).

Siphon also uses and distributes third-party components under their own licenses. In particular, the current dependency stack includes GPL-licensed components. The Apache-2.0 license for Siphon's original source does not replace or override those upstream terms. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) before redistributing binary builds.

## Responsible use

Only extract or convert media that you own or otherwise have permission or legal authority to use.
