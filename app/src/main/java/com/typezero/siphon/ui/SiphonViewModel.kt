package com.typezero.siphon.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import com.typezero.siphon.BuildConfig
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.typezero.siphon.data.model.*
import com.typezero.siphon.di.AppContainer
import com.typezero.siphon.work.ExtractionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { LOCAL, LINK }

data class SiphonUiState(
    val tab: Tab = Tab.LOCAL,
    val hasMediaPermission: Boolean = false,
    val videosLoading: Boolean = false,
    val allVideos: List<VideoItem> = emptyList(),
    val searchQuery: String = "",
    val linkUrl: String = "",
    val linkClient: YouTubeClient = YouTubeClient.DEFAULT,
    val cookiesLoaded: Boolean = false,
    val extractorVersion: String? = null,
    val sheetOpen: Boolean = false,
    val pendingSource: ExtractRequest.Source? = null,
    val format: AudioFormat = AudioFormat.COPY,
    val quality: AudioQuality = AudioQuality.lossyDefault,
    val tags: Tags = Tags(),
    val outputName: String = "",
    val activeJob: JobState? = null,
    val history: List<JobState> = emptyList(),
    val extractorUpdating: Boolean = false,
    val storage: StorageSnapshot = StorageSnapshot(),
    val storageLoading: Boolean = false,
    val storageDialogOpen: Boolean = false,
    val legacyPromptOpen: Boolean = false,
    val cleanupConfirmation: CleanupTarget? = null,
    val cleanupDeleting: Boolean = false,
    val aboutOpen: Boolean = false,
    val licensesOpen: Boolean = false,
    val ffmpegVersion: String? = null,
    val componentVersionsLoading: Boolean = false,
    val snackbar: String? = null
) {
    val visibleVideos: List<VideoItem>
        get() = if (searchQuery.isBlank()) allVideos
        else allVideos.filter { it.displayName.contains(searchQuery.trim(), ignoreCase = true) }
}

class SiphonViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SiphonUiState())
    val state: StateFlow<SiphonUiState> = _state.asStateFlow()
    private val workManager = WorkManager.getInstance(container.appContext)
    private val prefs = container.appContext.getSharedPreferences("jobs", 0)
    private val cleanupPrefs = container.appContext.getSharedPreferences("storage_cleanup", 0)

    private val workLiveData = workManager.getWorkInfosForUniqueWorkLiveData(ExtractionWorker.UNIQUE_WORK)
    private val workObserver = Observer<List<WorkInfo>> { infos -> syncWork(infos.orEmpty()) }

    init {
        _state.update { it.copy(cookiesLoaded = container.cookieStore.hasCookies()) }
        workLiveData.observeForever(workObserver)
        viewModelScope.launch(Dispatchers.IO) {
            val version = runCatching {
                container.engine.ensureInit()
                container.engine.extractorVersion()
            }.getOrNull()
            _state.update { it.copy(extractorVersion = version) }
        }
        refreshStorage(showLegacyPrompt = true)
    }

    fun selectTab(tab: Tab) = _state.update { it.copy(tab = tab) }
    fun consumeRequestedTab() = _state.update { it.copy(tab = Tab.LOCAL) }
    fun setSearch(q: String) = _state.update { it.copy(searchQuery = q) }
    fun setLinkUrl(url: String) = _state.update { it.copy(linkUrl = url) }
    fun setLinkClient(client: YouTubeClient) = _state.update { it.copy(linkClient = client) }

    fun importCookies(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { container.cookieStore.importFrom(uri) }
            _state.update {
                it.copy(
                    cookiesLoaded = container.cookieStore.hasCookies(),
                    snackbar = if (ok) "Cookies loaded securely" else
                        "Invalid or oversized cookies.txt file"
                )
            }
        }
    }

    fun clearCookies() {
        container.cookieStore.clear()
        _state.update { it.copy(cookiesLoaded = false, snackbar = "Cookies removed") }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasMediaPermission = granted) }
        if (granted) loadVideos()
    }

    fun updateExtractor(nightly: Boolean) {
        if (_state.value.extractorUpdating || _state.value.activeJob != null) return
        _state.update { it.copy(extractorUpdating = true) }
        viewModelScope.launch {
            val msg = try {
                container.engine.updateBinary(nightly)
            } catch (e: Exception) {
                e.message ?: "Update failed"
            }
            _state.update {
                it.copy(
                    extractorUpdating = false,
                    extractorVersion = container.engine.extractorVersion(),
                    snackbar = msg
                )
            }
        }
    }

    fun openAbout() {
        _state.update { it.copy(aboutOpen = true) }
        if (_state.value.ffmpegVersion == null && !_state.value.componentVersionsLoading) {
            _state.update { it.copy(componentVersionsLoading = true) }
            viewModelScope.launch(Dispatchers.IO) {
                val version = container.localExtractor.ffmpegVersion()
                _state.update { it.copy(ffmpegVersion = version, componentVersionsLoading = false) }
            }
        }
    }

    fun closeAbout() = _state.update { it.copy(aboutOpen = false) }
    fun openLicenses() = _state.update { it.copy(licensesOpen = true) }
    fun closeLicenses() = _state.update { it.copy(licensesOpen = false) }

    fun copyDiagnostics() {
        val s = _state.value
        val channel = if (BuildConfig.VERSION_NAME.contains("-dev")) "Development" else "Stable"
        val text = buildString {
            appendLine("Siphon diagnostics")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine("Channel: $channel")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("yt-dlp: ${s.extractorVersion ?: "unknown"}")
            appendLine("FFmpeg: ${s.ffmpegVersion ?: "unknown"}")
        }.trim()
        val clipboard = container.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Siphon diagnostics", text))
        _state.update { it.copy(snackbar = "Diagnostics copied") }
    }

    fun openStorageCleanup() {
        cleanupPrefs.edit().putBoolean(KEY_LEGACY_PROMPT_HANDLED, true).apply()
        _state.update { it.copy(storageDialogOpen = true, legacyPromptOpen = false) }
        refreshStorage(showLegacyPrompt = false)
    }

    fun closeStorageCleanup() = _state.update {
        it.copy(storageDialogOpen = false, cleanupConfirmation = null)
    }

    fun dismissLegacyPrompt() {
        cleanupPrefs.edit().putBoolean(KEY_LEGACY_PROMPT_HANDLED, true).apply()
        _state.update { it.copy(legacyPromptOpen = false) }
    }

    fun reviewLegacyFiles() {
        cleanupPrefs.edit().putBoolean(KEY_LEGACY_PROMPT_HANDLED, true).apply()
        _state.update { it.copy(legacyPromptOpen = false, storageDialogOpen = true) }
        refreshStorage(showLegacyPrompt = false)
    }

    fun requestCleanup(target: CleanupTarget) {
        if (_state.value.activeJob != null) {
            _state.update { it.copy(snackbar = "Finish or cancel the active extraction before cleaning storage") }
            return
        }
        _state.update { it.copy(cleanupConfirmation = target) }
    }

    fun cancelCleanupConfirmation() = _state.update { it.copy(cleanupConfirmation = null) }

    fun confirmCleanup() {
        val target = _state.value.cleanupConfirmation ?: return
        if (_state.value.activeJob != null || _state.value.cleanupDeleting) return
        _state.update { it.copy(cleanupDeleting = true, cleanupConfirmation = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (target) {
                    CleanupTarget.LEGACY -> container.outputResolver.deleteLegacyFiles()
                    CleanupTarget.ABANDONED_STAGING -> container.outputResolver.deleteAbandonedStaging()
                }
            }
            if (target == CleanupTarget.LEGACY) {
                cleanupPrefs.edit().putBoolean(KEY_LEGACY_PROMPT_HANDLED, true).apply()
            }
            val refreshed = withContext(Dispatchers.IO) {
                runCatching { container.outputResolver.scanCleanup() }.getOrDefault(StorageSnapshot())
            }
            val message = buildString {
                append("Deleted ${result.deletedCount} file")
                if (result.deletedCount != 1) append('s')
                if (result.deletedBytes > 0) append(" (${humanBytes(result.deletedBytes)})")
                if (result.failedCount > 0) append("; ${result.failedCount} could not be deleted")
            }
            _state.update {
                it.copy(storage = refreshed, cleanupDeleting = false, snackbar = message)
            }
        }
    }

    fun refreshStorage(showLegacyPrompt: Boolean = false) {
        if (_state.value.storageLoading) return
        _state.update { it.copy(storageLoading = true) }
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                runCatching { container.outputResolver.scanCleanup() }.getOrDefault(StorageSnapshot())
            }
            val shouldPrompt = showLegacyPrompt && snapshot.legacy.count > 0 &&
                !cleanupPrefs.getBoolean(KEY_LEGACY_PROMPT_HANDLED, false)
            _state.update {
                it.copy(storage = snapshot, storageLoading = false, legacyPromptOpen = shouldPrompt)
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null) }

    fun loadVideos() {
        _state.update { it.copy(videosLoading = true) }
        viewModelScope.launch {
            val vids = runCatching { container.videoRepository.loadVideos() }.getOrDefault(emptyList())
            _state.update { it.copy(allVideos = vids, videosLoading = false) }
        }
    }

    fun openSheetForVideo(video: VideoItem) {
        if (_state.value.activeJob != null) {
            _state.update { it.copy(snackbar = "Finish or cancel the active extraction first") }
            return
        }
        _state.update {
            it.copy(
                sheetOpen = true,
                pendingSource = ExtractRequest.Source.LocalFile(
                    path = video.filePath,
                    uri = video.uri.toString(),
                    displayName = video.displayName,
                    durationMs = video.durationMs
                ),
                outputName = video.displayName.substringBeforeLast('.'),
                tags = Tags(embedSourceMetadata = false, embedThumbnail = false)
            )
        }
    }

    fun openSheetForLink() {
        if (_state.value.activeJob != null) {
            _state.update { it.copy(snackbar = "Finish or cancel the active extraction first") }
            return
        }
        val url = _state.value.linkUrl.trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            _state.update { it.copy(snackbar = "Enter a complete http:// or https:// link") }
            return
        }
        _state.update {
            it.copy(
                sheetOpen = true,
                pendingSource = ExtractRequest.Source.Link(url),
                outputName = "",
                tags = Tags(embedSourceMetadata = true, embedThumbnail = true)
            )
        }
    }

    fun closeSheet() = _state.update { it.copy(sheetOpen = false, pendingSource = null) }
    fun setFormat(f: AudioFormat) = _state.update { it.copy(format = f) }
    fun setQuality(q: AudioQuality) = _state.update { it.copy(quality = q) }
    fun setOutputName(n: String) = _state.update { it.copy(outputName = n) }
    fun updateTags(transform: (Tags) -> Tags) = _state.update { it.copy(tags = transform(it.tags)) }
    fun resetTags() = _state.update {
        it.copy(tags = Tags(embedSourceMetadata = it.pendingSource is ExtractRequest.Source.Link,
            embedThumbnail = it.pendingSource is ExtractRequest.Source.Link))
    }

    fun startExtraction() {
        val s = _state.value
        if (s.activeJob != null) return
        val source = s.pendingSource ?: return
        val isLink = source is ExtractRequest.Source.Link
        val request = ExtractRequest(
            source = source,
            format = s.format,
            quality = s.quality,
            tags = s.tags,
            outputName = s.outputName,
            extractorClient = if (isLink) s.linkClient.value else null,
            cookiesPath = if (isLink) container.cookieStore.path() else null
        )
        val title = when (source) {
            is ExtractRequest.Source.LocalFile -> source.displayName
            is ExtractRequest.Source.Link -> s.outputName.ifBlank { source.url }
        }
        val work = OneTimeWorkRequestBuilder<ExtractionWorker>()
            .setInputData(ExtractionWorker.inputData(request, title))
            .addTag(ExtractionWorker.TAG)
            .build()
        prefs.edit()
            .putString("title_${work.id}", title)
            .putString("format_${work.id}", s.format.label)
            .putString(
                "quality_${work.id}",
                if (s.format.lossy) s.quality.label else if (s.format == AudioFormat.COPY) "Original stream" else "Lossless"
            )
            .putString(
                "source_${work.id}",
                if (source is ExtractRequest.Source.LocalFile) "Local video" else "Link / URL"
            )
            .putLong("created_${work.id}", System.currentTimeMillis())
            .apply()
        workManager.enqueueUniqueWork(
            ExtractionWorker.UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            work
        )
        _state.update { it.copy(sheetOpen = false, pendingSource = null) }
    }

    fun cancelActive() {
        val active = _state.value.activeJob ?: return
        workManager.cancelWorkById(java.util.UUID.fromString(active.id))
    }

    fun clearHistory() {
        val edit = prefs.edit()
        _state.value.history.forEach { job ->
            edit.remove("title_${job.id}")
            edit.remove("format_${job.id}")
            edit.remove("quality_${job.id}")
            edit.remove("source_${job.id}")
            edit.remove("created_${job.id}")
        }
        edit.apply()
        workManager.pruneWork()
        _state.update { it.copy(history = emptyList()) }
    }

    private fun syncWork(infos: List<WorkInfo>) {
        val jobs = infos.map { info ->
            val title = prefs.getString("title_${info.id}", null) ?: "Audio extraction"
            val status = when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> JobState.Status.QUEUED
                WorkInfo.State.RUNNING -> JobState.Status.RUNNING
                WorkInfo.State.SUCCEEDED -> JobState.Status.DONE
                WorkInfo.State.FAILED -> JobState.Status.FAILED
                WorkInfo.State.CANCELLED -> JobState.Status.CANCELLED
            }
            val p = info.progress.getInt(ExtractionWorker.KEY_PROGRESS, -1)
            JobState(
                id = info.id.toString(),
                title = title,
                status = status,
                progress = if (p < 0) -1f else p / 100f,
                line = info.progress.getString(ExtractionWorker.KEY_LINE).orEmpty(),
                outputPath = info.outputData.getString(ExtractionWorker.KEY_OUTPUT_PATH),
                outputUri = info.outputData.getString(ExtractionWorker.KEY_OUTPUT_URI),
                outputBytes = info.outputData.getLong(ExtractionWorker.KEY_OUTPUT_BYTES, 0L),
                error = info.outputData.getString(ExtractionWorker.KEY_ERROR),
                formatLabel = prefs.getString("format_${info.id}", null) ?: "Audio",
                qualityLabel = prefs.getString("quality_${info.id}", null) ?: "Best available",
                sourceLabel = prefs.getString("source_${info.id}", null) ?: "Media",
                createdAt = prefs.getLong("created_${info.id}", 0L)
            )
        }.sortedByDescending { it.createdAt }
        val active = jobs.firstOrNull { it.status == JobState.Status.RUNNING || it.status == JobState.Status.QUEUED }
        val history = jobs.filter { it.status !in setOf(JobState.Status.RUNNING, JobState.Status.QUEUED) }.take(30)
        _state.update { it.copy(activeJob = active, history = history) }
    }

    override fun onCleared() {
        workLiveData.removeObserver(workObserver)
        super.onCleared()
    }

    private fun humanBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return "%.1f %s".format(value, units[unit])
    }

    companion object {
        private const val KEY_LEGACY_PROMPT_HANDLED = "legacy_prompt_handled_v1"
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SiphonViewModel(container) as T
    }
}
