package com.typezero.siphon.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typezero.siphon.data.model.JobState
import com.typezero.siphon.ui.components.ChipRow
import com.typezero.siphon.ui.components.PremiumCard
import com.typezero.siphon.ui.components.SectionHeading
import com.typezero.siphon.ui.components.StatusPill
import com.typezero.siphon.ui.components.formatSize
import com.typezero.siphon.ui.theme.*
import java.text.DateFormat
import java.util.Date

private enum class HistoryFilter(val label: String) {
    ALL("All"), DONE("Completed"), FAILED("Failed"), CANCELLED("Cancelled")
}

@Composable
fun HistoryScreen(history: List<JobState>, onClear: () -> Unit) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    val filtered = history.filter {
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.DONE -> it.status == JobState.Status.DONE
            HistoryFilter.FAILED -> it.status == JobState.Status.FAILED
            HistoryFilter.CANCELLED -> it.status == JobState.Status.CANCELLED
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeading(
                title = "Recent history",
                subtitle = "Completed, failed, and cancelled extraction jobs.",
                action = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                }
            )
        }
        item {
            ChipRow(
                options = HistoryFilter.entries,
                selected = filter,
                label = { it.label },
                onSelect = { filter = it },
                singleLine = true
            )
        }
        if (filtered.isEmpty()) {
            item { EmptyHistory(filter) }
        } else {
            items(filtered, key = { it.id }) { job -> JobHistoryCard(job) }
        }
    }
}

@Composable
fun LibraryScreen(history: List<JobState>) {
    val completed = history.filter { it.status == JobState.Status.DONE && it.outputUri != null }
    val totalBytes = completed.sumOf { it.outputBytes }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Column {
                            Text("Extraction library", style = MaterialTheme.typography.titleLarge,
                                color = SiphonPurpleSoft)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Completed files known to this Siphon installation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SiphonPurple.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, SiphonPurple.copy(alpha = 0.28f))
                    ) {
                        Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.End) {
                            Text("${completed.size}", style = MaterialTheme.typography.headlineSmall,
                                color = SiphonPurpleSoft)
                            Text(formatSize(totalBytes).ifBlank { "0 B" },
                                style = MaterialTheme.typography.labelSmall, color = SiphonTextMuted)
                        }
                    }
                }
            }
        }
        if (completed.isEmpty()) {
            item {
                PremiumCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LibraryMusic, null, modifier = Modifier.size(42.dp), tint = SiphonPurple)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Your extracted audio will appear here",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(5.dp))
                        Text("Files are stored in Music/Siphon.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(completed, key = { it.id }) { job -> LibraryItem(job) }
        }
    }
}

@Composable
private fun JobHistoryCard(job: JobState) {
    val context = LocalContext.current
    val presentation = statusPresentation(job.status)
    PremiumCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (job.status == JobState.Status.DONE && job.outputUri != null) {
            { openOutput(context, job.outputUri) }
        } else null,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(13.dp),
                color = presentation.color.copy(alpha = 0.13f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(presentation.icon, null, tint = presentation.color, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(job.title, modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    StatusPill(presentation.label, presentation.color)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    listOf(job.formatLabel, job.qualityLabel).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(formatTimestamp(job.createdAt), style = MaterialTheme.typography.labelSmall,
                        color = SiphonTextMuted, modifier = Modifier.weight(1f))
                    if (job.outputBytes > 0) {
                        Text(formatSize(job.outputBytes), style = MaterialTheme.typography.labelSmall,
                            color = SiphonTextMuted)
                    }
                }
                val detail = job.error ?: job.outputPath
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(detail, style = MaterialTheme.typography.bodySmall,
                        color = if (job.status == JobState.Status.FAILED) SiphonRed else SiphonTextMuted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LibraryItem(job: JobState) {
    val context = LocalContext.current
    PremiumCard(
        Modifier.fillMaxWidth(),
        onClick = { job.outputUri?.let { openOutput(context, it) } },
        contentPadding = PaddingValues(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(15.dp),
                color = SiphonPurple.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = SiphonPurpleSoft, modifier = Modifier.size(25.dp))
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(job.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("${job.formatLabel} • ${job.qualityLabel}",
                    style = MaterialTheme.typography.bodySmall, color = SiphonTextMuted)
                Spacer(Modifier.height(4.dp))
                Text(formatSize(job.outputBytes), style = MaterialTheme.typography.labelSmall,
                    color = SiphonTextMuted)
            }
            Icon(Icons.Default.PlayCircle, null, tint = SiphonPurpleBright, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun EmptyHistory(filter: HistoryFilter) {
    PremiumCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.History, null, modifier = Modifier.size(42.dp), tint = SiphonPurple)
            Spacer(Modifier.height(14.dp))
            Text(
                if (filter == HistoryFilter.ALL) "No extraction history yet" else "No ${filter.label.lowercase()} jobs",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(5.dp))
            Text("Start an extraction and its status will be saved here.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class StatusPresentation(val label: String, val icon: ImageVector, val color: Color)

private fun statusPresentation(status: JobState.Status): StatusPresentation = when (status) {
    JobState.Status.DONE -> StatusPresentation("Completed", Icons.Default.CheckCircle, SiphonGreen)
    JobState.Status.FAILED -> StatusPresentation("Failed", Icons.Default.Error, SiphonRed)
    JobState.Status.CANCELLED -> StatusPresentation("Cancelled", Icons.Default.Cancel, SiphonTextMuted)
    JobState.Status.RUNNING -> StatusPresentation("Running", Icons.Default.GraphicEq, SiphonPurpleBright)
    JobState.Status.QUEUED -> StatusPresentation("Queued", Icons.Default.HourglassTop, SiphonPurpleSoft)
}

private fun openOutput(context: Context, outputUri: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(outputUri), "audio/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun formatTimestamp(timestamp: Long): String =
    if (timestamp <= 0L) "Previous session"
    else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
