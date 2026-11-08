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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class VNextTrustJournalTest {
    private val now = Instant.parse("2026-11-07T20:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private fun pair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp384r1"))
    }.generateKeyPair()
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun pin(id: String, pair: KeyPair, state: VNextManifestSecurity.Lifecycle) =
        VNextManifestSecurity.PinnedKey(id, sha(pair.public.encoded), pair.public.encoded, state)
    private fun anchor(pair: KeyPair) = VNextRecoveryAuthority.RecoveryAnchor(
        "offline-recovery-01", sha(pair.public.encoded), pair.public.encoded)
    private fun statement(recovery: VNextRecoveryAuthority.RecoveryAnchor, seq: Long,
                          keys: List<VNextManifestSecurity.PinnedKey>): ByteArray = JSONObject().apply {
        put("schemaVersion", 1)
        put("appId", "siphon")
        put("trustDomain", "release-signing")
        put("trustSequence", seq)
        put("publishedAt", "2026-11-07T19:00:00Z")
        put("authority", JSONObject().put("role", "recovery")
            .put("keyId", recovery.keyId).put("spkiSha256", recovery.spkiSha256))
        put("releaseKeys", JSONArray().apply { keys.forEach { key ->
            put(JSONObject().put("keyId", key.keyId).put("profile", VNextManifestSecurity.PROFILE)
                .put("spkiSha256", key.spkiSha256)
                .put("spkiDerBase64", Base64.getEncoder().encodeToString(key.spkiDer))
                .put("lifecycleState", key.lifecycle.name.lowercase()))
        } })
    }.toString().toByteArray()
    private fun sign(pair: KeyPair, statement: ByteArray) = Signature.getInstance("SHA384withECDSA").run {
        initSign(pair.private); update(statement); sign()
    }
    private class MemoryStorage : VNextTrustJournal.Storage {
        var journal: ByteArray? = null
        var water: ByteArray? = null
        var failWater = false
        override fun readJournal() = journal?.clone()
        override fun readHighWater() = water?.clone()
        override fun writeJournal(bytes: ByteArray) { journal = bytes.clone() }
        override fun writeHighWater(bytes: ByteArray) {
            if (failWater) error("Interrupted high-water write")
            water = bytes.clone()
        }
    }
    private class Fixture {
        val recovery = pairStatic()
        val anchor = VNextRecoveryAuthority.RecoveryAnchor(
            "offline-recovery-01", digestStatic(recovery.public.encoded), recovery.public.encoded)
        val release = pairStatic()
        val installed = VNextManifestSecurity.PinnedKey(
            "release-a", digestStatic(release.public.encoded), release.public.encoded,
            VNextManifestSecurity.Lifecycle.ACTIVE)
        val initial = VNextRecoveryAuthority.TrustedState(1, listOf(installed))
        val storage = MemoryStorage()
        fun journal() = VNextTrustJournal(storage, anchor, initial, "siphon",
            Clock.fixed(Instant.parse("2026-11-07T20:00:00Z"), ZoneOffset.UTC))
        companion object {
            private fun pairStatic(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp384r1"))
            }.generateKeyPair()
            private fun digestStatic(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }

    @Test fun signedHistoryReplaysAcrossRestart() {
        val f = Fixture()
        val b2 = statement(f.anchor, 2, listOf(f.installed.copy(lifecycle = VNextManifestSecurity.Lifecycle.RETIRING)))
        assertEquals(2L, f.journal().applySignedRecovery(b2, sign(f.recovery, b2)).sequence)
        val b3 = statement(f.anchor, 3, listOf(f.installed.copy(lifecycle = VNextManifestSecurity.Lifecycle.REVOKED)))
        assertEquals(3L, f.journal().applySignedRecovery(b3, sign(f.recovery, b3)).sequence)
        assertEquals(3L, f.journal().load().sequence)
        assertEquals(VNextManifestSecurity.Lifecycle.REVOKED, f.journal().load().releaseKeys[0].lifecycle)
    }

    @Test fun journalTamperingAndJournalOnlyRollbackFailClosed() {
        val f = Fixture()
        val b2 = statement(f.anchor, 2, listOf(f.installed.copy(lifecycle = VNextManifestSecurity.Lifecycle.REVOKED)))
        f.journal().applySignedRecovery(b2, sign(f.recovery, b2))
        val saved = f.storage.journal!!.clone()
        f.storage.journal = saved.clone().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { f.journal().load() }
        f.storage.journal = null // rollback the journal but leave high-water intact
        assertThrows(Exception::class.java) { f.journal().load() }
    }

    @Test fun interruptedWatermarkWriteIsRecoverableFromSignedJournal() {
        val f = Fixture()
        val b2 = statement(f.anchor, 2, listOf(f.installed.copy(lifecycle = VNextManifestSecurity.Lifecycle.REVOKED)))
        f.storage.failWater = true
        assertThrows(Exception::class.java) { f.journal().applySignedRecovery(b2, sign(f.recovery, b2)) }
        f.storage.failWater = false
        assertEquals(2L, f.journal().load().sequence)
    }

    @Test fun invalidSignatureReplayAndHighWaterCorruptionRejected() {
        val f = Fixture()
        val b2 = statement(f.anchor, 2, listOf(f.installed.copy(lifecycle = VNextManifestSecurity.Lifecycle.REVOKED)))
        assertThrows(Exception::class.java) { f.journal().applySignedRecovery(b2, sign(f.release, b2)) }
        assertEquals(1L, f.journal().load().sequence)
        f.journal().applySignedRecovery(b2, sign(f.recovery, b2))
        assertThrows(Exception::class.java) { f.journal().applySignedRecovery(b2, sign(f.recovery, b2)) }
        f.storage.water = "not-a-number".toByteArray()
        assertThrows(Exception::class.java) { f.journal().load() }
    }
}
