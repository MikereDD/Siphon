# Security policy

## Supported versions

Siphon is still in active development. Security fixes are targeted at the current development line and the most recent supported release line rather than every historical build.

| Version line | Security support |
| --- | --- |
| Current development build | ✅ Active |
| Most recent stable line | ✅ Best effort |
| Older releases | ❌ Not supported |

## Reporting a vulnerability

Please do **not** publish exploitable security details, private URLs, authentication cookies, tokens, signing material, or other secrets in a public issue.

For a GitHub-hosted repository, use **GitHub Private Vulnerability Reporting** when it is enabled for the project. If private reporting is unavailable, open a minimal public issue stating only that you need a private channel for a security report; do not include exploit details in that issue.

A useful report should include:

- affected Siphon version/build;
- Android version and device class;
- reproducible steps;
- expected vs. actual behavior;
- security impact;
- whether the issue involves link extraction, local media, cookies, storage, package updating, or release verification;
- sanitized logs where useful.

## Security-sensitive areas

Extra care is expected around:

- imported cookies and authenticated extraction;
- source URL and filesystem-path logging;
- app-private staging files;
- MediaStore export transactions;
- foreground WorkManager execution;
- downloaded extractor data;
- future APK update metadata and downloads;
- package-name, version, checksum, and signing-certificate verification;
- FileProvider/package-installer handoff;
- release signing keys and CI secrets.

## Update security

The future Siphon application updater must follow the shared Typezer∅ updater standard and fail closed if release metadata, package identity, SHA-256, version rules, or signing-certificate verification cannot be satisfied. Android's package-installer confirmation must remain in the loop; Siphon will not silently install APK updates.
