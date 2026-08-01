package com.typezero.siphon.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.typezero.siphon.MainActivity
import com.typezero.siphon.R
import com.typezero.siphon.SiphonApp
import com.typezero.siphon.data.model.AudioFormat
import com.typezero.siphon.data.model.AudioQuality
import com.typezero.siphon.data.model.ExtractRequest
import com.typezero.siphon.data.model.Tags
import com.typezero.siphon.engine.OutputResolver
import kotlinx.coroutines.CancellationException

/** Process-resilient foreground extraction and export job. */
class ExtractionWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val container = (appContext.applicationContext as SiphonApp).container
    private val processId get() = id.toString()

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground("Preparing extraction", -1)

    override suspend fun doWork(): Result {
        createChannel()
        setForeground(foreground("Preparing extraction", -1))
        container.outputResolver.cleanupStaging()

        val request = try {
            decode(inputData)
        } catch (t: Throwable) {
            return Result.failure(outputData("Invalid extraction request: ${t.message}"))
        }
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "Audio extraction" }
        var stagedPath: String? = null

        return try {
            val progress: (Float, String) -> Unit = { value, line ->
                val percent = if (value < 0f) -1 else (value * 100).toInt().coerceIn(0, 100)
                setProgressAsync(Data.Builder()
                    .putInt(KEY_PROGRESS, percent)
                    .putString(KEY_LINE, line.takeLast(400))
                    .build())
                setForegroundAsync(foreground(line.ifBlank { title }, percent))
            }
            val extracted = when (val source = request.source) {
                is ExtractRequest.Source.LocalFile -> container.localExtractor.extract(
                    request, source, container.outputResolver, processId, progress
                )
                is ExtractRequest.Source.Link -> container.engine.extract(
                    request, container.outputResolver, processId, progress
                )
            }
            stagedPath = extracted.outputFile.absolutePath
            setProgress(Data.Builder().putInt(KEY_PROGRESS, 99).putString(KEY_LINE, "Saving to Music/Siphon").build())
            setForeground(foreground("Saving to Music/Siphon", 99))
            val desiredStem = container.outputResolver.sanitize(
                request.outputName,
                when (val source = request.source) {
                    is ExtractRequest.Source.LocalFile -> source.displayName.substringBeforeLast('.')
                    is ExtractRequest.Source.Link -> title
                }
            )
            val exported = container.outputResolver.exportToMusic(
                extracted.outputFile, desiredStem, OutputResolver.mimeFor(extracted.extension)
            )
            if (!extracted.outputFile.delete()) extracted.outputFile.deleteOnExit()
            Result.success(Data.Builder()
                .putString(KEY_OUTPUT_PATH, exported.displayPath)
                .putString(KEY_OUTPUT_URI, exported.uri.toString())
                .putLong(KEY_OUTPUT_BYTES, exported.bytes)
                .build())
        } catch (cancelled: CancellationException) {
            cancelProcesses()
            stagedPath?.let { runCatching { java.io.File(it).delete() } }
            throw cancelled
        } catch (t: Throwable) {
            cancelProcesses()
            Result.failure(Data.Builder()
                .putString(KEY_ERROR, t.message ?: "Extraction failed")
                .putString(KEY_STAGED_PATH, stagedPath)
                .build())
        }
    }

    private fun cancelProcesses() {
        container.engine.cancel(processId)
        container.localExtractor.cancel(processId)
    }

    private fun foreground(text: String, percent: Int): ForegroundInfo {
        val intent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = androidx.work.WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_siphon_notification)
            .setContentTitle("Siphon")
            .setContentText(text.take(100))
            .setContentIntent(intent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }


    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Audio extraction", NotificationManager.IMPORTANCE_LOW
            ))
        }
    }

    private fun decode(data: Data): ExtractRequest {
        val source = when (data.getString(KEY_SOURCE_TYPE)) {
            SOURCE_LOCAL -> ExtractRequest.Source.LocalFile(
                path = data.getString(KEY_SOURCE_PATH),
                uri = requireNotNull(data.getString(KEY_SOURCE_URI)),
                displayName = requireNotNull(data.getString(KEY_SOURCE_NAME)),
                durationMs = data.getLong(KEY_SOURCE_DURATION, 0L)
            )
            SOURCE_LINK -> ExtractRequest.Source.Link(requireNotNull(data.getString(KEY_SOURCE_URL)))
            else -> error("Unknown source type")
        }
        return ExtractRequest(
            source = source,
            format = AudioFormat.fromId(data.getString(KEY_FORMAT).orEmpty()),
            quality = AudioQuality.entries.firstOrNull { it.name == data.getString(KEY_QUALITY) }
                ?: AudioQuality.lossyDefault,
            tags = Tags(
                title = data.getString(KEY_TAG_TITLE).orEmpty(),
                artist = data.getString(KEY_TAG_ARTIST).orEmpty(),
                album = data.getString(KEY_TAG_ALBUM).orEmpty(),
                albumArtist = data.getString(KEY_TAG_ALBUM_ARTIST).orEmpty(),
                genre = data.getString(KEY_TAG_GENRE).orEmpty(),
                year = data.getString(KEY_TAG_YEAR).orEmpty(),
                track = data.getString(KEY_TAG_TRACK).orEmpty(),
                comment = data.getString(KEY_TAG_COMMENT).orEmpty(),
                embedSourceMetadata = data.getBoolean(KEY_EMBED_METADATA, true),
                embedThumbnail = data.getBoolean(KEY_EMBED_THUMBNAIL, false)
            ),
            outputName = data.getString(KEY_OUTPUT_NAME).orEmpty(),
            extractorClient = data.getString(KEY_EXTRACTOR_CLIENT),
            cookiesPath = data.getString(KEY_COOKIES_PATH)
        )
    }

    companion object {
        private const val CHANNEL_ID = "siphon_extraction"
        private const val NOTIFICATION_ID = 7301
        const val UNIQUE_WORK = "siphon-active-extraction"
        const val TAG = "siphon-extraction"

        const val KEY_TITLE = "title"
        const val KEY_PROGRESS = "progress"
        const val KEY_LINE = "line"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_OUTPUT_BYTES = "output_bytes"
        const val KEY_ERROR = "error"
        const val KEY_STAGED_PATH = "staged_path"

        private const val KEY_SOURCE_TYPE = "source_type"
        private const val KEY_SOURCE_PATH = "source_path"
        private const val KEY_SOURCE_URI = "source_uri"
        private const val KEY_SOURCE_NAME = "source_name"
        private const val KEY_SOURCE_DURATION = "source_duration"
        private const val KEY_SOURCE_URL = "source_url"
        private const val KEY_FORMAT = "format"
        private const val KEY_QUALITY = "quality"
        private const val KEY_OUTPUT_NAME = "output_name"
        private const val KEY_EXTRACTOR_CLIENT = "extractor_client"
        private const val KEY_COOKIES_PATH = "cookies_path"
        private const val KEY_TAG_TITLE = "tag_title"
        private const val KEY_TAG_ARTIST = "tag_artist"
        private const val KEY_TAG_ALBUM = "tag_album"
        private const val KEY_TAG_ALBUM_ARTIST = "tag_album_artist"
        private const val KEY_TAG_GENRE = "tag_genre"
        private const val KEY_TAG_YEAR = "tag_year"
        private const val KEY_TAG_TRACK = "tag_track"
        private const val KEY_TAG_COMMENT = "tag_comment"
        private const val KEY_EMBED_METADATA = "embed_metadata"
        private const val KEY_EMBED_THUMBNAIL = "embed_thumbnail"
        private const val SOURCE_LOCAL = "local"
        private const val SOURCE_LINK = "link"

        fun inputData(request: ExtractRequest, title: String): Data {
            val b = Data.Builder()
                .putString(KEY_TITLE, title)
                .putString(KEY_FORMAT, request.format.id)
                .putString(KEY_QUALITY, request.quality.name)
                .putString(KEY_OUTPUT_NAME, request.outputName)
                .putString(KEY_EXTRACTOR_CLIENT, request.extractorClient)
                .putString(KEY_COOKIES_PATH, request.cookiesPath)
                .putString(KEY_TAG_TITLE, request.tags.title)
                .putString(KEY_TAG_ARTIST, request.tags.artist)
                .putString(KEY_TAG_ALBUM, request.tags.album)
                .putString(KEY_TAG_ALBUM_ARTIST, request.tags.albumArtist)
                .putString(KEY_TAG_GENRE, request.tags.genre)
                .putString(KEY_TAG_YEAR, request.tags.year)
                .putString(KEY_TAG_TRACK, request.tags.track)
                .putString(KEY_TAG_COMMENT, request.tags.comment)
                .putBoolean(KEY_EMBED_METADATA, request.tags.embedSourceMetadata)
                .putBoolean(KEY_EMBED_THUMBNAIL, request.tags.embedThumbnail)
            when (val source = request.source) {
                is ExtractRequest.Source.LocalFile -> b
                    .putString(KEY_SOURCE_TYPE, SOURCE_LOCAL)
                    .putString(KEY_SOURCE_PATH, source.path)
                    .putString(KEY_SOURCE_URI, source.uri)
                    .putString(KEY_SOURCE_NAME, source.displayName)
                    .putLong(KEY_SOURCE_DURATION, source.durationMs)
                is ExtractRequest.Source.Link -> b
                    .putString(KEY_SOURCE_TYPE, SOURCE_LINK)
                    .putString(KEY_SOURCE_URL, source.url)
            }
            return b.build()
        }

        private fun outputData(error: String) = Data.Builder().putString(KEY_ERROR, error).build()
    }
}
