package com.typezero.siphon.update

import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object ReleaseVerifier {
    fun verify(apk: File, signatureFile: File, asset: ReleaseAsset) {
        require(UpdateTrust.cryptographicAnchorsConfigured) {
            "Release trust anchors are not configured in this build"
        }
        require(asset.signature.algorithm == "rsa-sha256") { "Unsupported release signature algorithm" }
        require(asset.signature.keyId == UpdateTrust.RELEASE_KEY_ID) { "Unapproved release signing key" }
        require(asset.signature.publicKeySha256.equals(UpdateTrust.RELEASE_PUBLIC_KEY_SHA256, true)) {
            "Release public-key identity mismatch"
        }
        require(apk.length() == asset.size) { "APK size does not match release metadata" }
        require(signatureFile.length() == asset.signature.size) {
            "Signature size does not match release metadata"
        }
        require(sha256(apk).equals(asset.sha256, true)) { "APK SHA-256 verification failed" }
        require(sha256(signatureFile).equals(asset.signature.sha256, true)) {
            "Detached signature SHA-256 verification failed"
        }

        val publicKey = parsePublicKey(UpdateTrust.RELEASE_PUBLIC_KEY_PEM)
        val keyFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(publicKey.encoded).joinToString("") { "%02x".format(it) }
        require(keyFingerprint.equals(UpdateTrust.RELEASE_PUBLIC_KEY_SHA256, true)) {
            "Pinned release public key fingerprint mismatch"
        }

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        apk.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                verifier.update(buffer, 0, n)
            }
        }
        require(verifier.verify(signatureFile.readBytes())) { "Detached release signature verification failed" }
    }

    private fun parsePublicKey(pem: String) =
        KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(
                Base64.getDecoder().decode(
                    pem.replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replace(Regex("\\s+"), "")
                )
            )
        )

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
