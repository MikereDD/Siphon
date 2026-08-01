package com.typezero.siphon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.typezero.siphon.data.model.*
import com.typezero.siphon.ui.SiphonUiState
import com.typezero.siphon.ui.SiphonViewModel
import com.typezero.siphon.ui.components.ChipRow
import com.typezero.siphon.ui.components.PremiumCard
import com.typezero.siphon.ui.components.PremiumPrimaryButton
import com.typezero.siphon.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractSheet(state: SiphonUiState, vm: SiphonViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isLink = state.pendingSource is ExtractRequest.Source.Link

    ModalBottomSheet(
        onDismissRequest = vm::closeSheet,
        sheetState = sheetState,
        containerColor = SiphonBackground,
        contentColor = MaterialTheme.colorScheme.onBackground,
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 5.dp).size(width = 42.dp, height = 5.dp)
                    .clip(RoundedCornerShape(99.dp)).background(SiphonOutline)
            )
        }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = SiphonPurple.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null,
                            tint = SiphonPurpleSoft, modifier = Modifier.size(25.dp))
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text("Configure extraction", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isLink) "Link / URL source" else "Local video source",
                        style = MaterialTheme.typography.bodySmall,
                        color = SiphonPurpleSoft
                    )
                }
            }

            PremiumCard(Modifier.fillMaxWidth()) {
                SectionLabel("Format")
                Spacer(Modifier.height(10.dp))
                ChipRow(
                    options = AudioFormat.entries,
                    selected = state.format,
                    label = { formatChipLabel(it) },
                    onSelect = vm::setFormat
                )
                Spacer(Modifier.height(14.dp))
                if (state.format.lossy) {
                    SectionLabel("Quality")
                    Spacer(Modifier.height(10.dp))
                    ChipRow(
                        options = AudioQuality.entries,
                        selected = state.quality,
                        label = { it.label },
                        onSelect = vm::setQuality
                    )
                } else {
                    Surface(shape = RoundedCornerShape(12.dp), color = SiphonPurple.copy(alpha = 0.09f)) {
                        Text(
                            if (state.format == AudioFormat.COPY)
                                "Keeps the original audio stream whenever possible — no generation loss."
                            else "Lossless output — bitrate does not apply.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            PremiumCard(Modifier.fillMaxWidth()) {
                SectionLabel("Output")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.outputName,
                    onValueChange = vm::setOutputName,
                    placeholder = { Text(if (isLink) "Use source title automatically" else "Output name") },
                    label = { Text("File name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = premiumFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(9.dp))
                Text("Saved to Music/Siphon after verification.",
                    style = MaterialTheme.typography.bodySmall, color = SiphonTextMuted)
            }

            PremiumCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Metadata")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = vm::resetTags) {
                        Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Reset")
                    }
                }
                Spacer(Modifier.height(8.dp))
                val tags = state.tags
                TagField("Title", tags.title) { value -> vm.updateTags { it.copy(title = value) } }
                Spacer(Modifier.height(10.dp))
                TagField("Artist", tags.artist) { value -> vm.updateTags { it.copy(artist = value) } }
                Spacer(Modifier.height(10.dp))
                TagField("Album", tags.album) { value -> vm.updateTags { it.copy(album = value) } }
                Spacer(Modifier.height(10.dp))
                TagField("Album artist", tags.albumArtist) { value -> vm.updateTags { it.copy(albumArtist = value) } }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TagField("Genre", tags.genre, Modifier.weight(1f)) { value ->
                        vm.updateTags { it.copy(genre = value) }
                    }
                    TagField("Year", tags.year, Modifier.weight(1f), KeyboardType.Number) { value ->
                        vm.updateTags { it.copy(year = value.filter(Char::isDigit).take(4)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TagField("Track #", tags.track, keyboard = KeyboardType.Number) { value ->
                    vm.updateTags { it.copy(track = value.filter(Char::isDigit)) }
                }
                Spacer(Modifier.height(10.dp))
                TagField("Comment", tags.comment) { value -> vm.updateTags { it.copy(comment = value) } }
                Spacer(Modifier.height(10.dp))
                SwitchRow("Embed source metadata", tags.embedSourceMetadata) { enabled ->
                    vm.updateTags { it.copy(embedSourceMetadata = enabled) }
                }
                if (isLink) {
                    SwitchRow("Embed thumbnail as cover art", tags.embedThumbnail) { enabled ->
                        vm.updateTags { it.copy(embedThumbnail = enabled) }
                    }
                }
            }

            PremiumPrimaryButton(
                text = "Start extraction",
                onClick = vm::startExtraction,
                icon = Icons.Default.RocketLaunch,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = SiphonPurpleSoft,
    fontWeight = FontWeight.SemiBold
)

@Composable
private fun TagField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = premiumFieldColors(),
        modifier = if (modifier == Modifier) Modifier.fillMaxWidth() else modifier
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = SiphonPurple,
                uncheckedTrackColor = SiphonSurfaceBright,
                uncheckedBorderColor = SiphonOutline
            )
        )
    }
}

@Composable
private fun premiumFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SiphonSurfaceBright,
    unfocusedContainerColor = SiphonSurfaceBright,
    focusedBorderColor = SiphonPurple,
    unfocusedBorderColor = SiphonOutline,
    focusedLabelColor = SiphonPurpleSoft,
    cursorColor = SiphonPurpleBright
)

private fun formatChipLabel(format: AudioFormat): String = when (format) {
    AudioFormat.COPY -> "Original"
    AudioFormat.MP3 -> "MP3"
    AudioFormat.M4A -> "M4A"
    AudioFormat.OPUS -> "Opus"
    AudioFormat.FLAC -> "FLAC"
    AudioFormat.WAV -> "WAV"
}
