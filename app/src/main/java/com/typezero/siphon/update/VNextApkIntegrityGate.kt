package com.typezero.siphon.update

import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Clock

/**
 * Phase 4C: a cryptographic eligibility check for a downloaded vNext APK.
 * This does NOT authorize installation. Android package/archive signature and
 * versionCode validation must run separately at the installation boundary.
 * Never use a key, digest, or certificate supplied by unsigned metadata.
 */
internal class VNextApkIntegrityGate(private val clock: Clock = Clock.systemUTC()) {
    data class EligibleApk(
        val assetName: String,
        val version: String,
        val manifestSequence: Long,
        val signingKeyId: String,
        val expectedPackageId: String,
        val expectedApkCertSha256: String,
        val apkSha256: String
    )

    fun verify(
        manifestBytes: ByteArray,
        manifestSignature: ByteArray,
        policy: VNextTrustPolicy,
        channel: UpdateChannel,
        assetName: String,
        apk: File,
        apkSignature: File
    ): EligibleApk {
        // Reauthenticate exact manifest bytes at the point where file bytes are approved.
        // Do not accept a previously parsed JSON object or unsigned asset description.
        val verified = VNextManifestSecurity.verify(
            manifestBytes, manifestSignature, policy.approvedKeys(),
            UpdateTrust.APP_ID, channel.wire, clock.instant()
        )
        val m = verified.manifest
        require(m.getString("platform") == "android") { "Wrong release platform" }
        require(m.getInt("minimumUpdaterProtocol") <= VNextManifestSecurity.PROTOCOL) {
            "Updater is too old"
        }
        val version = m.getString("version")
        TypezeroVersion.parse(version)
        require(assetName == "Siphon-v$version.apk") { "APK filename/version mismatch" }
        require(assetName.isNotBlank() && '/' !in assetName && '\\' !in assetName) {
            "Invalid APK asset name"
        }
        val assets = m.getJSONArray("assets")
        require(assets.length() > 0) { "Empty signed asset set" }
        val names = mutableSetOf<String>()
        var asset: JSONObject? = null
        for (i in 0 until assets.length()) {
            val item = assets.getJSONObject(i)
            val name = item.getString("name")
            require(name.isNotBlank() && '/' !in name && '\\' !in name) { "Invalid signed asset name" }
            require(names.add(name)) { "Duplicate signed asset name" }
            if (name == assetName) asset = item
        }
        val a = requireNotNull(asset) { "APK not listed in signed manifest" }
        require(a.getString("packageId") == UpdateTrust.PACKAGE_ID) {
            "Signed package identity mismatch"
        }
        val cert = a.getString("signingCertificateSha256")
        require(cert.equals(UpdateTrust.APK_SIGNING_CERT_SHA256, ignoreCase = true)) {
            "Signed APK certificate identity mismatch"
        }
        val size = a.getLong("size")
        require(size > 0 && apk.isFile && apk.length() == size) { "APK size mismatch" }
        val expectedHash = a.getString("sha256")
        require(expectedHash.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid APK hash" }
        val actualHash = sha256(apk)
        require(actualHash.equals(expectedHash, ignoreCase = true)) { "APK hash mismatch" }

        // For this development-only gate require an APK-level detached signature.
        // No downgrade to hash-only verification or legacy RSA is allowed here.
        val detached = a.getJSONObject("signature")
        require(detached.getString("name") == "$assetName.sig") {
            "APK signature asset name mismatch"
        }
        require(detached.getString("profile") == VNextManifestSecurity.PROFILE) {
            "Unsupported APK signature profile"
        }
        require(detached.getString("keyId") == verified.keyId) {
            "APK signing key differs from signed manifest identity"
        }
        val sigSize = detached.getLong("size")
        require(sigSize in 1..144 && apkSignature.isFile && apkSignature.length() == sigSize) {
            "Invalid APK detached signature size"
        }
        val sigHash = detached.getString("sha256")
        require(sigHash.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid signature hash" }
        require(sha256(apkSignature).equals(sigHash, ignoreCase = true)) {
            "Detached signature hash mismatch"
        }
        val signing = m.getJSONObject("releaseSigning")
        val key = policy.authorize(verified.keyId, signing.getString("spkiSha256"))
        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(key.spkiDer))
        require(publicKey is ECPublicKey && publicKey.params.curve.field.fieldSize == 384) {
            "APK release key is not P-384"
        }
        val verifier = Signature.getInstance("SHA384withECDSA")
        verifier.initVerify(publicKey)
        apk.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buf)
                if (count < 0) break
                verifier.update(buf, 0, count)
            }
        }
        require(verifier.verify(apkSignature.readBytes())) {
            "Detached APK release signature rejected"
        }
        return EligibleApk(assetName, version, verified.state.sequence, verified.keyId,
            UpdateTrust.PACKAGE_ID, UpdateTrust.APK_SIGNING_CERT_SHA256, actualHash)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buf)
                if (count < 0) break
                md.update(buf, 0, count)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
