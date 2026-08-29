# Siphon application updater plan

Siphon has two intentionally separate update systems:

- **Extractor updater** — updates yt-dlp data used by the installed app.
- **Application updater** — downloads and hands off a newly signed Siphon APK to Android's package installer.

The application updater is not implemented yet. When it is, it must follow the shared **Typezer∅ release/updater rule set derived from the CouchLink updater work** rather than inventing a Siphon-specific release protocol.

## Core rules

1. Fetch release metadata only over HTTPS from the canonical configured release source.
2. Keep **Stable** and **Development** channels distinct.
3. Use exact version/pre-release comparison; do not rely on naive string ordering.
4. Require the expected package name: `com.typezero.siphon`.
5. Verify the downloaded APK SHA-256 against release metadata.
6. Verify the APK signing certificate matches the certificate pinned by Siphon.
7. Reject unexpected signer, package, version, channel, or asset metadata.
8. Reject downgrades unless an explicit developer-only override exists.
9. Download into app-private storage using WorkManager and an appropriate foreground notification.
10. Hand the verified APK to Android through a narrowly scoped `FileProvider` URI.
11. Never silently install; Android's package-installer confirmation remains mandatory.
12. Treat missing, malformed, or unverifiable required metadata as a hard failure.
13. Keep updater logs free of private URLs, cookies, tokens, credentials, and unnecessary filesystem details.
14. Preserve enough metadata to support intentional rollback rules without silently downgrading users.
15. Surface release notes, publish date, channel, and mandatory/optional status before installation when applicable.

## Shared manifest concepts

The updater should consume the same ecosystem-wide concepts used by other Typezer∅ applications:

```text
appId
platform
version
versionCode
channel
publishedAt
releaseNotes
minimumSupportedVersion
download asset URL
asset SHA-256
mandatory/optional flag
rollback metadata
release-notes URL
signature/certificate metadata
```

The final manifest serialization/signing design should be implemented with the updater, not improvised in advance.

## Proposed Android structure

```text
update/
  AppUpdateService.kt        release lookup, manifest validation, version/channel selection
  ApkVerifier.kt             SHA-256, package name, version, signer checks
  AppUpdateWorker.kt         resilient foreground download into private staging
  UpdatePreferences.kt       selected channel and last-check state
  UpdateModels.kt            release metadata and UI state
```

The existing extraction WorkManager implementation provides useful lifecycle, notification, cancellation, and private-staging patterns, but updater verification must remain its own security boundary.

## Expected flow

```text
Check
  ↓
Fetch manifest
  ↓
Validate manifest + channel + version
  ↓
Download APK privately
  ↓
Verify SHA-256
  ↓
Verify package/version/signing certificate
  ↓
Show release/install decision
  ↓
Android package-installer confirmation
```

Any verification failure stops the flow before installation.

See [`RELEASES.md`](RELEASES.md) for the repository-side release discipline that feeds this updater.
