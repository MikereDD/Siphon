package com.typezero.siphon.update

import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * Isolated vNext schema-3 signed-manifest verifier (not yet connected to the live updater).
 *
 * A caller must supply independently pinned release identities, and must persist an accepted
 * sequence/digest atomically before acting on update metadata. Never populate trust from JSON.
 */
internal object VNextManifestSecurity {
    const val PROFILE = "typezero-ecdsa-p384-sha384-v1"
    const val SCHEMA = 3
    const val PROTOCOL = 3
    private const val MAX_MANIFEST_BYTES = 1024 * 1024
    private const val MAX_SIG_BYTES = 144
    private const val MAX_FUTURE_SKEW_SECONDS = 300L

    enum class Lifecycle { PENDING, ACTIVE, RETIRING, RETIRED, REVOKED }

    data class PinnedKey(
        val keyId: String,
        val spkiSha256: String,
        val spkiDer: ByteArray,
        val lifecycle: Lifecycle
    )

    data class AcceptedState(val sequence: Long, val manifestSha256: String)

    data class Verified(
        val manifest: JSONObject,
        val state: AcceptedState,
        val keyId: String
    )

    /**
     * The bytes must be the exact HTTPS response body; do not reserialize JSON before verification.
     * The acceptedState is locally persisted trusted state, never read from the remote manifest.
     */
    fun verify(
        manifestBytes: ByteArray,
        detachedSignature: ByteArray,
        pinnedKeys: Collection<PinnedKey>,
        expectedAppId: String,
        expectedChannel: String,
        now: Instant,
        acceptedState: AcceptedState? = null
    ): Verified {
        require(manifestBytes.isNotEmpty() && manifestBytes.size <= MAX_MANIFEST_BYTES) {
            "Invalid manifest byte length"
        }
        require(detachedSignature.isNotEmpty() && detachedSignature.size <= MAX_SIG_BYTES) {
            "Invalid detached signature length"
        }
        val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
        require(manifest.getInt("schemaVersion") == SCHEMA) { "Unsupported vNext schema" }
        require(manifest.getString("appId") == expectedAppId) { "Manifest app identity mismatch" }
        require(manifest.getString("channel") == expectedChannel) { "Manifest channel mismatch" }
        require(manifest.getInt("minimumUpdaterProtocol") <= PROTOCOL) { "Updater protocol too old" }
        val signing = manifest.getJSONObject("releaseSigning")
        require(signing.getString("profile") == PROFILE) { "Unsupported vNext signing profile" }
        val keyId = signing.getString("keyId")
        val expectedFingerprint = signing.getString("spkiSha256")
        val key = pinnedKeys.singleOrNull { it.keyId == keyId }
            ?: error("Unapproved release signing identity")
        require(key.lifecycle == Lifecycle.ACTIVE || key.lifecycle == Lifecycle.RETIRING) {
            "Release signing identity is not authorized"
        }
        require(key.spkiSha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid locally pinned fingerprint" }
        require(expectedFingerprint == key.spkiSha256) { "Release public key identity mismatch" }
        require(sha256(key.spkiDer) == key.spkiSha256) { "Pinned SPKI fingerprint mismatch" }

        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(key.spkiDer))
        require(publicKey is ECPublicKey && publicKey.params.curve.field.fieldSize == 384) {
            "Release key is not P-384"
        }
        val verifier = Signature.getInstance("SHA384withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(manifestBytes)
        require(verifier.verify(detachedSignature)) { "Signed manifest verification failed" }

        // Only authenticated fields are interpreted as security policy from here forward.
        val sequence = manifest.getLong("manifestSequence")
        require(sequence > 0) { "Invalid manifest sequence" }
        val published = Instant.parse(manifest.getString("publishedAt"))
        val expires = Instant.parse(manifest.getString("expiresAt"))
        require(expires.isAfter(published)) { "Invalid manifest freshness window" }
        require(!published.isAfter(now.plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
            "Manifest published in the future"
        }
        require(now.isBefore(expires)) { "Manifest has expired" }
        val digest = sha256(manifestBytes)
        if (acceptedState != null) {
            require(sequence >= acceptedState.sequence) { "Manifest replay rejected" }
            require(sequence != acceptedState.sequence || digest == acceptedState.manifestSha256) {
                "Manifest sequence collision rejected"
            }
        }
        return Verified(manifest, AcceptedState(sequence, digest), keyId)
    }

    fun pinnedKeyFromPem(keyId: String, pem: String, fingerprint: String, lifecycle: Lifecycle): PinnedKey {
        val body = pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s+"), "")
        return PinnedKey(keyId, fingerprint, Base64.getDecoder().decode(body), lifecycle)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
