package com.typezero.siphon.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class VNextEndToEndStageTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T20:00:00Z"), ZoneOffset.UTC)
    private val channel = UpdateChannel.fromWire("development")
    private val url = "https://raw.githubusercontent.com/MikereDD/Siphon/main/release-manifest-vnext.json"
    private val pair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp384r1"))
    }.generateKeyPair()
    private val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest(pair.public.encoded).joinToString("") { "%02x".format(it) }
    private val key = VNextManifestSecurity.PinnedKey(
        "local-dev-test", fingerprint, pair.public.encoded, VNextManifestSecurity.Lifecycle.ACTIVE
    )
    private val policy = VNextTrustPolicy.fromInstalledAnchors(listOf(key))

    private class MemoryStore : VNextReplayGate.Store {
        val values = mutableMapOf<String, VNextManifestSecurity.AcceptedState>()
        var rejectWrites = false
        override fun read(scope: String) = values[scope]
        override fun write(scope: String, state: VNextManifestSecurity.AcceptedState) {
            if (rejectWrites) error("durable write failed")
            values[scope] = state
        }
    }

    private fun manifest(sequence: Int): ByteArray =
        """{"schemaVersion":3,"appId":"siphon","platform":"android","channel":"development","version":"0.3-dev.8","minimumUpdaterProtocol":3,"manifestSequence":$sequence,"publishedAt":"2026-11-07T19:00:00Z","expiresAt":"2026-11-08T20:00:00Z","releaseSigning":{"profile":"${VNextManifestSecurity.PROFILE}","keyId":"local-dev-test","spkiSha256":"$fingerprint"},"assets":[]}""".toByteArray()

    private fun sign(bytes: ByteArray): ByteArray = Signature.getInstance("SHA384withECDSA").run {
        initSign(pair.private)
        update(bytes)
        sign()
    }

    private fun admit(store: MemoryStore, bytes: ByteArray, signature: ByteArray = sign(bytes),
                      p: VNextTrustPolicy = policy) =
        VNextStageAuthorization.authenticateAndPersist(url, channel, bytes, signature, p, store, clock)

    @Test fun authenticatedMetadataIsPersistedBeforeStaging() {
        val store = MemoryStore()
        val bytes = manifest(1)
        val result = admit(store, bytes)
        assertEquals(1L, result.sequence)
        assertEquals(result.state, store.read(result.scope))
    }

    @Test fun olderSequenceRejectedAndOriginalRecordRetained() {
        val store = MemoryStore()
        admit(store, manifest(5))
        assertThrows(IllegalArgumentException::class.java) { admit(store, manifest(4)) }
        assertEquals(5L, store.values.values.single().sequence)
    }

    @Test fun persistenceFailureBlocksAdmission() {
        val store = MemoryStore().apply { rejectWrites = true }
        assertThrows(IllegalStateException::class.java) { admit(store, manifest(1)) }
        assertEquals(0, store.values.size)
    }

    @Test fun invalidSignatureNeverPersists() {
        val store = MemoryStore()
        val bytes = manifest(1)
        assertThrows(Exception::class.java) { admit(store, bytes, byteArrayOf(1, 2, 3)) }
        assertEquals(0, store.values.size)
    }

    @Test fun revokedSigningIdentityNeverPersists() {
        val store = MemoryStore()
        val revoked = VNextTrustPolicy.fromInstalledAnchors(
            listOf(key.copy(lifecycle = VNextManifestSecurity.Lifecycle.REVOKED))
        )
        assertThrows(Exception::class.java) { admit(store, manifest(1), p = revoked) }
        assertEquals(0, store.values.size)
    }

    @Test fun unapprovedFeedRejectedWithoutPersisting() {
        val store = MemoryStore()
        val bytes = manifest(1)
        assertThrows(Exception::class.java) {
            VNextStageAuthorization.authenticateAndPersist(
                "https://example.com/manifest.json", channel, bytes, sign(bytes), policy, store, clock
            )
        }
        assertEquals(0, store.values.size)
    }
}
