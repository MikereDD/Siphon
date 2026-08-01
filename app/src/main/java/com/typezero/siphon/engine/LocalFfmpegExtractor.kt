package com.typezero.siphon.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.typezero.siphon.data.model.AudioFormat
import com.typezero.siphon.data.model.ExtractRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.TimeUnit

/** Extracts audio from a local content URI with the bundled FFmpeg binary. */
class LocalFfmpegExtractor(
    private val appContext: Context,
    private val engine: YtdlpEngine
) {
    private val processes = Collections.synchronizedMap(HashMap<String, Process>())

    private val ffmpegBinary: String
        get() = File(appContext.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath

    private val libraryPath: String
        get() {
            val packages = File(File(appContext.noBackupFilesDir, "youtubedl-android"), "packages")
            return listOf("python", "ffmpeg", "aria2c")
                .joinToString(":") { File(packages, "$it/usr/lib").absolutePath }
        }

    suspend fun extract(
        request: ExtractRequest,
        source: ExtractRequest.Source.LocalFile,
        outputResolver: OutputResolver,
        processId: String,
        onProgress: (Float, String) -> Unit
    ): YtdlpEngine.Result = withContext(Dispatchers.IO) {
        engine.ensureInit()
        resolveInput(source).use { input ->
            try {
                val mime = if (request.format == AudioFormat.COPY) probeAudioMime(input) else null
                val ext = FfmpegArgs.outputExtension(request.format, mime)
                val jobStem = "siphon_${processId.replace("-", "")}"
                val outFile = File(outputResolver.stagingDir(), "$jobStem.$ext")
                val args = FfmpegArgs.build(
                    request.format, request.quality, input.ffmpegPath,
                    outFile.absolutePath, request.tags
                )
                try {
                    runFfmpeg(args, processId, source.durationMs, onProgress)
                } catch (cancelled: CancellationException) {
                    cancel(processId)
                    throw cancelled
                }
                if (!outFile.isFile || outFile.length() == 0L) {
                    throw YtdlpEngine.EngineException("FFmpeg produced no output.")
                }
                YtdlpEngine.Result(outFile, ext)
            } finally {
                processes.remove(processId)
            }
        }
    }


    suspend fun ffmpegVersion(): String? = withContext(Dispatchers.IO) {
        engine.ensureInit()
        runCatching {
            val pb = ProcessBuilder(ffmpegBinary, "-version").redirectErrorStream(true)
            pb.environment().apply {
                put("LD_LIBRARY_PATH", libraryPath)
                put("TMPDIR", appContext.cacheDir.absolutePath)
            }
            val process = pb.start()
            val firstLine = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
            firstLine?.removePrefix("ffmpeg version ")?.substringBefore(' ')
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun cancel(processId: String): Boolean {
        val process = processes[processId] ?: return false
        return runCatching {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
            true
        }.getOrDefault(false)
    }

    private data class InputHandle(
        val ffmpegPath: String,
        val pfd: ParcelFileDescriptor? = null
    ) : Closeable {
        override fun close() { pfd?.close() }
    }

    /** Prefer a readable path; otherwise stream the content URI through /proc/self/fd. */
    private fun resolveInput(source: ExtractRequest.Source.LocalFile): InputHandle {
        source.path?.let(::File)?.takeIf { it.canRead() }?.let {
            return InputHandle(it.absolutePath)
        }
        val pfd = appContext.contentResolver.openFileDescriptor(Uri.parse(source.uri), "r")
            ?: throw YtdlpEngine.EngineException("Could not open the selected video.")
        return InputHandle("/proc/self/fd/${pfd.fd}", pfd)
    }

    private fun probeAudioMime(input: InputHandle): String? = runCatching {
        val extractor = MediaExtractor()
        try {
            if (input.pfd != null) extractor.setDataSource(input.pfd.fileDescriptor)
            else extractor.setDataSource(input.ffmpegPath)
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) return mime
            }
            null
        } finally {
            extractor.release()
        }
    }.getOrNull()

    private suspend fun runFfmpeg(
        args: List<String>,
        processId: String,
        durationMs: Long,
        onProgress: (Float, String) -> Unit
    ) {
        val pb = ProcessBuilder(listOf(ffmpegBinary) + args).redirectErrorStream(true)
        pb.environment().apply {
            put("LD_LIBRARY_PATH", libraryPath)
            put("TMPDIR", appContext.cacheDir.absolutePath)
        }
        Log.i(TAG, "Starting FFmpeg job ${processId.take(8)}")
        val process = pb.start()
        processes[processId] = process
        val coroutineJob = currentCoroutineContext()[Job]

        val tail = ArrayDeque<String>()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            while (true) {
                coroutineJob?.ensureActive()
                val raw = reader.readLine() ?: break
                val line = raw.takeLast(400)
                if (tail.size >= 12) tail.removeFirst()
                tail.addLast(line)
                parseProgress(line, durationMs)?.let { onProgress(it, line) }
            }
        }
        val exit = process.waitFor()
        if (exit != 0) {
            val msg = tail.lastOrNull { it.contains("Error", true) || it.contains("Invalid", true) }
                ?: tail.lastOrNull().orEmpty()
            throw YtdlpEngine.EngineException(
                if (msg.isBlank()) "FFmpeg failed (exit $exit)." else msg.trim()
            )
        }
    }

    private val timeRegex = Regex("""time=(\d+):(\d+):(\d+(?:\.\d+)?)""")
    private fun parseProgress(line: String, durationMs: Long): Float? {
        if (durationMs <= 0) return null
        val m = timeRegex.find(line) ?: return null
        val (h, min, s) = m.destructured
        val sec = h.toLong() * 3600 + min.toLong() * 60 + s.toDouble()
        return (sec * 1000.0 / durationMs).coerceIn(0.0, 1.0).toFloat()
    }

    companion object { private const val TAG = "SiphonLocal" }
}
