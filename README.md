<p align="center">
  <img src="https://github.com/MikereDD/It-Works-On-My-Machine/raw/main/Android/Siphon/ic_launcher-web.png" alt="Siphon icon" width="120">
</p>

# Siphon

Extract audio from a video — from a **local file** or a **link** — in the format
and quality you want, with full metadata tagging. Android / Kotlin + Jetpack
Compose.

**Development version: 0.3-dev.5** · [Changelog](https://github.com/MikereDD/It-Works-On-My-Machine/blob/main/Android/Siphon/CHANGELOG.md)

The stable APK remains v0.2.0 while v0.3-dev.5 is under source testing.

## What it does

- **Local videos** — a searchable list of everything MediaStore knows about.
  Tap one, pick a format, tag it, extract.
- **Links** — paste a URL (or `Share → Siphon` from another app). The stream is
  pulled and the audio extracted into your chosen format.
- **Best quality by default** — the *Keep original* option copies the source
  audio stream with no re-encoding (zero generation loss). Switch to MP3 / M4A /
  Opus / FLAC / WAV with bitrate presets when you want a specific container.
- **Tagging for both sources** — title, artist, album, album artist, genre,
  year, track and comment are written into the output, plus optional
  source-metadata and (for links) embedded thumbnail cover art.
- Foreground WorkManager jobs survive Activity recreation, show notification progress, and cancel cleanly.
- Finished files are transactionally exported to **Music/Siphon** so any player picks them up. Private staging files are deleted after verified export.
- **Storage cleanup** detects files left in the old app-private output folder, shows their names and total size, and deletes them only after confirmation. Abandoned staging files older than 24 hours can be cleaned separately.
- **About Siphon** shows app/build information, yt-dlp and FFmpeg versions, safe diagnostics, source and component notices, and the status of the planned signed APK updater.
- **Premium interface** — OLED-dark visual system, violet Siphon identity, four-section bottom navigation, extraction dashboard, dedicated live-job view, filtered history, completed-file library, and full-screen storage/About tools.

## How it works

Both sources use [`youtubedl-android`](https://github.com/yausername/youtubedl-android)
(the maintained `io.github.junkfood02` fork), which bundles yt-dlp, Python and FFmpeg —
but they take different paths because yt-dlp only understands URLs:

- **Links** go through yt-dlp, which downloads and extracts the audio. Tags are
  injected into its FFmpeg pass via `--postprocessor-args "ExtractAudio:-metadata ..."`
  (see `engine/CommandFactory.kt`).
- **Local files** are handed straight to the bundled FFmpeg binary
  (`<nativeLibDir>/libffmpeg.so`) — extraction, transcode, bitrate and `-metadata`
  tagging in one pass (see `engine/FfmpegArgs.kt` / `engine/LocalFfmpegExtractor.kt`).

Both builders are pure and unit-tested, and present the same format / quality /
tagging options to the UI. This replaces the old ffmpeg-kit approach, retired in 2025.

## Build

Standard Android Studio project — open `Android/Siphon/` and run, or use the checked-in checksum-verifying Gradle bootstrap scripts:

```bash
./gradlew :app:assembleRelease
```

Build matrix: AGP 8.7.2 · Gradle 8.9 · Kotlin 2.0.21 · Compose BOM 2024.10.01 ·
compileSdk/targetSdk 35 · minSdk 26 · Java 17 · manual DI.

> **Note on size & ABIs.** The bundled Python + FFmpeg binaries are large, so the
> build targets a single ABI — **arm64-v8a** (modern phones, including Pixels) —
> and skips the universal APK, which was exhausting Gradle's heap during packaging.
> To build for other devices, add ABIs back to the `splits { abi { include(...) } }`
> block in `app/build.gradle.kts`; for a shareable multi-device build use an App
> Bundle: `./gradlew :app:bundleRelease`. `android:extractNativeLibs="true"` is
> required for the bundled binaries to run. Built APKs are committed to
> `releases/` as `Siphon-v<version>.apk` (tracked via Git LFS), which is where the
> download link above resolves.

## Keeping the link extractor working

YouTube changes constantly and the bundled yt-dlp goes stale, which shows up as
`ERROR: unable to download video data: HTTP Error 403` — the title resolves but
the media fetch is refused. Use **Settings → Update extractor** (or the nightly action when exposed) to pull the latest yt-dlp in place; the maintainers ship fixes almost daily.

If a specific video is still blocked, the Link screen has two escalating fallbacks:
switch the **player** (Default / TV / Web / iOS / Mobile), and if it asks you to
"sign in to confirm you're not a bot," **load a cookies.txt** exported from a
browser signed in to the site. Export it with a "Get cookies.txt" browser
extension — ideally from a throwaway account in a private window that you close
(not log out of) afterward, so the cookies don't get rotated/invalidated.

Some videos may still fail even then: current YouTube extraction can require PO
tokens that can't be generated on-device. Non-YouTube links and most YouTube ones
work; this is a yt-dlp/YouTube limitation, not a Siphon bug.

## Permissions

- `READ_MEDIA_VIDEO` (API 33+) / `READ_EXTERNAL_STORAGE` (≤32) — list local videos.
- `WRITE_EXTERNAL_STORAGE` (API 26–28 only) — export into the shared Music collection on legacy Android.
- `INTERNET` — link extraction and extractor updates.
- Foreground-service and notification permissions — keep long extraction jobs visible and resilient.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

`CommandFactoryTest` covers the yt-dlp (link) args — format/bitrate selection,
thumbnail embedding, and tag quoting/escaping. `FfmpegArgsTest` covers the local
FFmpeg args — codec/bitrate selection, VBR vs. CBR, unquoted `-metadata` tokens,
and copy-container mapping from the source codec.

## Usage note

Only extract audio from content you own or otherwise have the right to use.

---
Part of [It-Works-On-My-Machine](https://github.com/MikereDD/It-Works-On-My-Machine).


## Planned application updater

The current Settings and About actions update yt-dlp only. A separately secured APK updater is planned; see [`docs/UPDATER-PLAN.md`](docs/UPDATER-PLAN.md). It will require SHA-256 verification, package-name validation, pinned signing-certificate verification, and Android package-installer confirmation.
