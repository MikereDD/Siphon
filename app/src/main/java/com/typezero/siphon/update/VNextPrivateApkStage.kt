package com.typezero.siphon.update

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Phase 4E.1: isolated private staging and fresh authorization of the staged bytes.
 * Not an installer. In particular a returned File is NOT a transferable grant of
 * authority: the eventual installer must stream these same approved bytes into a
 * PackageInstaller session and let Android verify the APK's signing block.
 */
internal class VNextPrivateApkStage(
    private val verifier: VNextApkIntegrityGate = VNextApkIntegrityGate()
) {
    data class Prepared(
        val file: File,
        val digestSha256: String,
        val identity: VNextAndroidApkIdentityGate.ApprovedIdentity,
        val manifestSequence: Long
    )

    fun prepare(
        context: Context,
        downloadedApk: File,
        detachedSignature: File,
        manifestBytes: ByteArray,
        manifestSignature: ByteArray,
        policy: VNextTrustPolicy,
        channel: UpdateChannel,
        assetName: String
    ): Prepared {
        val dir = File(context.noBackupFilesDir, "vnext-apk-stage")
        require(dir.isDirectory || dir.mkdirs()) { "Cannot create private vNext staging directory" }
        val target = File(dir, "candidate-${UUID.randomUUID()}.apk")
        try {
            copyBounded(downloadedApk, target)
            // Reauthenticate signed metadata and exact STAGED bytes, not the download.
            val eligible = verifier.verify(
                manifestBytes, manifestSignature, policy, channel, assetName,
                target, detachedSignature
            )
            val identity = VNextAndroidApkIdentityGate.inspect(context, target, eligible)
            require(digest(target).equals(eligible.apkSha256, true)) {
                "Staged APK changed during identity inspection"
            }
            return Prepared(target, eligible.apkSha256, identity, eligible.manifestSequence)
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    companion object {
        // The checked file must be copied rather than moved, linked or opened via a
        // mutable download path. This cap avoids unbounded staging allocations.
        internal const val MAX_APK_BYTES = 1024L * 1024L * 1024L

        internal fun copyBounded(source: File, target: File) {
            require(source.isFile && source.length() > 0L && source.length() <= MAX_APK_BYTES) {
                "Invalid candidate APK size"
            }
            require(!target.exists()) { "Staging target already exists" }
            try {
                source.inputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        var copied = 0L
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            require(copied <= MAX_APK_BYTES) { "APK exceeded staging limit" }
                            output.write(buffer, 0, count)
                        }
                        require(copied > 0) { "Empty APK" }
                        output.fd.sync()
                    }
                }
            } catch (t: Throwable) {
                target.delete()
                throw t
            }
        }

        internal fun digest(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
