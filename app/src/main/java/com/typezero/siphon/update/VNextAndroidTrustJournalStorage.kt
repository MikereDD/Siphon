package com.typezero.siphon.update

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** Android-backed atomic storage for the two independent recovery-journal records. */
internal class VNextAndroidTrustJournalStorage(context: Context) : VNextTrustJournal.Storage {
    private val folder = File(context.noBackupFilesDir, "vnext-recovery").apply {
        require(isDirectory || mkdirs()) { "Cannot create recovery state directory" }
    }
    private val journal = AtomicFile(File(folder, "journal-v1.json"))
    private val highWater = AtomicFile(File(folder, "high-water-v1.txt"))

    override fun readJournal() = read(journal)
    override fun readHighWater() = read(highWater)
    override fun writeJournal(bytes: ByteArray) = write(journal, bytes)
    override fun writeHighWater(bytes: ByteArray) = write(highWater, bytes)

    private fun read(file: AtomicFile): ByteArray? =
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null
        else file.openRead().use { input ->
            // Bounded by caller's parser, plus a strict read cap before allocating further.
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= 5 * 1024 * 1024) { "Recovery state record too large" }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }

    private fun write(file: AtomicFile, bytes: ByteArray) {
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}
