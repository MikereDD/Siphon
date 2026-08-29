# Siphon release guide

This document defines the repository-side release discipline for Siphon. The future in-app application updater must consume release information produced by this process and must follow the shared Typezer∅ release/updater standard derived from the CouchLink implementation.

## Release principles

1. **Source and binaries stay separate.** APK/AAB artifacts belong in a release, not Git history.
2. **The checked-in Gradle wrapper is canonical.** Release verification starts from the repository wrapper.
3. **Stable and Development are distinct channels.** A development build must never be mistaken for a stable upgrade.
4. **Versions compare exactly.** Pre-release identifiers must not be flattened into naive string or numeric comparisons.
5. **Every distributable artifact gets SHA-256.** The checksum published in release metadata must match the uploaded asset exactly.
6. **Signing identity is part of release identity.** Future updater verification must reject an APK signed by an unexpected certificate even if its filename/version/checksum fields look plausible.
7. **Never silently downgrade.** Downgrades require an explicit developer-only override, not normal update behavior.
8. **Release notes matter.** Users should be able to see what changed before installing.
9. **Verification fails closed.** Missing or malformed required metadata is an update failure, not permission to continue.
10. **Rollback metadata is planned, not improvised.** Where rollback is supported, metadata must identify the intended previous release explicitly.

## Pre-release verification

From a clean working tree:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Also run:

```powershell
git diff --check
git status --short
```

The release should not proceed if tests/builds fail or if unintended working-tree changes remain.

## Release checklist

- [ ] Version name and version code are intentional.
- [ ] `CHANGELOG.md` contains the release notes.
- [ ] README version/status text is current.
- [ ] Unit tests pass.
- [ ] Debug build succeeds.
- [ ] Release build succeeds.
- [ ] Final APK is signed with the expected release certificate.
- [ ] APK package name is `com.typezero.siphon`.
- [ ] APK version matches the release metadata.
- [ ] SHA-256 is generated from the exact uploaded APK.
- [ ] Third-party notices still match the bundled runtime/dependencies.
- [ ] No APK/AAB, keystore, credentials, cookies, or local build configuration is committed.
- [ ] Stable/Development channel is explicit.
- [ ] Release notes and publish date are present.
- [ ] Release tag matches the intended version.

## Planned updater metadata

When the application updater is implemented, release metadata should use the common Typezer∅ concepts rather than a Siphon-only schema. At minimum:

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

The exact signed manifest format should be finalized alongside the application updater rather than guessed ahead of implementation.

## GitHub and Forgejo

Siphon may be mirrored or released through both hosting platforms, but release semantics should remain identical. Hosting location is transport; the manifest, verification rules, version/channel policy, checksums, and signing identity are the contract.
