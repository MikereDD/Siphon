package com.typezero.siphon.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/**
 * Phase 4D: fail-closed Android package-identity inspection after Phase 4C
 * has authorized the exact APK bytes. This does NOT cryptographically validate
 * the APK Signing Block or grant permission to install. That requires separate
 * end-to-end verification and an unchanged-file handoff.
 */
internal object VNextAndroidApkIdentityGate {
    data class PackageSnapshot(
        val packageId: String,
        val versionName: String?,
        val versionCode: Long,
        val signerFingerprints: List<String>
    )

    data class ApprovedIdentity(
        val packageId: String,
        val versionName: String,
        val versionCode: Long,
        val signerSha256: String
    )

    /** Pure validation for unit testing; caller must extract authentic data. */
    fun validate(
        candidate: PackageSnapshot,
        installed: PackageSnapshot,
        eligible: VNextApkIntegrityGate.EligibleApk
    ): ApprovedIdentity {
        require(candidate.packageId == UpdateTrust.PACKAGE_ID) { "APK package ID mismatch" }
        require(installed.packageId == UpdateTrust.PACKAGE_ID) { "Installed package ID mismatch" }
        require(eligible.expectedPackageId == UpdateTrust.PACKAGE_ID) {
            "Signed release package identity mismatch"
        }
        require(candidate.versionName == eligible.version) { "APK versionName differs from signed release" }
        require(candidate.versionCode > installed.versionCode) { "APK versionCode is not newer" }
        require(candidate.versionCode > 0) { "Invalid APK versionCode" }
        require(candidate.signerFingerprints.size == 1 && installed.signerFingerprints.size == 1) {
            "Exactly one APK signer is required"
        }
        val signer = candidate.signerFingerprints.single()
        require(signer.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid signer SHA-256" }
        require(signer.equals(UpdateTrust.APK_SIGNING_CERT_SHA256, ignoreCase = true)) {
            "APK signer does not match the locally pinned certificate"
        }
        require(signer.equals(eligible.expectedApkCertSha256, ignoreCase = true)) {
            "APK signer does not match signed release identity"
        }
        require(signer.equals(installed.signerFingerprints.single(), ignoreCase = true)) {
            "APK signer does not match installed Siphon"
        }
        return ApprovedIdentity(candidate.packageId, eligible.version, candidate.versionCode, signer.lowercase())
    }

    @Suppress("DEPRECATION")
    fun inspect(
        context: Context,
        apk: File,
        eligible: VNextApkIntegrityGate.EligibleApk
    ): ApprovedIdentity {
        require(apk.isFile && apk.length() > 0) { "Candidate APK missing or empty" }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        // Android may decline to expose archive signing information; fail closed.
        val archive = requireNotNull(pm.getPackageArchiveInfo(apk.absolutePath, flags)) {
            "Unable to inspect candidate APK"
        }
        val installed = pm.getPackageInfo(UpdateTrust.PACKAGE_ID, flags)
        return validate(snapshot(archive), snapshot(installed), eligible)
    }

    @Suppress("DEPRECATION")
    private fun snapshot(info: PackageInfo): PackageSnapshot = PackageSnapshot(
        packageId = info.packageName,
        versionName = info.versionName,
        versionCode = PackageInfoCompat.getLongVersionCode(info),
        signerFingerprints = certificateBytes(info).map(::sha256)
    )

    @Suppress("DEPRECATION")
    private fun certificateBytes(info: PackageInfo): List<ByteArray> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = requireNotNull(info.signingInfo) { "Missing package signing info" }
            require(!signing.hasMultipleSigners()) { "Multiple APK signers are not supported" }
            signing.apkContentsSigners
        } else {
            info.signatures
        }
        return requireNotNull(signatures) { "Missing APK signing certificate" }
            .map { it.toByteArray() }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
