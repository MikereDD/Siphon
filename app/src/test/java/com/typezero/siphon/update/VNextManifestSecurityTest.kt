package com.typezero.siphon.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant

class VNextManifestSecurityTest {
    private val now = Instant.parse("2026-11-07T20:00:00Z")
    private val pair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp384r1"))
    }.generateKeyPair()
    private val fingerprint = MessageDigest.getInstance("SHA-256").digest(pair.public.encoded)
        .joinToString("") { "%02x".format(it) }
    private val active = VNextManifestSecurity.PinnedKey(
        "test-p384", fingerprint, pair.public.encoded, VNextManifestSecurity.Lifecycle.ACTIVE
    )

    private fun bytes(sequence: Int = 1, expiry: String = "2026-11-08T20:00:00Z"): ByteArray =
        """{"schemaVersion":3,"appId":"siphon","platform":"android","version":"0.3-dev.8","channel":"development","minimumUpdaterProtocol":3,"manifestSequence":$sequence,"publishedAt":"2026-11-07T19:00:00Z","expiresAt":"$expiry","releaseSigning":{"profile":"typezero-ecdsa-p384-sha384-v1","keyId":"test-p384","spkiSha256":"$fingerprint"},"assets":[]}"""
            .toByteArray(Charsets.UTF_8)

    private fun sign(bytes: ByteArray): ByteArray = Signature.getInstance("SHA384withECDSA").run {
        initSign(pair.private)
        update(bytes)
        sign()
    }

    private fun verify(
        raw: ByteArray,
        signature: ByteArray = sign(raw),
        keys: Collection<VNextManifestSecurity.PinnedKey> = listOf(active),
        state: VNextManifestSecurity.AcceptedState? = null
    ) = VNextManifestSecurity.verify(raw, signature, keys, "siphon", "development", now, state)

    @Test fun validSignedManifestAccepted() {
        val accepted = verify(bytes())
        assertEquals(1L, accepted.state.sequence)
        assertEquals("test-p384", accepted.keyId)
        verify(bytes(), state = accepted.state) // same authenticated bytes are idempotent
    }

    @Test fun tamperedManifestRejected() {
        val raw = bytes()
        val sig = sign(raw)
        assertThrows(Exception::class.java) { verify(raw + byteArrayOf(32), sig) }
    }

    @Test fun wrongKeyOrRetiredKeyRejected() {
        assertThrows(Exception::class.java) { verify(bytes(), keys = emptyList()) }
        assertThrows(Exception::class.java) {
            verify(bytes(), keys = listOf(active.copy(lifecycle = VNextManifestSecurity.Lifecycle.REVOKED)))
        }
        assertThrows(Exception::class.java) {
            verify(bytes(), keys = listOf(active.copy(lifecycle = VNextManifestSecurity.Lifecycle.RETIRED)))
        }
    }

    @Test fun wrongSignatureRejected() {
        val raw = bytes()
        val bad = sign(raw).clone()
        bad[bad.lastIndex] = (bad.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { verify(raw, bad) }
    }

    @Test fun expiredManifestRejected() {
        assertThrows(Exception::class.java) { verify(bytes(expiry = "2026-11-07T19:30:00Z")) }
    }

    @Test fun replayAndSequenceCollisionRejected() {
        val accepted = verify(bytes(sequence = 2)).state
        assertThrows(Exception::class.java) { verify(bytes(sequence = 1), state = accepted) }
        val collided = bytes(sequence = 2, expiry = "2026-11-09T20:00:00Z")
        assertThrows(Exception::class.java) { verify(collided, state = accepted) }
    }
}
