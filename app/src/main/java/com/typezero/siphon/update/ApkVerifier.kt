package com.typezero.siphon.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

object ApkVerifier {
    data class Verified(val packageId: String, val versionCode: Long, val signerSha256: String)

    @Suppress("DEPRECATION")
    fun verify(context: Context, apk: File, asset: ReleaseAsset): Verified {
        require(UpdateTrust.cryptographicAnchorsConfigured) {
            "Release trust anchors are not configured in this build"
        }

        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        val archive = requireNotNull(pm.getPackageArchiveInfo(apk.absolutePath, flags)) {
            "Downloaded file is not a readable APK"
        }
        require(archive.packageName == UpdateTrust.PACKAGE_ID && archive.packageName == asset.packageId) {
            "APK package ID mismatch"
        }

        val candidateSigner = signerDigest(archive)
        require(candidateSigner.equals(UpdateTrust.APK_SIGNING_CERT_SHA256, true)) {
            "APK signing certificate does not match the pinned Siphon identity"
        }
        require(candidateSigner.equals(asset.signingCertificateSha256, true)) {
            "APK signing certificate does not match release metadata"
        }

        val installed = pm.getPackageInfo(UpdateTrust.PACKAGE_ID, flags)
        val installedSigner = signerDigest(installed)
        require(candidateSigner.equals(installedSigner, true)) {
            "APK signing certificate does not match the installed Siphon application"
        }

        val candidateCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else archive.versionCode.toLong()
        val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.longVersionCode
        } else installed.versionCode.toLong()

        require(candidateCode > installedCode) {
            "Downloaded APK is not a newer Android versionCode"
        }

        return Verified(archive.packageName, candidateCode, candidateSigner)
    }

    @Suppress("DEPRECATION")
    private fun signerDigest(info: android.content.pm.PackageInfo): String {
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = requireNotNull(info.signingInfo) { "APK has no signing information" }
            require(!signing.hasMultipleSigners()) { "Multiple APK signers are not supported" }
            signing.apkContentsSigners.single().toByteArray()
        } else {
            requireNotNull(info.signatures).single().toByteArray()
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
