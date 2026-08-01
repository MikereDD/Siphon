package com.typezero.siphon.engine

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/** Stores an optional, size-limited Netscape cookies.txt in private app storage. */
class CookieStore(private val appContext: Context) {

    private val file: File get() = File(appContext.filesDir, "cookies.txt")

    fun hasCookies(): Boolean = file.isFile && file.length() in 1..MAX_BYTES
    fun path(): String? = if (hasCookies()) file.absolutePath else null

    fun importFrom(uri: Uri): Boolean {
        val temp = File(appContext.cacheDir, "cookies-import.tmp")
        return try {
            var total = 0L
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return false
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return false
            if (!looksLikeCookies(temp)) return false
            if (file.exists() && !file.delete()) return false
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
            true
        } finally {
            temp.delete()
        }
    }

    fun clear() { file.delete() }

    private fun looksLikeCookies(candidate: File): Boolean {
        if (!candidate.isFile || candidate.length() !in 1..MAX_BYTES) return false
        var header = false
        var validRows = 0
        BufferedReader(InputStreamReader(candidate.inputStream(), Charsets.UTF_8)).useLines { lines ->
            lines.take(MAX_LINES).forEach { raw ->
                val line = raw.trimEnd()
                if (line.contains("# Netscape", true) || line.contains("# HTTP Cookie", true)) {
                    header = true
                }
                if (line.isNotBlank() && !line.startsWith("#")) {
                    val fields = line.split('\t')
                    if (fields.size == 7 && fields[0].isNotBlank() && fields[5].isNotBlank()) validRows++
                }
            }
        }
        return header || validRows > 0
    }

    companion object {
        private const val MAX_BYTES = 2L * 1024L * 1024L
        private const val MAX_LINES = 50_000
    }
}
