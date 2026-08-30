# Siphon updater implementation checkpoint — v0.3-dev.6

This checkpoint implements the application-updater foundation against the
Typezer∅ Android updater standard.

## Implemented

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
- package ID, Android versionCode, installed signer, manifest signer, and pinned
  Siphon signer checks.
- FileProvider handoff to Android's package installer.
- unknown-sources permission handoff on Android 8+.
- stale update-file cleanup.

## Deliberate security gate

`UpdateTrust.kt` contains empty trust-anchor constants in this checkpoint.
That is intentional. The updater may check public manifest availability, but it
must not download or install a release until both of these real identities are
created and pinned:

1. the Typezer∅ detached-release public key/key ID/fingerprint;
2. the canonical Siphon release APK signing-certificate SHA-256.

Do not replace this with trust data taken only from a remote manifest.

## Morning test

1. Build/install v0.3-dev.6.
2. Open Settings → About Siphon and confirm it opens/closes without crashing.
3. Re-test local and URL extraction.
4. Confirm extraction never shows whole-job 100% before final completion.
5. Trigger a known bad/stale URL case and confirm the 403 guidance is useful.
6. Paste a YouTube channel URL and confirm Siphon explains that collection
   extraction is not enabled yet.
7. Open Settings → Application updates.
8. Confirm Development is selected automatically on the dev build.
9. Tap Check now. Until `release-manifest.json` is published, the expected
   result is a clean "not published yet" message rather than a crash.
10. Confirm Download & verify cannot bypass the missing local trust anchors.

The next updater checkpoint is release-key/APK-signing identity establishment,
manifest publication, and a real pre-release upgrade test.
