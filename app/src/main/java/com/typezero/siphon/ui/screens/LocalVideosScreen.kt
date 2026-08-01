package com.typezero.siphon.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typezero.siphon.data.model.VideoItem
import com.typezero.siphon.ui.SiphonUiState
import com.typezero.siphon.ui.SiphonViewModel
import com.typezero.siphon.ui.components.IconTile
import com.typezero.siphon.ui.components.PremiumCard
import com.typezero.siphon.ui.components.PremiumPrimaryButton
import com.typezero.siphon.ui.components.formatDuration
import com.typezero.siphon.ui.components.formatSize
import com.typezero.siphon.ui.theme.*

@Composable
fun LocalVideosScreen(state: SiphonUiState, vm: SiphonViewModel, onRequestPermission: () -> Unit) {
    if (!state.hasMediaPermission) {
        PermissionPrompt(onRequestPermission)
        return
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = vm::setSearch,
            placeholder = { Text("Search videos") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.searchQuery.isNotBlank()) {
                    IconButton(onClick = { vm.setSearch("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SiphonSurfaceRaised,
                unfocusedContainerColor = SiphonSurfaceRaised,
                focusedBorderColor = SiphonPurple,
                unfocusedBorderColor = SiphonOutline
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
        )

        when {
            state.videosLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = SiphonPurple)
                    Spacer(Modifier.height(12.dp))
                    Text("Scanning local videos…", color = SiphonTextMuted)
                }
            }
            state.visibleVideos.isEmpty() -> EmptyState(
                if (state.searchQuery.isBlank()) "No videos found on this device."
                else "No videos match “${state.searchQuery}”."
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "${state.visibleVideos.size} video${if (state.visibleVideos.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SiphonPurpleSoft,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(state.visibleVideos, key = { it.id }) { video ->
                    VideoRow(video) { vm.openSheetForVideo(video) }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun VideoRow(video: VideoItem, onClick: () -> Unit) {
    PremiumCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.VideoLibrary)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                val meta = buildString {
                    append(formatDuration(video.durationMs))
                    formatSize(video.sizeBytes).takeIf { it.isNotEmpty() }?.let { append(" • $it") }
                }
                Text(meta, style = MaterialTheme.typography.bodySmall, color = SiphonTextMuted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = SiphonTextMuted)
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(18.dp), Alignment.Center) {
        PremiumCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                IconTile(Icons.Default.VideoLibrary)
                Spacer(Modifier.height(18.dp))
                Text("Allow access to local videos", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Siphon only needs video-library access so you can choose a source file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                PremiumPrimaryButton(
                    text = "Grant video access",
                    onClick = onRequest,
                    icon = Icons.Default.LockOpen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(18.dp), Alignment.Center) {
        PremiumCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(44.dp), tint = SiphonPurple)
                Spacer(Modifier.height(14.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
