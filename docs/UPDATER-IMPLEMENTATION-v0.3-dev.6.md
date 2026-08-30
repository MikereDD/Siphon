# Siphon updater implementation checkpoint — v0.3-dev.6

> [!NOTE]
> **Historical checkpoint.** This document records the state of the updater foundation at the original `v0.3-dev.6` checkpoint. The empty-trust-anchor gate described below was later superseded when Siphon's real release key and APK signing identity were established and pinned. The current updater architecture is documented in [`UPDATER-PLAN.md`](UPDATER-PLAN.md).

This checkpoint implemented the application-updater foundation against the Typezer∅ Android updater standard.

## Implemented at this checkpoint

- Stable / Development channel preference.
- Typezer∅ numeric version parser/comparator.
- Schema revision 2 identity checks.
- updater protocol compatibility gate.
- approved HTTPS-origin enforcement including redirect validation.
- private WorkManager download staging.
- exact asset and detached-signature names/sizes.
- APK SHA-256 and signature-file SHA-256 verification.
- pinned release-key identity checks.
- RSA/SHA-256 detached release-signature verification.
- package ID, Android versionCode, installed signer, manifest signer, and pinned Siphon signer checks.
- FileProvider handoff to Android's package installer.
- unknown-sources permission handoff on Android 8+.
- stale update-file cleanup.

## Security gate at the original checkpoint

At the moment this checkpoint was created, `UpdateTrust.kt` intentionally contained empty production trust anchors.

The updater foundation was designed to fail closed until both real identities were created and pinned:

1. the Typezer∅ detached-release public key/key ID/fingerprint;
2. the canonical Siphon release APK signing-certificate SHA-256.

The rule remains important: trust data must not be accepted only because a remote manifest supplies it.

## What superseded this checkpoint

After `v0.3-dev.6`:

- Siphon's production detached release RSA keypair was established.
- The release public key, key ID, and canonical public-key fingerprint were pinned locally.
- The canonical Android APK signing-certificate SHA-256 was pinned locally.
- `release-manifest.json` schema revision 2 was published.
- The Development channel performed a real signed `0.3-dev.6` → `0.3-dev.7` update.
- APK/package/signing continuity passed Android's normal installer path.
- Application data was preserved.
- The updater reopened successfully at `0.3-dev.7 (9)`.
- The repository gained a fail-closed single-command release publisher aligned with the Typezer∅ Android Release Publishing Standard.
- The publisher was hardened to preflight first, then bind the exact release-source commit, then produce the canonical clean distributable build from that commit.

Therefore, the empty-anchor and "not published yet" expectations below are historical only.

## Original morning-test plan

The original checkpoint planned to verify:

1. Build/install v0.3-dev.6.
2. Open Settings → About Siphon and confirm it opens/closes without crashing.
3. Re-test local and URL extraction.
4. Confirm extraction never shows whole-job 100% before final completion.
5. Trigger a known bad/stale URL case and confirm the 403 guidance is useful.
6. Paste a YouTube channel URL and confirm Siphon explains that collection extraction is not enabled yet.
7. Open Settings → Application updates.
8. Confirm Development is selected automatically on the dev build.
9. Check the not-yet-published manifest behavior.
10. Confirm Download & verify cannot bypass missing local trust anchors.

Those publication/trust-anchor expectations were superseded by the successful signed Development release path described above.
