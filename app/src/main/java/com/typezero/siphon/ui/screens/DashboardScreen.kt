package com.typezero.siphon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typezero.siphon.data.model.AudioFormat
import com.typezero.siphon.data.model.AudioQuality
import com.typezero.siphon.data.model.JobState
import com.typezero.siphon.ui.SiphonUiState
import com.typezero.siphon.ui.SiphonViewModel
import com.typezero.siphon.ui.components.*
import com.typezero.siphon.ui.theme.*

@Composable
fun ExtractDashboardScreen(
    state: SiphonUiState,
    vm: SiphonViewModel,
    onLocal: () -> Unit,
    onLink: () -> Unit,
    onOpenActive: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Text("New extraction", style = MaterialTheme.typography.titleLarge, color = SiphonPurpleSoft)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Choose where the audio is coming from.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                SourceChoiceCard(
                    icon = Icons.Default.FolderOpen,
                    title = "Local video",
                    subtitle = "Extract audio from a video stored on this device.",
                    onClick = onLocal,
                    trailingIcon = Icons.Default.ChevronRight
                )
                Spacer(Modifier.height(12.dp))
                SourceChoiceCard(
                    icon = Icons.Default.Link,
                    title = "Link / URL",
                    subtitle = "Extract audio from supported URLs and playlists.",
                    onClick = onLink,
                    trailingIcon = Icons.Default.ChevronRight
                )
            }
        }

        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                SectionHeading(
                    title = "Quick settings",
                    subtitle = "These become the defaults when you choose a source."
                )
                Spacer(Modifier.height(14.dp))
                QuickSettingRow(
                    icon = Icons.Default.AudioFile,
                    label = "Format",
                    value = shortFormatLabel(state.format),
                    options = AudioFormat.entries,
                    optionLabel = { shortFormatLabel(it) },
                    onSelect = vm::setFormat
                )
                HorizontalDivider(color = SiphonOutline.copy(alpha = 0.65f))
                QuickSettingRow(
                    icon = Icons.Default.HighQuality,
                    label = "Quality",
                    value = if (state.format.lossy) state.quality.label else if (state.format == AudioFormat.COPY) "Original" else "Lossless",
                    options = AudioQuality.entries,
                    optionLabel = { it.label },
                    enabled = state.format.lossy,
                    onSelect = vm::setQuality
                )
                HorizontalDivider(color = SiphonOutline.copy(alpha = 0.65f))
                StaticSettingRow(
                    icon = Icons.Default.AutoAwesome,
                    label = "Metadata",
                    value = "Auto"
                )
            }
        }

        item {
            if (state.activeJob != null) {
                ActiveDashboardCard(requireNotNull(state.activeJob), onOpenActive)
            } else {
                PremiumCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                                .background(SiphonSurfaceBright),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GraphicEq, null, tint = SiphonTextMuted)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column {
                            Text("Active job", style = MaterialTheme.typography.titleMedium, color = SiphonPurpleSoft)
                            Text(
                                "No active extractions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrustCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Shield,
                    title = "Reliable",
                    subtitle = "Foreground jobs keep long extractions running."
                )
                TrustCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Lock,
                    title = "Private",
                    subtitle = "Your media and cookies remain on your device."
                )
            }
        }
    }
}

@Composable
private fun ActiveDashboardCard(job: JobState, onClick: () -> Unit) {
    PremiumCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(
                    Brush.linearGradient(listOf(SiphonPurple, Color(0xFF5B2AB1)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active job", style = MaterialTheme.typography.labelLarge, color = SiphonPurpleSoft)
                    Spacer(Modifier.width(8.dp))
                    StatusPill(if (job.status == JobState.Status.QUEUED) "Queued" else "Running", SiphonGreen)
                }
                Spacer(Modifier.height(5.dp))
                Text(job.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(9.dp))
                if (job.progress < 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(5.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { job.progress },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = SiphonPurpleBright,
                        trackColor = SiphonSurfaceBright
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.ChevronRight, null, tint = SiphonTextMuted)
        }
    }
}

@Composable
private fun TrustCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    PremiumCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Icon(icon, null, tint = SiphonPurpleBright, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = SiphonPurpleSoft)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> QuickSettingRow(
    icon: ImageVector,
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean = true,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(vertical = 13.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (enabled) SiphonPurpleSoft else SiphonTextMuted,
                modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else SiphonTextMuted)
            Surface(shape = RoundedCornerShape(10.dp), color = SiphonSurfaceBright) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) SiphonText else SiphonTextMuted)
                    if (enabled) {
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp), tint = SiphonTextMuted)
                    }
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SiphonSurfaceBright
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StaticSettingRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = SiphonPurpleSoft, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SiphonSurfaceBright,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                value,
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun shortFormatLabel(format: AudioFormat): String = when (format) {
    AudioFormat.COPY -> "Original"
    AudioFormat.MP3 -> "MP3"
    AudioFormat.M4A -> "M4A"
    AudioFormat.OPUS -> "Opus"
    AudioFormat.FLAC -> "FLAC"
    AudioFormat.WAV -> "WAV"
}
