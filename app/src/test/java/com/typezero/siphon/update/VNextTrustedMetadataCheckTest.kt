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

class VNextTrustedMetadataCheckTest {
    private val now = Instant.parse("2026-11-07T20:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val manifestUrl = "https://raw.githubusercontent.com/MikereDD/Siphon/main/release-manifest-vnext.json"
    private val channel = UpdateChannel.fromWire("development")
    private val pair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp384r1"))
    }.generateKeyPair()
    private val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest(pair.public.encoded).joinToString("") { "%02x".format(it) }
    private val activeKey = VNextManifestSecurity.PinnedKey(
        "local-test-key", fingerprint, pair.public.encoded, VNextManifestSecurity.Lifecycle.ACTIVE
    )

    private class MemoryStore : VNextReplayGate.Store {
        val states = mutableMapOf<String, VNextManifestSecurity.AcceptedState>()
        override fun read(scope: String) = states[scope]
        override fun write(scope: String, state: VNextManifestSecurity.AcceptedState) {
            states[scope] = state
        }
    }

    private fun manifest(): ByteArray =
        """{"schemaVersion":3,"appId":"siphon","platform":"android","version":"0.3-dev.8","channel":"development","minimumUpdaterProtocol":3,"manifestSequence":1,"publishedAt":"2026-11-07T19:00:00Z","expiresAt":"2026-11-08T20:00:00Z","releaseSigning":{"profile":"typezero-ecdsa-p384-sha384-v1","keyId":"local-test-key","spkiSha256":"$fingerprint"},"assets":[{"name":"Siphon-v0.3-dev.8.apk","size":12,"sha256":"${"a".repeat(64)}","url":"https://github.com/MikereDD/Siphon/releases/download/v0.3-dev.8/Siphon-v0.3-dev.8.apk"}]}"""
            .toByteArray(Charsets.UTF_8)

    private fun signature(raw: ByteArray): ByteArray = Signature.getInstance("SHA384withECDSA").run {
        initSign(pair.private)
        update(raw)
        sign()
    }

    @Test fun locallyAuthorizedSignedMetadataAccepted() {
        val raw = manifest()
        val sig = signature(raw)
        val store = MemoryStore()
        val urls = mutableListOf<String>()
        val checker = VNextTrustedMetadataCheck(store, { url, _ ->
            urls += url
            when (url) {
                manifestUrl -> raw
                "$manifestUrl.sig" -> sig
                else -> error("unexpected network request")
            }
        }, clock)
        val result = checker.check(
            manifestUrl, VNextTrustPolicy.fromInstalledAnchors(listOf(activeKey)), channel
        )
        assertEquals(1L, result.sequence)
        assertEquals("local-test-key", result.signingKeyId)
        assertEquals(listOf(manifestUrl, "$manifestUrl.sig"), urls)
        assertEquals(1, store.states.size)
    }

    @Test fun retiredOnlyPolicyRejectsBeforeNetworkAccess() {
        var networkCalled = false
        val checker = VNextTrustedMetadataCheck(MemoryStore(), { _, _ ->
            networkCalled = true
            error("network should not be used")
        }, clock)
        val policy = VNextTrustPolicy.fromInstalledAnchors(
            listOf(activeKey.copy(lifecycle = VNextManifestSecurity.Lifecycle.REVOKED))
        )
        assertThrows(IllegalArgumentException::class.java) {
            checker.check(manifestUrl, policy, channel)
        }
        assertEquals(false, networkCalled)
    }

    @Test fun disallowedFeedHostRejectedBeforeNetworkAccess() {
        var networkCalled = false
        val checker = VNextTrustedMetadataCheck(MemoryStore(), { _, _ ->
            networkCalled = true
            error("network should not be used")
        }, clock)
        assertThrows(Exception::class.java) {
            checker.check("https://example.com/malicious.json",
                VNextTrustPolicy.fromInstalledAnchors(listOf(activeKey)), channel)
        }
        assertEquals(false, networkCalled)
    }
}
