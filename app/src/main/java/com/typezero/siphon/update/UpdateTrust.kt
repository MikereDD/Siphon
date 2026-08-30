package com.typezero.siphon.update

/**
 * Locally trusted updater configuration.
 *
 * IMPORTANT: the two cryptographic identity fields intentionally remain empty
 * until the real Typezer∅ release public key and canonical Siphon release APK
 * signing certificate are created and pinned. Remote manifest values can never
 * populate these trust anchors.
 */
object UpdateTrust {
    const val MANIFEST_SCHEMA_VERSION = 2
    const val UPDATER_PROTOCOL_VERSION = 2
    const val APP_ID = "siphon"
    const val PACKAGE_ID = "com.typezero.siphon"

    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/MikereDD/Siphon/main/release-manifest.json"

    val APPROVED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    const val RELEASE_KEY_ID = ""
    const val RELEASE_PUBLIC_KEY_SHA256 = ""
    const val RELEASE_PUBLIC_KEY_PEM = ""
    const val APK_SIGNING_CERT_SHA256 = ""

    val cryptographicAnchorsConfigured: Boolean
        get() = RELEASE_KEY_ID.isNotBlank() &&
            RELEASE_PUBLIC_KEY_SHA256.matches(Regex("[0-9a-fA-F]{64}")) &&
            RELEASE_PUBLIC_KEY_PEM.contains("BEGIN PUBLIC KEY") &&
            APK_SIGNING_CERT_SHA256.matches(Regex("[0-9a-fA-F]{64}"))
}
