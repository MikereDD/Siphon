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
 * Phase 3B: offline, explicitly invoked recovery statement verifier.
 * Not a release-manifest parser, network client, or automatic trust installer.
 *
 * Candidate statement contract (schema 1):
 * schemaVersion, appId, trustDomain="release-signing", trustSequence, publishedAt,
 * authority={role:"recovery",keyId,spkiSha256},
 * releaseKeys=[{keyId,profile,spkiSha256,spkiDerBase64,lifecycleState}].
 * The detached signature is DER ECDSA P-384/SHA-384 over exact statement bytes.
 * This local contract remains experimental until adoption tests complete.
 */
internal object VNextRecoveryAuthority {
    private const val MAX_STATEMENT_BYTES = 128 * 1024
    private const val MAX_SIGNATURE_BYTES = 144
    private const val MAX_KEYS = 16

    data class RecoveryAnchor(
        val keyId: String,
        val spkiSha256: String,
        val spkiDer: ByteArray
    )

    data class TrustedState(
        val sequence: Long,
        val releaseKeys: List<VNextManifestSecurity.PinnedKey>
    )

    /** Construct with an independently installed recovery key, never one from the statement. */
    fun verifyAndPropose(
        statementBytes: ByteArray,
        signatureBytes: ByteArray,
        installedRecovery: RecoveryAnchor,
        current: TrustedState,
        expectedAppId: String,
        now: Instant
    ): TrustedState {
        require(statementBytes.isNotEmpty() && statementBytes.size <= MAX_STATEMENT_BYTES) { "Recovery statement size invalid" }
        require(signatureBytes.isNotEmpty() && signatureBytes.size <= MAX_SIGNATURE_BYTES) { "Recovery signature size invalid" }
        require(expectedAppId.isNotBlank()) { "Expected app ID is missing" }
        require(current.sequence >= 0) { "Corrupt persisted trust sequence" }
        require(installedRecovery.keyId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "Invalid recovery ID" }
        require(installedRecovery.spkiSha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid recovery fingerprint" }
        require(sha256(installedRecovery.spkiDer) == installedRecovery.spkiSha256) { "Recovery key fingerprint mismatch" }
        require(current.releaseKeys.none { it.keyId == installedRecovery.keyId || it.spkiSha256 == installedRecovery.spkiSha256 }) {
            "Recovery key cannot be a routine release key"
        }
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(installedRecovery.spkiDer))
        require(publicKey is ECPublicKey && publicKey.params.curve.field.fieldSize == 384) { "Recovery key must be P-384" }
        val verifier = Signature.getInstance("SHA384withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(statementBytes)
        require(verifier.verify(signatureBytes)) { "Recovery authorization signature failed" }

        val statement = JSONObject(statementBytes.toString(Charsets.UTF_8))
        requireExactKeys(statement, setOf("schemaVersion", "appId", "trustDomain", "trustSequence", "publishedAt", "authority", "releaseKeys"))
        require(statement.getInt("schemaVersion") == 1) { "Unsupported recovery schema" }
        require(statement.getString("appId") == expectedAppId) { "Recovery app identity mismatch" }
        require(statement.getString("trustDomain") == "release-signing") { "Recovery trust domain mismatch" }
        val sequence = statement.getLong("trustSequence")
        require(sequence > current.sequence) { "Trust sequence replay/collision rejected" }
        require(!Instant.parse(statement.getString("publishedAt")).isAfter(now.plusSeconds(300))) {
            "Recovery statement published in future"
        }
        val authority = statement.getJSONObject("authority")
        requireExactKeys(authority, setOf("role", "keyId", "spkiSha256"))
        require(authority.getString("role") == "recovery") { "Wrong signing role" }
        require(authority.getString("keyId") == installedRecovery.keyId &&
            authority.getString("spkiSha256") == installedRecovery.spkiSha256) { "Recovery authority mismatch" }

        val entries = statement.getJSONArray("releaseKeys")
        require(entries.length() in 1..MAX_KEYS) { "Invalid release key count" }
        val proposed = (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            requireExactKeys(entry, setOf("keyId", "profile", "spkiSha256", "spkiDerBase64", "lifecycleState"))
            require(entry.getString("profile") == VNextManifestSecurity.PROFILE) { "Unsupported release key profile" }
            val state = when (entry.getString("lifecycleState")) {
                "pending" -> VNextManifestSecurity.Lifecycle.PENDING
                "active" -> VNextManifestSecurity.Lifecycle.ACTIVE
                "retiring" -> VNextManifestSecurity.Lifecycle.RETIRING
                "retired" -> VNextManifestSecurity.Lifecycle.RETIRED
                "revoked" -> VNextManifestSecurity.Lifecycle.REVOKED
                else -> error("Invalid release lifecycle state")
            }
            val key = VNextManifestSecurity.PinnedKey(
                entry.getString("keyId"), entry.getString("spkiSha256"),
                Base64.getDecoder().decode(entry.getString("spkiDerBase64")), state
            )
            require(key.keyId != installedRecovery.keyId && key.spkiSha256 != installedRecovery.spkiSha256) {
                "Recovery identity cannot become routine release identity"
            }
            require(sha256(key.spkiDer) == key.spkiSha256) { "Release key SPKI digest mismatch" }
            val parsed = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(key.spkiDer))
            require(parsed is ECPublicKey && parsed.params.curve.field.fieldSize == 384) { "Release key must be P-384" }
            key
        }
        // Reject duplicate IDs/fingerprints and malformed identity metadata.
        VNextTrustPolicy.fromInstalledAnchors(proposed)
        VNextTrustPolicy.fromInstalledAnchors(current.releaseKeys.ifEmpty { proposed })
        val oldById = current.releaseKeys.associateBy { it.keyId }
        val newById = proposed.associateBy { it.keyId }
        require(oldById.keys.all { it in newById }) { "Recovery cannot erase an installed key history" }
        current.releaseKeys.forEach { old ->
            val new = newById.getValue(old.keyId)
            require(old.spkiSha256 == new.spkiSha256 && old.spkiDer.contentEquals(new.spkiDer)) {
                "Existing key identity cannot be replaced under the same ID"
            }
            val allowed = when (old.lifecycle) {
                VNextManifestSecurity.Lifecycle.PENDING -> setOf(
                    VNextManifestSecurity.Lifecycle.PENDING, VNextManifestSecurity.Lifecycle.ACTIVE,
                    VNextManifestSecurity.Lifecycle.REVOKED)
                VNextManifestSecurity.Lifecycle.ACTIVE -> setOf(
                    VNextManifestSecurity.Lifecycle.ACTIVE, VNextManifestSecurity.Lifecycle.RETIRING,
                    VNextManifestSecurity.Lifecycle.RETIRED, VNextManifestSecurity.Lifecycle.REVOKED)
                VNextManifestSecurity.Lifecycle.RETIRING -> setOf(
                    VNextManifestSecurity.Lifecycle.RETIRING, VNextManifestSecurity.Lifecycle.RETIRED,
                    VNextManifestSecurity.Lifecycle.REVOKED)
                VNextManifestSecurity.Lifecycle.RETIRED -> setOf(VNextManifestSecurity.Lifecycle.RETIRED)
                VNextManifestSecurity.Lifecycle.REVOKED -> setOf(VNextManifestSecurity.Lifecycle.REVOKED)
            }
            require(new.lifecycle in allowed) { "Retired/revoked key resurrection or invalid lifecycle transition" }
        }
        // Fresh identities are introduced as pending only, even under recovery authorization.
        proposed.filter { it.keyId !in oldById }.forEach {
            require(it.lifecycle == VNextManifestSecurity.Lifecycle.PENDING) { "New key must begin pending" }
        }
        return TrustedState(sequence, proposed.map { it.copy(spkiDer = it.spkiDer.clone()) })
    }

    private fun requireExactKeys(o: JSONObject, required: Set<String>) {
        val keys = mutableSetOf<String>()
        val iterator = o.keys()
        while (iterator.hasNext()) keys.add(iterator.next())
        require(keys == required) { "Invalid recovery statement fields" }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
