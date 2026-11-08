package com.typezero.siphon.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64

class VNextRecoveryAuthorityTest {
    private val now = Instant.parse("2026-11-07T20:00:00Z")
    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp384r1"))
    }.generateKeyPair()
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun pinned(id: String, pair: KeyPair, state: VNextManifestSecurity.Lifecycle) =
        VNextManifestSecurity.PinnedKey(id, digest(pair.public.encoded), pair.public.encoded, state)
    private fun authority(pair: KeyPair) = VNextRecoveryAuthority.RecoveryAnchor(
        "offline-recovery-01", digest(pair.public.encoded), pair.public.encoded)
    private fun entry(key: VNextManifestSecurity.PinnedKey): JSONObject = JSONObject().apply {
        put("keyId", key.keyId)
        put("profile", VNextManifestSecurity.PROFILE)
        put("spkiSha256", key.spkiSha256)
        put("spkiDerBase64", Base64.getEncoder().encodeToString(key.spkiDer))
        put("lifecycleState", key.lifecycle.name.lowercase())
    }
    private fun statement(
        anchor: VNextRecoveryAuthority.RecoveryAnchor,
        sequence: Long,
        keys: List<VNextManifestSecurity.PinnedKey>
    ) = JSONObject().apply {
        put("schemaVersion", 1)
        put("appId", "siphon")
        put("trustDomain", "release-signing")
        put("trustSequence", sequence)
        put("publishedAt", "2026-11-07T19:00:00Z")
        put("authority", JSONObject().apply {
            put("role", "recovery")
            put("keyId", anchor.keyId)
            put("spkiSha256", anchor.spkiSha256)
        })
        put("releaseKeys", JSONArray().apply { keys.forEach { put(entry(it)) } })
    }.toString().toByteArray(Charsets.UTF_8)
    private fun sign(pair: KeyPair, bytes: ByteArray): ByteArray = Signature.getInstance("SHA384withECDSA")
        .run { initSign(pair.private); update(bytes); sign() }
    private fun check(
        bytes: ByteArray, sig: ByteArray,
        anchor: VNextRecoveryAuthority.RecoveryAnchor, old: VNextRecoveryAuthority.TrustedState
    ) = VNextRecoveryAuthority.verifyAndPropose(bytes, sig, anchor, old, "siphon", now)

    @Test fun independentRecoveryCanRevokeAndIntroducePendingKey() {
        val recovery = keyPair()
        val a = pinned("release-a", keyPair(), VNextManifestSecurity.Lifecycle.ACTIVE)
        val b = pinned("release-b", keyPair(), VNextManifestSecurity.Lifecycle.PENDING)
        val anchor = authority(recovery)
        val bytes = statement(anchor, 2, listOf(a.copy(lifecycle = VNextManifestSecurity.Lifecycle.REVOKED), b))
        val next = check(bytes, sign(recovery, bytes), anchor, VNextRecoveryAuthority.TrustedState(1, listOf(a)))
        assertEquals(2L, next.sequence)
        assertEquals(VNextManifestSecurity.Lifecycle.REVOKED, next.releaseKeys[0].lifecycle)
        assertEquals(VNextManifestSecurity.Lifecycle.PENDING, next.releaseKeys[1].lifecycle)
    }

    @Test fun routineKeyCannotApproveRecoveryAndTamperingFails() {
        val recovery = keyPair()
        val routine = keyPair()
        val a = pinned("release-a", routine, VNextManifestSecurity.Lifecycle.ACTIVE)
        val anchor = authority(recovery)
        val bytes = statement(anchor, 2, listOf(a.copy(lifecycle = VNextManifestSecurity.Lifecycle.REVOKED)))
        val current = VNextRecoveryAuthority.TrustedState(1, listOf(a))
        assertThrows(Exception::class.java) { check(bytes, sign(routine, bytes), anchor, current) }
        val tampered = bytes.clone().also { it[it.size - 2] = (it[it.size - 2].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { check(tampered, sign(recovery, bytes), anchor, current) }
        val sameAsRoutine = VNextRecoveryAuthority.RecoveryAnchor("offline-recovery-01", a.spkiSha256, a.spkiDer)
        assertThrows(Exception::class.java) { check(bytes, sign(routine, bytes), sameAsRoutine, current) }
    }

    @Test fun replayResurrectionAndIdentityReplacementAreRejected() {
        val recovery = keyPair()
        val anchor = authority(recovery)
        val retired = pinned("release-a", keyPair(), VNextManifestSecurity.Lifecycle.RETIRED)
        val revoked = pinned("release-b", keyPair(), VNextManifestSecurity.Lifecycle.REVOKED)
        val old = VNextRecoveryAuthority.TrustedState(4, listOf(retired, revoked))
        val replay = statement(anchor, 4, listOf(retired, revoked))
        assertThrows(Exception::class.java) { check(replay, sign(recovery, replay), anchor, old) }
        val revived = statement(anchor, 5, listOf(
            retired.copy(lifecycle = VNextManifestSecurity.Lifecycle.ACTIVE), revoked))
        assertThrows(Exception::class.java) { check(revived, sign(recovery, revived), anchor, old) }
        val replaced = statement(anchor, 5, listOf(
            pinned("release-a", keyPair(), VNextManifestSecurity.Lifecycle.RETIRED), revoked))
        assertThrows(Exception::class.java) { check(replaced, sign(recovery, replaced), anchor, old) }
        val removed = statement(anchor, 5, listOf(retired))
        assertThrows(Exception::class.java) { check(removed, sign(recovery, removed), anchor, old) }
    }

    @Test fun newKeyMustBePendingAndRecoveryIdentityIsImmutable() {
        val recovery = keyPair()
        val anchor = authority(recovery)
        val installed = pinned("release-a", keyPair(), VNextManifestSecurity.Lifecycle.ACTIVE)
        val newActive = pinned("release-b", keyPair(), VNextManifestSecurity.Lifecycle.ACTIVE)
        val current = VNextRecoveryAuthority.TrustedState(1, listOf(installed))
        val bytes = statement(anchor, 2, listOf(installed, newActive))
        assertThrows(Exception::class.java) { check(bytes, sign(recovery, bytes), anchor, current) }
        val alternate = authority(keyPair())
        assertThrows(Exception::class.java) { check(bytes, sign(recovery, bytes), alternate, current) }
    }
}
