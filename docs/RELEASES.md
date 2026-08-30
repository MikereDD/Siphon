# Siphon release guide

This document defines the repository-side release discipline for Siphon. Siphon follows the shared **Typezer∅ Release Standards**, including the Android updater, signing, validation, manifest, and Android release-publishing requirements.

## Release principles

1. **Source and binaries stay separate.** APK/AAB artifacts belong in a release, not Git history.
2. **The checked-in Gradle wrapper is canonical.**
3. **Stable and Development are distinct channels.** A development build must never be mistaken for a stable upgrade.
4. **Versions compare exactly** under the Typezer∅ comparator.
5. **Every distributable payload is hashed and signed.** SHA-256 is required for transfer integrity; detached signing authenticates the release.
6. **Android package signing is an independent trust boundary.** The APK certificate must match the locally pinned Siphon identity.
7. **Trust is pinned locally.** A remote manifest cannot redefine the release key, APK certificate, package ID, or approved release origins.
8. **Source, tag, manifest, and release assets correspond exactly.**
9. **Never silently downgrade or switch channels.**
10. **Verification fails closed.**
11. **The live updater manifest is published only after its release assets exist.**
12. **A release is not complete until remote publication and updater-facing metadata are verified.**

## Canonical publication workflow

The preferred release entry point is:

```powershell
.\tools\Publish-SiphonRelease.ps1 -Version <version> -VersionCode <code>
```

The publisher performs the equivalent of:

```text
clean/synchronized main
→ intended version metadata
→ tests + preflight debug build
→ verify only expected source changes
→ commit exact release source
→ bind release build commit
→ clean canonical release build from bound commit
→ verify APK certificate
→ verify detached release-key fingerprint
→ generate APK checksum
→ create + verify detached signature
→ generate release notes
→ generate release validation record
→ generate + validate release manifest
→ explicit [y/N] publication gate
→ annotated tag at exact build commit
→ push source/tag
→ GitHub release with exact canonical assets
→ publish live updater manifest
→ verify published assets/digests
→ verify live manifest
```

Stable publication is blocked by default and requires deliberate `-AllowStable` approval.

### Dry run

Use:

```powershell
.\tools\Publish-SiphonRelease.ps1 `
    -Version <current-version> `
    -VersionCode <current-code> `
    -DryRun
```

DryRun never creates/moves a tag, pushes commits/tags, creates/modifies a GitHub release, or replaces the live manifest. DryRun artifacts are isolated under:

```text
$HOME\Downloads\Siphon-v<version>-dryrun\
```

Canonical release artifacts use:

```text
$HOME\Downloads\Siphon-v<version>-release\
```

## Canonical Android release contents

Every Siphon Android publication must contain exactly the required Typezer∅ release set:

```text
Siphon-v<VERSION>.apk
Siphon-v<VERSION>.apk.sha256
Siphon-v<VERSION>.apk.sig
release-manifest.json
release-notes.md
release-validation.md
```

## Trust and signing

Siphon uses two independent signing layers:

1. **Detached Typezer∅ release signature** — verified with the locally pinned Siphon release public key.
2. **Android APK signing certificate** — verified against the installed application and the locally pinned canonical certificate identity.

Both must pass. SHA-256 alone is not publisher authentication.

The updater also pins approved HTTPS hosts and the expected package ID `com.typezero.siphon`. Manifest-provided trust metadata is checked against these local anchors rather than trusted by itself.

## Release checklist

- [ ] Version name and version code are intentional.
- [ ] Development/Stable channel is intentional.
- [ ] Stable releases have explicit Stable approval.
- [ ] Working tree starts clean and required remotes are synchronized for publication.
- [ ] Unit tests pass.
- [ ] Preflight debug build succeeds.
- [ ] Exact intended release source is committed.
- [ ] Canonical clean release build is produced from that bound commit.
- [ ] Final APK is signed with the expected Siphon certificate.
- [ ] APK package name is `com.typezero.siphon`.
- [ ] APK version matches the release metadata.
- [ ] APK SHA-256 is generated from the exact canonical APK.
- [ ] Detached release public-key fingerprint matches the pinned identity.
- [ ] Detached signature verifies successfully.
- [ ] Manifest source commit and tag identify the canonical build commit.
- [ ] All six canonical assets are generated.
- [ ] Third-party notices still match bundled runtime/dependencies.
- [ ] No APK/AAB, private key, keystore, credential, cookie, or local build configuration is committed.
- [ ] Explicit publication confirmation is obtained.
- [ ] Release tag resolves to the canonical build commit.
- [ ] Published release asset names, sizes, and SHA-256 digests match local canonical artifacts.
- [ ] Live updater manifest is published only after release assets exist.
- [ ] Live updater manifest is fetched back from its real updater-facing origin and verified.

## Current validated path

The `0.3-dev.6` → `0.3-dev.7` Development update was validated end to end:

- signed APK continuity passed;
- pinned detached release signature verification passed;
- Android package-installer handoff passed;
- Android accepted the same-package/same-signing-identity update;
- application data was preserved;
- the app reopened at `0.3-dev.7 (9)`.

The single-command publisher was subsequently hardened to the Typezer∅ canonical provenance order: preflight validation first, then commit/bind the exact release source, then the clean canonical distributable build from that commit.

## GitHub and Forgejo

Source is mirrored to Forgejo and GitHub. The current updater manifest and release assets are served from approved GitHub origins.

Hosting is transport. The manifest contract, pinned trust, version/channel policy, source/tag correspondence, signing identities, and validation requirements remain authoritative regardless of mirror.
