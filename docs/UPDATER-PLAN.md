# Siphon application updater plan

The extractor updater and the application updater are separate systems:

- **Extractor updater** updates yt-dlp data used by the installed app.
- **Application updater** downloads and installs a newly signed Siphon APK.

## Security requirements

1. Fetch release metadata only over HTTPS from the canonical release source.
2. Match the expected package name: `com.typezero.siphon`.
3. Verify the downloaded APK SHA-256 against release metadata.
4. Verify the APK signing certificate matches the certificate pinned in Siphon.
5. Reject downgrades unless a developer-only override is explicitly enabled.
6. Download into app-private storage with WorkManager and a foreground notification.
7. Install through Android's package installer using a narrowly scoped FileProvider URI.
8. Never silently install; Android's confirmation UI remains mandatory.
9. Support stable and development channels without mixing their release selection rules.
10. Keep updater logs free of private URLs, cookies, tokens, and filesystem details.

## Proposed structure

```text
update/
  AppUpdateService.kt        release lookup and version selection
  ApkVerifier.kt             SHA-256, package name, version, signer checks
  AppUpdateWorker.kt         resilient foreground download
  UpdatePreferences.kt       channel and last-check state
  UpdateModels.kt            release metadata and UI state
```

The existing extraction WorkManager implementation establishes lifecycle, notification,
cancellation, and private-download patterns that the application updater can reuse.
