package com.typezero.siphon.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class VNextApkIntegrityGateTest {
    @get:Rule val temp = TemporaryFolder()
    private val now = Instant.parse("2026-11-07T20:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val pair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp384r1"))
    }.generateKeyPair()
    private val fingerprint = hash(pair.public.encoded)
    private val key = VNextManifestSecurity.PinnedKey(
        "release-test", fingerprint, pair.public.encoded, VNextManifestSecurity.Lifecycle.ACTIVE
    )
    private val version = "0.3-dev.8"
    private val assetName = "Siphon-v$version.apk"
    private val channel = UpdateChannel.fromWire("development")

    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(b).joinToString("") { "%02x".format(it) }

    private fun sign(bytes: ByteArray) = Signature.getInstance("SHA384withECDSA").run {
        initSign(pair.private)
        update(bytes)
        sign()
    }

    private data class Fixture(val manifest: ByteArray, val manifestSig: ByteArray,
                               val apk: File, val apkSig: File)

    private fun fixture(
        packageId: String = UpdateTrust.PACKAGE_ID,
        cert: String = UpdateTrust.APK_SIGNING_CERT_SHA256,
        signatureKeyId: String = "release-test",
        duplicateAsset: Boolean = false
    ): Fixture {
        val apkBytes = "development-test-apk-bytes".toByteArray()
        val apk = temp.newFile("sample-${System.nanoTime()}.apk").apply { writeBytes(apkBytes) }
        val sigBytes = sign(apkBytes)
        val signature = temp.newFile("sample-${System.nanoTime()}.sig").apply { writeBytes(sigBytes) }
        val asset = """{"name":"$assetName","size":${apkBytes.size},"sha256":"${hash(apkBytes)}","url":"https://github.com/MikereDD/Siphon/releases/test.apk","packageId":"$packageId","signingCertificateSha256":"$cert","signature":{"name":"$assetName.sig","profile":"${VNextManifestSecurity.PROFILE}","keyId":"$signatureKeyId","size":${sigBytes.size},"sha256":"${hash(sigBytes)}"}}"""
        val assets = if (duplicateAsset) "$asset,$asset" else asset
        val manifest = """{"schemaVersion":3,"appId":"siphon","platform":"android","version":"$version","channel":"development","minimumUpdaterProtocol":3,"manifestSequence":1,"publishedAt":"2026-11-07T19:00:00Z","expiresAt":"2026-11-08T20:00:00Z","releaseSigning":{"profile":"${VNextManifestSecurity.PROFILE}","keyId":"release-test","spkiSha256":"$fingerprint"},"assets":[$assets]}""".toByteArray()
        return Fixture(manifest, sign(manifest), apk, signature)
    }

    private fun verify(f: Fixture, policy: VNextTrustPolicy =
        VNextTrustPolicy.fromInstalledAnchors(listOf(key))) =
        VNextApkIntegrityGate(clock).verify(f.manifest, f.manifestSig, policy,
            channel, assetName, f.apk, f.apkSig)

    @Test fun signedManifestAndExactApkAccepted() {
        val result = verify(fixture())
        assertEquals("release-test", result.signingKeyId)
        assertEquals(UpdateTrust.PACKAGE_ID, result.expectedPackageId)
    }

    @Test fun tamperedApkRejected() {
        val f = fixture()
        f.apk.appendBytes(byteArrayOf(0))
        assertThrows(IllegalArgumentException::class.java) { verify(f) }
    }

    @Test fun replacedSignatureRejected() {
        val f = fixture()
        f.apkSig.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(IllegalArgumentException::class.java) { verify(f) }
    }

    @Test fun wrongPackageRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            verify(fixture(packageId = "com.example.other"))
        }
    }

    @Test fun unapprovedApkCertificateRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            verify(fixture(cert = "0".repeat(64)))
        }
    }

    @Test fun detachedSignerMustMatchManifestSigner() {
        assertThrows(IllegalArgumentException::class.java) {
            verify(fixture(signatureKeyId = "other"))
        }
    }

    @Test fun duplicateAssetsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            verify(fixture(duplicateAsset = true))
        }
    }

    @Test fun revokedKeyCannotAuthorizeApk() {
        val revoked = VNextTrustPolicy.fromInstalledAnchors(
            listOf(key.copy(lifecycle = VNextManifestSecurity.Lifecycle.REVOKED))
        )
        assertThrows(Exception::class.java) { verify(fixture(), revoked) }
    }

    @Test fun alteredManifestSignatureRejected() {
        val f = fixture()
        val bad = f.manifestSig.copyOf().also { it[0] = 0 }
        assertThrows(Exception::class.java) {
            verify(f.copy(manifestSig = bad))
        }
    }
}
