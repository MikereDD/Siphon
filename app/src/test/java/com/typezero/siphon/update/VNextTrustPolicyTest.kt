package com.typezero.siphon.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VNextTrustPolicyTest {
    private fun key(id: String, state: VNextManifestSecurity.Lifecycle, marker: Char):
        VNextManifestSecurity.PinnedKey = VNextManifestSecurity.PinnedKey(
        id, marker.toString().repeat(64), byteArrayOf(marker.code.toByte()), state
    )

    @Test fun unprovisionedAndDuplicateAnchorsFailClosed() {
        assertEquals(null, VNextInstalledTrust.configuredPolicyOrNull())
        assertThrows(IllegalArgumentException::class.java) {
            VNextTrustPolicy.fromInstalledAnchors(emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            VNextTrustPolicy.fromInstalledAnchors(listOf(
                key("release-a", VNextManifestSecurity.Lifecycle.ACTIVE, 'a'),
                key("release-a", VNextManifestSecurity.Lifecycle.RETIRING, 'b')
            ))
        }
    }

    @Test fun onlyActiveAndRetiringMayAuthorize() {
        val policy = VNextTrustPolicy.fromInstalledAnchors(listOf(
            key("pending", VNextManifestSecurity.Lifecycle.PENDING, 'a'),
            key("active", VNextManifestSecurity.Lifecycle.ACTIVE, 'b'),
            key("retiring", VNextManifestSecurity.Lifecycle.RETIRING, 'c'),
            key("retired", VNextManifestSecurity.Lifecycle.RETIRED, 'd'),
            key("revoked", VNextManifestSecurity.Lifecycle.REVOKED, 'e')
        ))
        assertEquals(setOf("active", "retiring"), policy.approvedKeys().map { it.keyId }.toSet())
        for (id in listOf("pending", "retired", "revoked")) {
            assertThrows(IllegalArgumentException::class.java) {
                policy.authorize(id, mapOf("pending" to 'a', "retired" to 'd', "revoked" to 'e')
                    .getValue(id).toString().repeat(64))
            }
        }
        assertEquals("active", policy.authorize("active", "b".repeat(64)).keyId)
        assertThrows(IllegalArgumentException::class.java) { policy.authorize("active", "a".repeat(64)) }
    }

    @Test fun publicKeyBytesAreDefensivelyCopied() {
        val input = key("release", VNextManifestSecurity.Lifecycle.ACTIVE, 'a')
        val policy = VNextTrustPolicy.fromInstalledAnchors(listOf(input))
        input.spkiDer[0] = 0
        val first = policy.authorize("release", "a".repeat(64))
        assertFalse(first.spkiDer[0] == 0.toByte())
        first.spkiDer[0] = 0
        assertTrue(policy.authorize("release", "a".repeat(64)).spkiDer[0] != 0.toByte())
    }
}
