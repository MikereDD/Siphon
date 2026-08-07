package com.typezero.siphon.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.typezero.siphon.BuildConfig
import com.typezero.siphon.R
import com.typezero.siphon.ui.SiphonUiState
import com.typezero.siphon.ui.SiphonViewModel
import com.typezero.siphon.ui.components.PremiumCard
import com.typezero.siphon.ui.components.SettingRow
import com.typezero.siphon.ui.theme.*

@Composable
fun SettingsScreen(state: SiphonUiState, vm: SiphonViewModel) {
    val uriHandler = LocalUriHandler.current
    val channel = if (BuildConfig.VERSION_NAME.contains("-dev")) "Development" else "Stable"

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = SiphonSurfaceBright,
                        border = BorderStroke(1.dp, SiphonPurple.copy(alpha = 0.35f)),
                        shadowElevation = 8.dp
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher),
                            contentDescription = "Siphon icon",
                            modifier = Modifier.padding(7.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Siphon",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = SiphonPurple.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, SiphonPurple.copy(alpha = 0.3f))
                        ) {
                            Text(channel, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall, color = SiphonPurpleSoft)
                        }
                    }
                }
            }
        }

        item {
            PremiumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
                SettingRow(
                    icon = Icons.Default.Info,
                    title = "About Siphon",
                    subtitle = "Version, components, licenses, and diagnostics",
                    onClick = vm::openAbout
                )
                HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                SettingRow(
                    icon = Icons.Default.Storage,
                    title = "Storage cleanup",
                    subtitle = "Review legacy and abandoned app-data files",
                    badge = if (state.storage.totalBytes > 0) formatBytes(state.storage.totalBytes) else null,
                    onClick = vm::openStorageCleanup
                )
                HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                SettingRow(
                    icon = Icons.Default.Update,
                    title = "Update extractor",
                    subtitle = if (state.extractorUpdating) {
                        "Updating the stable extractor…"
                    } else {
                        "Current: ${state.extractorVersion ?: "Bundled"} · Update without reinstalling Siphon"
                    },
                    enabled = !state.extractorUpdating && state.activeJob == null,
                    onClick = { vm.updateExtractor(nightly = false) }
                )
                HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                SettingRow(
                    icon = Icons.Default.Bolt,
                    title = "Update extractor — nightly",
                    subtitle = "Newest site fixes; may be less stable",
                    enabled = !state.extractorUpdating && state.activeJob == null,
                    onClick = { vm.updateExtractor(nightly = true) }
                )
                HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                SettingRow(
                    icon = Icons.Default.SystemUpdate,
                    title = "Application updates",
                    subtitle = "Signed Siphon APK updater",
                    value = "Coming later",
                    enabled = false,
                    onClick = {}
                )
            }
        }

        item {
            PremiumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
                SettingRow(
                    icon = Icons.Default.Cookie,
                    title = "Link cookies",
                    subtitle = if (state.cookiesLoaded) "cookies.txt is loaded for protected links" else "No cookies are stored",
                    value = if (state.cookiesLoaded) "Loaded" else "None",
                    enabled = state.cookiesLoaded,
                    onClick = vm::clearCookies
                )
                HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                SettingRow(
                    icon = Icons.Default.Code,
                    title = "View source repository",
                    subtitle = "Open the current Siphon source",
                    onClick = { uriHandler.openUri(SOURCE_REPOSITORY) }
                )
                HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                SettingRow(
                    icon = Icons.Default.Description,
                    title = "Open-source notices",
                    onClick = vm::openLicenses
                )
                HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = SiphonOutline.copy(alpha = 0.65f))
                SettingRow(
                    icon = Icons.Default.ContentCopy,
                    title = "Copy safe diagnostics",
                    subtitle = "No URLs, cookies, filenames, or private paths",
                    onClick = vm::copyDiagnostics
                )
            }
        }

        item {
            Text(
                "Created by Typezer∅",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = SiphonPurpleSoft,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return "%.1f %s".format(java.util.Locale.US, value, units[unit])
}

internal const val SOURCE_REPOSITORY =
    "https://github.com/MikereDD/It-Works-On-My-Machine/tree/main/Android/Siphon"
