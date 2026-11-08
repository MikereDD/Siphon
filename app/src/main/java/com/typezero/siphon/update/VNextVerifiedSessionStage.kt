package com.typezero.siphon.update

import android.content.Context
import android.content.pm.PackageInstaller
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Phase 4E.2: copy previously authorized, private APK bytes into an Android
 * PackageInstaller session and compare the actual session bytes before handoff.
 *
 * This code NEVER commits an installation. In particular it does not replace
 * Android's full APK Signing Block validation or user approval flow. The caller
 * must separately authorize any later commit and handle installer status.
 */
internal object VNextVerifiedSessionStage {
    private const val ENTRY_NAME = "base.apk"
    private val SHA256_HEX = Regex("[0-9a-fA-F]{64}")

    data class StagedSession(
        val sessionId: Int,
        val digestSha256: String,
        val packageId: String,
        val manifestSequence: Long
    )

    /**
     * If an exception occurs, abandon the session. A successful result has
     * not installed anything; its session must be committed or abandoned by
     * a separate, explicitly enabled flow.
     */
    fun stage(context: Context, prepared: VNextPrivateApkStage.Prepared): StagedSession {
        val apk = prepared.file
        require(prepared.digestSha256.matches(SHA256_HEX)) { "Invalid approved APK digest" }
        require(prepared.identity.packageId == UpdateTrust.PACKAGE_ID) {
            "Approved package identity mismatch"
        }
        require(prepared.identity.signerSha256.equals(UpdateTrust.APK_SIGNING_CERT_SHA256, true)) {
            "Unapproved Android signing certificate"
        }
        require(prepared.manifestSequence > 0L) { "Invalid manifest sequence" }
        val privateDir = File(context.noBackupFilesDir, "vnext-apk-stage").canonicalFile
        require(apk.canonicalFile.parentFile == privateDir &&
            apk.name.startsWith("candidate-") && apk.name.endsWith(".apk")) {
            "APK was not prepared in the private vNext staging directory"
        }
        require(apk.isFile && apk.length() in 1..VNextPrivateApkStage.MAX_APK_BYTES) {
            "Missing or oversized approved APK"
        }
        val size = apk.length()
        val expected = prepared.digestSha256.lowercase()
        // Reject mutations before creating an Android session.
        require(VNextPrivateApkStage.digest(apk).equals(expected, true)) {
            "Prepared APK was modified"
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(UpdateTrust.PACKAGE_ID)
        params.setSize(size)
        val id = installer.createSession(params)
        var success = false
        try {
            installer.openSession(id).use { session ->
                val transmitted = apk.inputStream().use { input ->
                    session.openWrite(ENTRY_NAME, 0L, size).use { output ->
                        val result = transferAndSha256(input, output, size)
                        session.fsync(output)
                        result
                    }
                }
                require(transmitted.equals(expected, true)) {
                    "APK changed while streaming into installer"
                }
                // Read back the exact bytes stored in the Android install session.
                val sessionHash = session.openRead(ENTRY_NAME).use { hashStream(it) }
                require(sessionHash.equals(expected, true)) {
                    "Installer session bytes differ from authorized APK"
                }
            }
            success = true
            return StagedSession(id, expected, UpdateTrust.PACKAGE_ID, prepared.manifestSequence)
        } finally {
            if (!success) {
                // Safe cleanup; original verification error remains primary.
                try { installer.abandonSession(id) } catch (_: Exception) { }
            }
        }
    }

    /** Only transfer the declared size; fail closed on short or extra input. */
    internal fun transferAndSha256(input: InputStream, output: OutputStream, size: Long): String {
        require(size in 1..VNextPrivateApkStage.MAX_APK_BYTES) { "Invalid stream size" }
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0L) {
            val n = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            require(n > 0) { "Unexpected end of APK stream" }
            output.write(buffer, 0, n)
            hash.update(buffer, 0, n)
            remaining -= n.toLong()
        }
        require(input.read() == -1) { "APK stream length changed" }
        return hash.digest().toHexString()
    }

    internal fun hashStream(input: InputStream): String {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n > 0) hash.update(buffer, 0, n)
        }
        return hash.digest().toHexString()
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
