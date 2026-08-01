package com.typezero.siphon.engine

import android.content.Context
import android.util.Log
import com.typezero.siphon.data.model.ExtractRequest
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** URL extraction wrapper around youtubedl-android. */
class YtdlpEngine(private val appContext: Context) {

    @Volatile private var ready = false
    private val initMutex = Mutex()

    suspend fun ensureInit() = withContext(Dispatchers.IO) {
        if (ready) return@withContext
        initMutex.withLock {
            if (ready) return@withLock
            try {
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
                ready = true
            } catch (e: Exception) {
                Log.e(TAG, "Extractor initialisation failed", e)
                throw EngineException("Failed to initialise extractor: ${e.message}", e)
            }
        }
    }

    fun extractorVersion(): String? =
        runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()

    suspend fun updateBinary(nightly: Boolean): String = withContext(Dispatchers.IO) {
        ensureInit()
        val channel = if (nightly) YoutubeDL.UpdateChannel.NIGHTLY else YoutubeDL.UpdateChannel.STABLE
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, channel)
            val ver = extractorVersion()?.let { " ($it)" } ?: ""
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> "Extractor updated$ver"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Extractor already up to date$ver"
                else -> "Update finished$ver"
            }
        } catch (e: Exception) {
            throw EngineException("Update failed: ${e.message}", e)
        }
    }

    data class Result(val outputFile: File, val extension: String)

    suspend fun extract(
        request: ExtractRequest,
        outputResolver: OutputResolver,
        processId: String,
        onProgress: (Float, String) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        ensureInit()
        val coroutineJob = currentCoroutineContext()[Job]
        val jobStem = "siphon_${processId.replace("-", "")}"
        val args = CommandFactory.build(request, outputResolver.stagingDir().absolutePath, jobStem)
        val token = CommandFactory.sourceToken(request)
        val ytRequest = YoutubeDLRequest(token).addCommands(args)

        Log.i(TAG, "Starting yt-dlp job ${processId.take(8)} (${request.format.id})")
        try {
            YoutubeDL.getInstance().execute(ytRequest, processId) { progress, _, line ->
                coroutineJob?.ensureActive()
                onProgress(if (progress < 0) -1f else progress / 100f, redact(line))
            }
        } catch (cancelled: CancellationException) {
            cancel(processId)
            throw cancelled
        } catch (e: Exception) {
            throw EngineException(friendly(e.message), e)
        }

        val produced = outputResolver.locateStaged(jobStem)
            ?: throw EngineException("Extraction finished but no output file was found.")
        Result(produced, produced.extension)
    }

    fun cancel(processId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    private fun redact(line: String): String = line
        .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[source]")
        .replace(Regex("--cookies\\s+\\S+", RegexOption.IGNORE_CASE), "--cookies [private]")
        .takeLast(400)

    private fun friendly(raw: String?): String {
        val msg = raw ?: "Unknown error"
        return when {
            msg.contains("Unsupported URL", true) -> "That link isn't supported."
            msg.contains("Video unavailable", true) -> "The source is unavailable or private."
            msg.contains("No such file", true) -> "The selected file could not be read."
            msg.contains("Requested format", true) -> "No matching audio stream for that format."
            msg.contains("Sign in to confirm", true) -> "The site requires sign-in cookies for this source."
            else -> redact(msg.lineSequence().lastOrNull { it.isNotBlank() } ?: msg)
        }
    }

    class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object { private const val TAG = "SiphonEngine" }
}
