package com.typezero.siphon.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typezero.siphon.BuildConfig
import com.typezero.siphon.R
import com.typezero.siphon.data.model.CleanupTarget
import com.typezero.siphon.data.model.JobState
import com.typezero.siphon.data.model.StorageFileInfo
import com.typezero.siphon.data.model.StorageGroupSummary
import com.typezero.siphon.ui.SiphonUiState
import com.typezero.siphon.ui.SiphonViewModel
import com.typezero.siphon.ui.Tab as SourceTab
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiphonRoot(vm: SiphonViewModel, onRequestPermission: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Siphon", fontWeight = FontWeight.Bold) },
                actions = {
                    if (state.extractorUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Storage cleanup")
                                    if (state.storage.totalCount > 0) {
                                        Text(
                                            "${state.storage.totalCount} file(s) • ${formatBytes(state.storage.totalBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                            onClick = { menuOpen = false; vm.openStorageCleanup() }
                        )
                        HorizontalDivider()
                        state.extractorVersion?.let { version ->
                            DropdownMenuItem(
                                text = { Text("yt-dlp $version") },
                                enabled = false,
                                onClick = {}
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Update extractor") },
                            leadingIcon = { Icon(Icons.Default.Update, null) },
                            enabled = !state.extractorUpdating,
                            onClick = { menuOpen = false; vm.updateExtractor(nightly = false) }
                        )
                        DropdownMenuItem(
                            text = { Text("Update extractor (nightly)") },
                            leadingIcon = { Icon(Icons.Default.Bolt, null) },
                            enabled = !state.extractorUpdating,
                            onClick = { menuOpen = false; vm.updateExtractor(nightly = true) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("About Siphon") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuOpen = false; vm.openAbout() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == SourceTab.LOCAL,
                    onClick = { vm.selectTab(SourceTab.LOCAL) },
                    text = { Text("Local video") },
                    icon = { Icon(Icons.Default.VideoLibrary, null) }
                )
                Tab(
                    selected = state.tab == SourceTab.LINK,
                    onClick = { vm.selectTab(SourceTab.LINK) },
                    text = { Text("Link") },
                    icon = { Icon(Icons.Default.Link, null) }
                )
            }

            Box(Modifier.weight(1f)) {
                when (state.tab) {
                    SourceTab.LOCAL -> LocalVideosScreen(state, vm, onRequestPermission)
                    SourceTab.LINK -> LinkScreen(state, vm)
                }
            }

            state.activeJob?.let { ActiveJobBar(it, onCancel = vm::cancelActive) }
            if (state.history.isNotEmpty()) HistorySection(state.history, vm::clearHistory)
        }
    }

    if (state.sheetOpen && state.pendingSource != null) ExtractSheet(state, vm)
    if (state.legacyPromptOpen && !state.storageDialogOpen) {
        LegacyFilesPrompt(state, vm::reviewLegacyFiles, vm::dismissLegacyPrompt)
    }
    if (state.storageDialogOpen) StorageCleanupDialog(state, vm)
    state.cleanupConfirmation?.let { CleanupConfirmationDialog(it, state, vm) }

    if (state.licensesOpen) LicensesDialog(onClose = vm::closeLicenses)
    else if (state.aboutOpen) AboutDialog(state, vm)
}

@Composable
private fun LegacyFilesPrompt(
    state: SiphonUiState,
    onReview: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        icon = { Icon(Icons.Default.FolderDelete, null) },
        title = { Text("Old extraction files found") },
        text = {
            Text(
                "Siphon found ${state.storage.legacy.count} file(s) using " +
                    "${formatBytes(state.storage.legacy.bytes)} in its old private output folder. " +
                    "They may be duplicate copies from earlier versions. Review them before deleting."
            )
        },
        confirmButton = { TextButton(onClick = onReview) { Text("Review files") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } }
    )
}

@Composable
private fun StorageCleanupDialog(state: SiphonUiState, vm: SiphonViewModel) {
    val busy = state.storageLoading || state.cleanupDeleting
    val extractionActive = state.activeJob != null

    Dialog(onDismissRequest = { if (!busy) vm.closeStorageCleanup() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Storage cleanup", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Siphon app-data only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.refreshStorage() }, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (extractionActive) {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )) {
                        Text(
                            "Cleanup is disabled while an extraction is active.",
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                ) {
                    StorageGroup(
                        title = "Legacy extraction files",
                        subtitle = "Files left directly in Android/data/com.typezero.siphon/files/Music/Siphon by older versions.",
                        group = state.storage.legacy,
                        warning = "A failed old export may have left its only copy here. Confirm wanted audio is already in Music/Siphon before deleting.",
                        deleteLabel = "Delete legacy files",
                        deleteEnabled = !busy && !extractionActive && state.storage.legacy.count > 0,
                        onDelete = { vm.requestCleanup(CleanupTarget.LEGACY) }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 18.dp))

                    StorageGroup(
                        title = "Abandoned temporary files",
                        subtitle = "Incomplete staging files older than 24 hours.",
                        group = state.storage.abandonedStaging,
                        warning = null,
                        deleteLabel = "Delete abandoned files",
                        deleteEnabled = !busy && !extractionActive &&
                            state.storage.abandonedStaging.count > 0,
                        onDelete = { vm.requestCleanup(CleanupTarget.ABANDONED_STAGING) }
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = vm::closeStorageCleanup, enabled = !busy) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun StorageGroup(
    title: String,
    subtitle: String,
    group: StorageGroupSummary,
    warning: String?,
    deleteLabel: String,
    deleteEnabled: Boolean,
    onDelete: () -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    Text(
        "${group.count} file(s) • ${formatBytes(group.bytes)}",
        style = MaterialTheme.typography.titleSmall,
        color = if (group.count > 0) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (warning != null && group.count > 0) {
        Spacer(Modifier.height(10.dp))
        Text(
            warning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (group.files.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        group.files.forEach { StorageFileRow(it) }
        if (group.hasMore) {
            Text(
                "Additional files are not shown in this preview.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Spacer(Modifier.height(8.dp))
        Text("Nothing to clean.", style = MaterialTheme.typography.bodyMedium)
    }

    Spacer(Modifier.height(12.dp))
    FilledTonalButton(onClick = onDelete, enabled = deleteEnabled) {
        Icon(Icons.Default.Delete, null)
        Spacer(Modifier.width(8.dp))
        Text(deleteLabel)
    }
}

@Composable
private fun StorageFileRow(file: StorageFileInfo) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium)
        Text(
            "${formatBytes(file.bytes)} • ${formatDate(file.lastModified)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CleanupConfirmationDialog(
    target: CleanupTarget,
    state: SiphonUiState,
    vm: SiphonViewModel
) {
    val legacy = target == CleanupTarget.LEGACY
    val group = if (legacy) state.storage.legacy else state.storage.abandonedStaging
    AlertDialog(
        onDismissRequest = vm::cancelCleanupConfirmation,
        icon = { Icon(Icons.Default.Warning, null) },
        title = { Text(if (legacy) "Delete legacy files?" else "Delete abandoned files?") },
        text = {
            Text(
                "Delete ${group.count} file(s) using ${formatBytes(group.bytes)}? " +
                    if (legacy) "Check Music/Siphon first. This cannot be undone."
                    else "Only temporary files older than 24 hours will be removed. This cannot be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = vm::confirmCleanup) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = vm::cancelCleanupConfirmation) { Text("Cancel") } }
    )
}

@Composable
private fun AboutDialog(state: SiphonUiState, vm: SiphonViewModel) {
    val uriHandler = LocalUriHandler.current
    val channel = if (BuildConfig.VERSION_NAME.contains("-dev")) "Development" else "Stable"

    Dialog(onDismissRequest = vm::closeAbout) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = "Siphon icon",
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Siphon", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold)
                        Text("Created by Typezer∅", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Extract audio from local video files and supported media links, with format, quality, and metadata controls.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(18.dp))
                InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Channel", channel)
                InfoRow("Build type", BuildConfig.BUILD_TYPE)
                InfoRow("Package", BuildConfig.APPLICATION_ID)
                InfoRow("yt-dlp", state.extractorVersion ?: "Loading…")
                InfoRow(
                    "FFmpeg",
                    when {
                        state.componentVersionsLoading -> "Loading…"
                        state.ffmpegVersion != null -> state.ffmpegVersion ?: "Bundled"
                        else -> "Bundled"
                    }
                )

                HorizontalDivider(Modifier.padding(vertical = 18.dp))

                Text("Application updates", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "The signed Siphon APK updater is planned but not enabled in this build. Extractor updates below update yt-dlp only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.SystemUpdate, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Check for app updates — coming later")
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { vm.updateExtractor(nightly = false) },
                    enabled = !state.extractorUpdating && state.activeJob == null
                ) {
                    Icon(Icons.Default.Update, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Update extractor")
                }

                HorizontalDivider(Modifier.padding(vertical = 18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::copyDiagnostics) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy diagnostics")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        uriHandler.openUri(SOURCE_REPOSITORY)
                    }) {
                        Icon(Icons.Default.Code, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Source repository")
                    }
                    TextButton(onClick = vm::openLicenses) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Licenses")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = vm::closeAbout) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun LicensesDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Default.Description, null) },
        title = { Text("Open-source components") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Siphon uses youtubedl-android, yt-dlp, FFmpeg, aria2, AndroidX, Kotlin, and Jetpack Compose. " +
                        "Each component remains subject to its own upstream license and notices."
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "The project repository should retain the corresponding license and notice files when Siphon is distributed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Back") } }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.width(100.dp), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActiveJobBar(job: JobState, onCancel: () -> Unit) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.title, Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            Spacer(Modifier.height(8.dp))
            if (job.progress < 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
            if (job.line.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(job.line, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HistorySection(history: List<JobState>, onClear: () -> Unit) {
    Surface(tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Recent", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onClear) { Text("Clear") }
            }
            LazyColumn(Modifier.heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history, key = { it.id }) { job -> HistoryRow(job) }
            }
        }
    }
}

@Composable
private fun HistoryRow(job: JobState) {
    val (icon, tint) = when (job.status) {
        JobState.Status.DONE -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        JobState.Status.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
        JobState.Status.CANCELLED -> Icons.Default.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon as ImageVector, null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(job.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            val sub = job.error ?: job.outputPath ?: ""
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

private const val SOURCE_REPOSITORY =
    "https://github.com/MikereDD/It-Works-On-My-Machine/tree/main/Android/Siphon"
