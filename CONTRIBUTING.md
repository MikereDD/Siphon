# Contributing to Siphon

Thanks for taking the time to improve Siphon.

Siphon is intentionally focused: reliable audio extraction, Android-native storage behavior, careful background work, and a polished interface. Contributions should preserve those priorities rather than adding complexity without a clear user benefit.

## Before opening an issue

1. Confirm the problem occurs on the latest available Siphon build you can test.
2. If the problem involves a website, update the yt-dlp extractor from **Settings → Update extractor** and retry.
3. Distinguish link-extraction failures from local-video extraction failures.
4. Remove private URLs, cookies, tokens, usernames, and personal filesystem paths from logs before posting them publicly.
5. Search existing issues for the same problem.

## Development setup

Requirements:

- JDK 17+
- Android SDK compatible with `compileSdk 35`
- An `arm64-v8a` Android device for the current native-runtime configuration

Build with the checked-in wrapper:

```powershell
.\gradlew.bat assembleDebug
```

Run unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Linux/macOS equivalents use `./gradlew`.

## Pull requests

Keep pull requests focused and explain:

- the problem being solved;
- the approach taken;
- user-visible behavior changes;
- tests or real-device verification performed;
- any new permissions, dependencies, storage behavior, network behavior, or release implications.

Before submitting:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Also check:

```powershell
git diff --check
```

Do not commit APKs, AABs, build directories, IDE state, local SDK paths, signing material, cookies, authentication files, or secrets.

## Architecture expectations

Changes should preserve these safety properties:

- long-running extraction belongs in foreground WorkManager jobs rather than the Activity lifetime;
- local scoped-storage media should be streamed through file descriptors instead of copied wholesale when avoidable;
- final shared output should be written through MediaStore;
- temporary output should remain private until the export is complete and verified;
- cancellation must not be reported as success;
- sensitive URLs, cookie paths, tokens, and credentials should not leak into logs;
- update flows must fail closed when verification cannot be completed.

## Dependencies and licensing

New dependencies require attention to license compatibility and binary redistribution obligations. If a change modifies the bundled extraction/runtime stack, update [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) as needed.

## Versioning and releases

Do not bump the public version or prepare release artifacts casually inside unrelated pull requests. See [`docs/RELEASES.md`](docs/RELEASES.md) for the expected release process.
