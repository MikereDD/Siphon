package com.typezero.siphon.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VNextAndroidApkIdentityGateTest {
    private val pinned = UpdateTrust.APK_SIGNING_CERT_SHA256
    private val eligible = VNextApkIntegrityGate.EligibleApk(
        assetName = "Siphon-v0.3-dev.8.apk",
        version = "0.3-dev.8",
        manifestSequence = 12,
        signingKeyId = "test-release",
        expectedPackageId = UpdateTrust.PACKAGE_ID,
        expectedApkCertSha256 = pinned,
        apkSha256 = "a".repeat(64)
    )
    private val existing = VNextAndroidApkIdentityGate.PackageSnapshot(
        UpdateTrust.PACKAGE_ID, "0.3-dev.7", 9, listOf(pinned)
    )
    private val next = VNextAndroidApkIdentityGate.PackageSnapshot(
        UpdateTrust.PACKAGE_ID, "0.3-dev.8", 10, listOf(pinned)
    )

    private fun rejects(
        candidate: VNextAndroidApkIdentityGate.PackageSnapshot = next,
        installed: VNextAndroidApkIdentityGate.PackageSnapshot = existing,
        metadata: VNextApkIntegrityGate.EligibleApk = eligible
    ) {
        assertThrows(IllegalArgumentException::class.java) {
            VNextAndroidApkIdentityGate.validate(candidate, installed, metadata)
        }
    }

    @Test fun acceptsMatchingSignedIdentityAndStrictVersionUpgrade() {
        val result = VNextAndroidApkIdentityGate.validate(next, existing, eligible)
        assertEquals(UpdateTrust.PACKAGE_ID, result.packageId)
        assertEquals("0.3-dev.8", result.versionName)
        assertEquals(10L, result.versionCode)
        assertEquals(pinned, result.signerSha256)
    }

    @Test fun rejectsPackageMismatch() {
        rejects(candidate = next.copy(packageId = "com.attacker.clone"))
        rejects(installed = existing.copy(packageId = "com.attacker.clone"))
        rejects(metadata = eligible.copy(expectedPackageId = "com.attacker.clone"))
    }

    @Test fun rejectsVersionMismatchAndDowngrade() {
        rejects(candidate = next.copy(versionName = "0.3-dev.9"))
        rejects(candidate = next.copy(versionName = null))
        rejects(candidate = next.copy(versionCode = 9))
        rejects(candidate = next.copy(versionCode = 8))
        rejects(candidate = next.copy(versionCode = 0))
    }

    @Test fun rejectsUnsignedAndMultipleSignerPackages() {
        rejects(candidate = next.copy(signerFingerprints = emptyList()))
        rejects(installed = existing.copy(signerFingerprints = emptyList()))
        rejects(candidate = next.copy(signerFingerprints = listOf(pinned, pinned)))
        rejects(installed = existing.copy(signerFingerprints = listOf(pinned, pinned)))
    }

    @Test fun rejectsDifferentOrForgedSigner() {
        rejects(candidate = next.copy(signerFingerprints = listOf("0".repeat(64))))
        rejects(candidate = next.copy(signerFingerprints = listOf("malformed")))
        rejects(installed = existing.copy(signerFingerprints = listOf("0".repeat(64))))
        rejects(metadata = eligible.copy(expectedApkCertSha256 = "0".repeat(64)))
    }

    @Test fun acceptsHexCaseVariantsOnly() {
        val result = VNextAndroidApkIdentityGate.validate(
            next.copy(signerFingerprints = listOf(pinned.uppercase())), existing, eligible
        )
        assertEquals(pinned, result.signerSha256)
    }
}
