# Third-party notices

Siphon's original source code is licensed under the Apache License 2.0. The
Android application also depends on and, in binary builds, may bundle software
distributed under other licenses.

This document records the direct components identifiable from the current
source tree. It is a project-maintenance summary, not a substitute for the full
license texts and notices shipped by upstream artifacts.

## Direct extraction/runtime components

| Component | Role in Siphon | Upstream license / note |
| --- | --- | --- |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | Android wrapper and bundled extraction runtime | GPL-3.0 |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Media-site extraction | Core project is Unlicense; some distributed forms include additional third-party licenses |
| [FFmpeg](https://ffmpeg.org/) | Local extraction, transcoding, metadata, post-processing | LGPL-2.1-or-later by default; GPL-2.0-or-later applies when GPL components are enabled. The exact bundled build must be verified before redistribution |
| [aria2](https://github.com/aria2/aria2) | Optional external downloader bundled by the current dependency | GPL-2.0, with additional upstream license/exception files for some dependencies |
| Python runtime | Runtime used by the bundled yt-dlp environment | Python Software Foundation License; exact runtime/version follows the youtubedl-android artifact |

## Android application stack

AndroidX, Jetpack Compose, WorkManager, Kotlin, and related Google/JetBrains
components used directly by Siphon are distributed under their respective
upstream terms, commonly Apache License 2.0 for the libraries used here.

## Distribution note

The root `LICENSE` applies to Siphon's original source code. It does **not**
relicense third-party libraries or native binaries.

Because the current application links to and bundles GPL-licensed components,
a distributed APK can have obligations beyond Apache-2.0. Before a public binary
release, preserve the complete upstream license texts/notices for the exact
artifact versions and verify the corresponding source-offer/source-availability
requirements.

For the current source tree, the direct Maven dependency version is:

```text
io.github.junkfood02.youtubedl-android:library:0.18.1
io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1
io.github.junkfood02.youtubedl-android:aria2c:0.18.1
```

Transitive native dependencies may add further notices. Generate or inspect the
resolved dependency/license inventory before publishing a production APK.
