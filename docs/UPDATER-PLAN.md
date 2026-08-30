# Siphon application updater architecture

Siphon has two intentionally separate update systems:

- **Extractor updater** — updates yt-dlp data used by the installed app.
- **Application updater** — downloads, verifies, and hands off a signed Siphon APK to Android's package installer.

The application updater is implemented and follows the shared **Typezer∅ Release Standards** rather than a Siphon-specific protocol.

## Current trust model

The updater pins local trust in `UpdateTrust.kt`:

- manifest schema revision: `2`;
- updater protocol version: `2`;
- application ID: `siphon`;
- package ID: `com.typezero.siphon`;
- approved HTTPS release hosts;
- detached release key ID;
- detached release public-key SHA-256 fingerprint;
- embedded release public key;
- canonical Siphon APK signing-certificate SHA-256.

Remote manifest values are metadata to validate against those local anchors. They do not redefine trust.

## Core rules

1. Fetch the manifest only from the configured approved HTTPS origin.
2. Keep **Stable** and **Development** channels distinct.
3. Use exact Typezer∅ version comparison.
4. Validate manifest schema and updater protocol compatibility.
5. Require the expected package ID `com.typezero.siphon`.
6. Validate exact asset/signature names and sizes.
7. Verify APK SHA-256.
8. Verify signature-file SHA-256.
9. Verify the detached release signature using the pinned release public key.
10. Verify the manifest release-key identity against locally pinned values.
11. Verify the APK signing certificate against the installed app and the locally pinned expected certificate.
12. Reject unexpected signer, package, version, channel, origin, or asset metadata.
13. Reject same-version reinstall and downgrade as normal updates.
14. Download into app-private staging using WorkManager.
15. Hand only a verified APK to Android through a narrowly scoped `FileProvider`.
16. Never silently install; Android package-installer confirmation remains mandatory.
17. Treat missing, malformed, or unverifiable required metadata as a hard failure.
18. Remove stale/abandoned update staging.
19. Keep updater logs free of secrets and unnecessary private data.

## Implemented structure

```text
update/
  AppUpdateService.kt        manifest lookup and update selection
  ApkVerifier.kt             package/version/signing-certificate verification
  AppUpdateWorker.kt         foreground download + validation into private staging
  UpdateInstaller.kt         Android package-installer/FileProvider handoff
  UpdatePreferences.kt       selected channel and update state
  UpdateModels.kt            release/update models
  ManifestParser.kt          strict manifest parsing
  SafeHttp.kt                approved-origin network enforcement
  ReleaseVerifier.kt         hashes, signature, key/trust validation
  TypezeroVersion.kt         canonical version comparison
  UpdateTrust.kt             locally pinned trust anchors
```

## Update flow

```text
Check
  ↓
Fetch manifest from approved HTTPS origin
  ↓
Validate schema + app/package identity + channel + protocol + version
  ↓
Resolve exact APK and detached-signature assets
  ↓
Download privately with WorkManager
  ↓
Verify names + sizes
  ↓
Verify APK SHA-256 + signature-file SHA-256
  ↓
Verify detached release signature with pinned key
  ↓
Verify APK package/version/canonical signing certificate
  ↓
Mark update verified
  ↓
Android package-installer confirmation
  ↓
Same-package/same-signing-identity upgrade
  ↓
Cleanup stale update staging
```

Any verification failure stops the flow before installer handoff.

## Release-side relationship

The updater consumes `release-manifest.json` produced by Siphon's Typezer∅-conforming release tooling.

A conforming Android release contains:

```text
Siphon-v<VERSION>.apk
Siphon-v<VERSION>.apk.sha256
Siphon-v<VERSION>.apk.sig
release-manifest.json
release-notes.md
release-validation.md
```

The live manifest is published only after release assets exist.

See [`RELEASES.md`](RELEASES.md) for publication discipline.

## Validation status

The signed Development update from `0.3-dev.6` to `0.3-dev.7` was tested successfully on a real device. The updater discovered the release, downloaded and verified it, handed it to Android, preserved application data, and reopened on `0.3-dev.7 (9)`.

The earlier [`UPDATER-IMPLEMENTATION-v0.3-dev.6.md`](UPDATER-IMPLEMENTATION-v0.3-dev.6.md) document is retained as a historical checkpoint from before the production trust anchors were established.
