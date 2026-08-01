package com.typezero.siphon.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.typezero.siphon.data.model.CleanupResult
import com.typezero.siphon.data.model.StorageFileInfo
import com.typezero.siphon.data.model.StorageGroupSummary
import com.typezero.siphon.data.model.StorageSnapshot
import java.io.File
import java.io.IOException

/** Owns Siphon's private staging directory and transactional MediaStore exports. */
class OutputResolver(private val context: Context) {

    data class ExportResult(val uri: Uri, val displayPath: String, val bytes: Long)

    /** App-private Siphon music root used by both legacy output and current staging. */
    private fun siphonRootDir(): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: throw IOException("External app storage is unavailable.")
        val dir = File(root, "Siphon")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Could not create Siphon's private music directory.")
        }
        return dir
    }

    /** Private staging area. Successful files are removed after MediaStore export. */
    fun stagingDir(): File {
        val dir = File(siphonRootDir(), ".staging")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Could not create Siphon's staging directory.")
        }
        return dir
    }

    /**
     * Scan only Siphon's app-private directories. Legacy files are regular files stored
     * directly in Music/Siphon by versions before 0.3-dev.1. Current jobs use .staging.
     */
    fun scanCleanup(
        olderThanMs: Long = ABANDONED_AFTER_MS,
        previewLimit: Int = 100
    ): StorageSnapshot {
        val root = siphonRootDir()
        val legacyFiles = root.listFiles().orEmpty()
            .filter { it.isFile && isDirectChild(it, root) }
            .sortedByDescending { it.lastModified() }
        val cutoff = System.currentTimeMillis() - olderThanMs
        val staging = stagingDir()
        val abandoned = staging.listFiles().orEmpty()
            .filter { it.isFile && isDirectChild(it, staging) && it.lastModified() < cutoff }
            .sortedByDescending { it.lastModified() }
        return StorageSnapshot(
            legacy = summary(legacyFiles, previewLimit),
            abandonedStaging = summary(abandoned, previewLimit)
        )
    }

    /** Automatic maintenance used before extraction; never touches legacy files. */
    fun cleanupStaging(olderThanMs: Long = ABANDONED_AFTER_MS): CleanupResult =
        deleteCandidates(abandonedStagingFiles(olderThanMs))

    /** Delete files left directly in the old app-private output directory. */
    fun deleteLegacyFiles(): CleanupResult {
        val root = siphonRootDir()
        val files = root.listFiles().orEmpty().filter { it.isFile && isDirectChild(it, root) }
        return deleteCandidates(files)
    }

    /** Delete only staging files old enough that they cannot be a newly started job. */
    fun deleteAbandonedStaging(olderThanMs: Long = ABANDONED_AFTER_MS): CleanupResult =
        deleteCandidates(abandonedStagingFiles(olderThanMs))

    private fun abandonedStagingFiles(olderThanMs: Long): List<File> {
        val cutoff = System.currentTimeMillis() - olderThanMs
        val staging = stagingDir()
        return staging.listFiles().orEmpty().filter {
            it.isFile && isDirectChild(it, staging) && it.lastModified() < cutoff
        }
    }

    private fun deleteCandidates(files: List<File>): CleanupResult {
        var deletedCount = 0
        var deletedBytes = 0L
        var failedCount = 0
        files.forEach { file ->
            val bytes = file.length().coerceAtLeast(0L)
            if (runCatching { file.delete() }.getOrDefault(false)) {
                deletedCount++
                deletedBytes += bytes
            } else {
                failedCount++
            }
        }
        return CleanupResult(deletedCount, deletedBytes, failedCount)
    }

    private fun summary(files: List<File>, previewLimit: Int): StorageGroupSummary =
        StorageGroupSummary(
            count = files.size,
            bytes = files.sumOf { it.length().coerceAtLeast(0L) },
            files = files.take(previewLimit).map {
                StorageFileInfo(it.name, it.length().coerceAtLeast(0L), it.lastModified())
            },
            hasMore = files.size > previewLimit
        )

    private fun isDirectChild(file: File, parent: File): Boolean = runCatching {
        file.parentFile?.canonicalFile == parent.canonicalFile
    }.getOrDefault(false)

    /** Sanitise a user-entered name into a safe, non-reserved file stem. */
    fun sanitize(name: String, fallback: String): String {
        val source = name.trim().ifBlank { fallback.trim() }.ifBlank { "audio" }
        val cleaned = source
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
            .take(120)
        val reserved = setOf("CON", "PRN", "AUX", "NUL") +
            (1..9).flatMap { listOf("COM$it", "LPT$it") }
        return when {
            cleaned.isBlank() -> "audio"
            cleaned.uppercase() in reserved -> "_$cleaned"
            else -> cleaned
        }
    }

    /** Find the unique staged file produced for a job. */
    fun locateStaged(jobStem: String): File? =
        stagingDir().listFiles()
            ?.filter { it.isFile && it.nameWithoutExtension == jobStem && it.length() > 0L }
            ?.maxByOrNull { it.lastModified() }

    /**
     * Copy a staged file into Music/Siphon transactionally.
     * Any inserted MediaStore row is deleted if copying or finalisation fails.
     */
    fun exportToMusic(file: File, desiredStem: String, mime: String): ExportResult {
        require(file.isFile && file.length() > 0L) { "The extracted file is missing or empty." }

        val resolver = context.contentResolver
        val displayName = "${sanitize(desiredStem, file.nameWithoutExtension)}.${file.extension}"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Siphon")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                val legacyDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Siphon"
                )
                if (!legacyDir.exists() && !legacyDir.mkdirs()) {
                    throw IOException("Could not create Music/Siphon.")
                }
                put(MediaStore.Audio.Media.DATA, uniqueLegacyFile(legacyDir, displayName).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values)
            ?: throw IOException("Android could not create the Music library entry.")
        try {
            val copied = resolver.openOutputStream(uri, "w")?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IOException("Android could not open the destination file.")
            if (copied != file.length()) {
                throw IOException("The exported file was incomplete ($copied of ${file.length()} bytes).")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finished = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                if (resolver.update(uri, finished, null, null) <= 0) {
                    throw IOException("Android could not finalise the Music library entry.")
                }
            }
            return ExportResult(uri, "Music/Siphon/$displayName", copied)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun uniqueLegacyFile(dir: File, displayName: String): File {
        val original = File(dir, displayName)
        if (!original.exists()) return original
        val stem = original.nameWithoutExtension
        val ext = original.extension.let { if (it.isBlank()) "" else ".$it" }
        var index = 2
        while (true) {
            val candidate = File(dir, "$stem ($index)$ext")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    companion object {
        const val ABANDONED_AFTER_MS = 24L * 60L * 60L * 1000L

        fun mimeFor(ext: String): String = when (ext.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "opus" -> "audio/opus"
            "flac" -> "audio/flac"
            "wav" -> "audio/x-wav"
            "ogg" -> "audio/ogg"
            "mka" -> "audio/x-matroska"
            "ac3" -> "audio/ac3"
            "eac3" -> "audio/eac3"
            else -> "audio/*"
        }
    }
}
