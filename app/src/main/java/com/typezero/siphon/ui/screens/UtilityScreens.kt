package com.typezero.siphon.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.typezero.siphon.BuildConfig
import com.typezero.siphon.R
import com.typezero.siphon.data.model.CleanupTarget
import com.typezero.siphon.data.model.StorageFileInfo
import com.typezero.siphon.data.model.StorageGroupSummary
import com.typezero.siphon.ui.SiphonUiState
import com.typezero.siphon.ui.SiphonViewModel
import com.typezero.siphon.ui.components.PremiumBackdrop
import com.typezero.siphon.ui.components.PremiumCard
import com.typezero.siphon.ui.components.PremiumPrimaryButton
import com.typezero.siphon.ui.components.SettingRow
import com.typezero.siphon.ui.components.StatusPill
import com.typezero.siphon.ui.theme.*
import java.text.DateFormat
import java.util.Date

@Composable
fun LegacyFilesPrompt(state: SiphonUiState, onReview: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        containerColor = SiphonSurfaceRaised,
        icon = {
            Surface(shape = RoundedCornerShape(16.dp), color = SiphonPurple.copy(alpha = 0.15f)) {
                Icon(Icons.Default.FolderDelete, null, tint = SiphonPurpleSoft,
                    modifier = Modifier.padding(12.dp).size(26.dp))
            }
        },
        title = { Text("Old extraction files found") },
        text = {
            Text(
                "Siphon found ${state.storage.legacy.count} file(s) using ${formatBytes(state.storage.legacy.bytes)} " +
                    "in its old private output folder. Review them before deleting possible duplicates."
            )
        },
        confirmButton = { Button(onClick = onReview) { Text("Review files") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCleanupScreen(state: SiphonUiState, vm: SiphonViewModel) {
    val busy = state.storageLoading || state.cleanupDeleting
    val extractionActive = state.activeJob != null

    Dialog(
        onDismissRequest = { if (!busy) vm.closeStorageCleanup() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(SiphonBackground)) {
            PremiumBackdrop()
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            actionIconContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        navigationIcon = {
                            IconButton(onClick = vm::closeStorageCleanup, enabled = !busy) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = { Text("Storage cleanup", style = MaterialTheme.typography.headlineSmall) },
                        actions = {
                            IconButton(onClick = { vm.refreshStorage() }, enabled = !busy) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                }
            ) { padding ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    item { StorageOverview(state) }
                    item {
                        PremiumCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, null, tint = SiphonPurpleSoft)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Siphon stores temporary files while extracting. Cleanup only touches Siphon’s private app-data folders; your exported Music/Siphon files are not deleted.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (extractionActive) {
                        item {
                            PremiumCard(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Shield, null, tint = SiphonGreen)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Cleanup is locked while an extraction is active.",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    item {
                        CleanupGroupCard(
                            title = "Legacy extraction files",
                            subtitle = "Old private copies left behind by previous versions after export.",
                            group = state.storage.legacy,
                            warning = "Confirm wanted audio is already present in Music/Siphon before deleting.",
                            button = "Delete legacy files",
                            enabled = !busy && !extractionActive && state.storage.legacy.count > 0,
                            onDelete = { vm.requestCleanup(CleanupTarget.LEGACY) }
                        )
                    }
                    item {
                        CleanupGroupCard(
                            title = "Abandoned staging files",
                            subtitle = "Incomplete temporary files older than 24 hours.",
                            group = state.storage.abandonedStaging,
                            warning = null,
                            button = "Delete abandoned files",
                            enabled = !busy && !extractionActive && state.storage.abandonedStaging.count > 0,
                            onDelete = { vm.requestCleanup(CleanupTarget.ABANDONED_STAGING) }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StorageOverview(state: SiphonUiState) {
    val total = state.storage.totalBytes
    val legacyRatio = if (total <= 0L) 0f else state.storage.legacy.bytes.toFloat() / total.toFloat()
    PremiumCard(Modifier.fillMaxWidth()) {
        Text("Storage overview", style = MaterialTheme.typography.labelLarge, color = SiphonPurpleSoft)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatBytes(state.storage.legacy.bytes), style = MaterialTheme.typography.headlineSmall)
                Text("Legacy files", style = MaterialTheme.typography.bodySmall, color = SiphonTextMuted)
                Spacer(Modifier.height(13.dp))
                Text(formatBytes(state.storage.abandonedStaging.bytes), style = MaterialTheme.typography.headlineSmall)
                Text("Staging files", style = MaterialTheme.typography.bodySmall, color = SiphonTextMuted)
            }
            Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16f
                    drawArc(
                        color = SiphonSurfaceBright,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    if (total > 0L) {
                        drawArc(
                            color = SiphonPurpleBright,
                            startAngle = -90f,
                            sweepAngle = 360f * legacyRatio,
                            useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color(0xFF6C4ED8),
                            startAngle = -90f + 360f * legacyRatio,
                            sweepAngle = 360f * (1f - legacyRatio),
                            useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.storage.totalCount}", style = MaterialTheme.typography.titleLarge)
                    Text("files", style = MaterialTheme.typography.labelSmall, color = SiphonTextMuted)
                }
            }
        }
    }
}

@Composable
private fun CleanupGroupCard(
    title: String,
    subtitle: String,
    group: StorageGroupSummary,
    warning: String?,
    button: String,
    enabled: Boolean,
    onDelete: () -> Unit
) {
    PremiumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill("${group.count} • ${formatBytes(group.bytes)}", SiphonPurpleBright)
        }

        if (warning != null && group.count > 0) {
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = SiphonRed.copy(alpha = 0.08f)) {
                Text(warning, Modifier.padding(11.dp), style = MaterialTheme.typography.bodySmall, color = SiphonRed)
            }
        }

        if (group.files.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            group.files.forEach { StorageFilePreview(it) }
            if (group.hasMore) {
                Text("Additional files are not shown in this preview.",
                    style = MaterialTheme.typography.labelSmall, color = SiphonTextMuted)
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = SiphonGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text("Nothing to clean.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))
        PremiumPrimaryButton(
            text = button,
            onClick = onDelete,
            enabled = enabled,
            icon = Icons.Default.Delete,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StorageFilePreview(file: StorageFileInfo) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AudioFile, null, tint = SiphonPurpleSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text("${formatBytes(file.bytes)} • ${formatDate(file.lastModified)}",
                style = MaterialTheme.typography.labelSmall, color = SiphonTextMuted)
        }
    }
}

@Composable
fun CleanupConfirmationDialog(target: CleanupTarget, state: SiphonUiState, vm: SiphonViewModel) {
    val legacy = target == CleanupTarget.LEGACY
    val group = if (legacy) state.storage.legacy else state.storage.abandonedStaging
    AlertDialog(
        onDismissRequest = vm::cancelCleanupConfirmation,
        containerColor = SiphonSurfaceRaised,
        icon = { Icon(Icons.Default.Warning, null, tint = SiphonRed) },
        title = { Text(if (legacy) "Delete legacy files?" else "Delete abandoned files?") },
        text = {
            Text(
                "Delete ${group.count} file(s) using ${formatBytes(group.bytes)}? " +
                    if (legacy) "Check Music/Siphon first. This cannot be undone."
                    else "Only temporary files older than 24 hours will be removed. This cannot be undone."
            )
        },
        confirmButton = {
            Button(
                onClick = vm::confirmCleanup,
                colors = ButtonDefaults.buttonColors(containerColor = SiphonRed)
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = vm::cancelCleanupConfirmation) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(state: SiphonUiState, vm: SiphonViewModel) {
    val uriHandler = LocalUriHandler.current
    val channel = if (BuildConfig.VERSION_NAME.contains("-dev")) "Development" else "Stable"

    Dialog(
        onDismissRequest = vm::closeAbout,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(SiphonBackground)) {
            PremiumBackdrop()
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            actionIconContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        navigationIcon = {
                            IconButton(onClick = vm::closeAbout) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = { Text("About Siphon", style = MaterialTheme.typography.headlineSmall) }
                    )
                }
            ) { padding ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher),
                            contentDescription = "Siphon icon",
                            modifier = Modifier.size(92.dp)
                        )
                    }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Siphon", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodyMedium, color = SiphonTextMuted)
                            Spacer(Modifier.height(8.dp))
                            StatusPill(channel, SiphonPurpleBright)
                        }
                    }
                    item {
                        Text(
                            "Extract audio from local videos and supported media links with precision, quality controls, and privacy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        PremiumCard(Modifier.fillMaxWidth()) {
                            Text("Components", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            ComponentRow("yt-dlp", state.extractorVersion ?: "Loading…")
                            HorizontalDivider(color = SiphonOutline.copy(alpha = 0.65f))
                            ComponentRow(
                                "FFmpeg",
                                if (state.componentVersionsLoading) "Loading…" else state.ffmpegVersion ?: "Bundled"
                            )
                            HorizontalDivider(color = SiphonOutline.copy(alpha = 0.65f))
                            ComponentRow("Package", BuildConfig.APPLICATION_ID)
                        }
                    }
                    item {
                        PremiumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
                            SettingRow(
                                icon = Icons.Default.SystemUpdate,
                                title = "Check for app updates",
                                subtitle = "Signed APK updater",
                                value = "Coming later",
                                enabled = false,
                                onClick = {}
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                            SettingRow(
                                icon = Icons.Default.Update,
                                title = "Update extractor",
                                subtitle = "Update yt-dlp only",
                                enabled = !state.extractorUpdating && state.activeJob == null,
                                onClick = { vm.updateExtractor(nightly = false) }
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                            SettingRow(
                                icon = Icons.Default.Description,
                                title = "View licenses",
                                onClick = vm::openLicenses
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                            SettingRow(
                                icon = Icons.Default.Code,
                                title = "Open source repository",
                                onClick = { uriHandler.openUri(SOURCE_REPOSITORY) }
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                            SettingRow(
                                icon = Icons.Default.ContentCopy,
                                title = "Copy diagnostics",
                                onClick = vm::copyDiagnostics
                            )
                        }
                    }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Created by Typezer∅", style = MaterialTheme.typography.labelLarge,
                                color = SiphonPurpleSoft)
                            Text("High quality • Private • Powerful", style = MaterialTheme.typography.labelSmall,
                                color = SiphonTextMuted)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(32.dp), shape = CircleShape, color = SiphonPurple.copy(alpha = 0.13f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Memory, null, tint = SiphonPurpleSoft, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelMedium, color = SiphonTextMuted,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LicensesDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = SiphonSurfaceRaised,
        icon = { Icon(Icons.Default.Description, null, tint = SiphonPurpleSoft) },
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

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
