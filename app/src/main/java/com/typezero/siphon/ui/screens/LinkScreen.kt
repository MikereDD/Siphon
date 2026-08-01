package com.typezero.siphon.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.typezero.siphon.data.model.YouTubeClient
import com.typezero.siphon.ui.SiphonUiState
import com.typezero.siphon.ui.SiphonViewModel
import com.typezero.siphon.ui.components.ChipRow
import com.typezero.siphon.ui.components.PremiumCard
import com.typezero.siphon.ui.components.PremiumPrimaryButton
import com.typezero.siphon.ui.components.SectionHeading
import com.typezero.siphon.ui.components.StatusPill
import com.typezero.siphon.ui.theme.*

@Composable
fun LinkScreen(state: SiphonUiState, vm: SiphonViewModel) {
    val clipboard = LocalClipboardManager.current
    val cookiePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importCookies)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PremiumCard(Modifier.fillMaxWidth()) {
            SectionHeading(
                title = "Audio from a link",
                subtitle = "Paste a supported video or audio URL. Siphon downloads the stream and extracts only the audio."
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.linkUrl,
                onValueChange = vm::setLinkUrl,
                placeholder = { Text("https://…") },
                leadingIcon = { Icon(Icons.Default.Link, null, tint = SiphonPurpleSoft) },
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.text?.let(vm::setLinkUrl) }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(15.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SiphonSurfaceBright,
                    unfocusedContainerColor = SiphonSurfaceBright,
                    focusedBorderColor = SiphonPurple,
                    unfocusedBorderColor = SiphonOutline
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            PremiumPrimaryButton(
                text = "Choose format and metadata",
                onClick = vm::openSheetForLink,
                enabled = state.linkUrl.isNotBlank(),
                icon = Icons.Default.Tune,
                modifier = Modifier.fillMaxWidth()
            )
        }

        PremiumCard(Modifier.fillMaxWidth()) {
            SectionHeading(
                title = "Extractor compatibility",
                subtitle = "Try a different player profile when a site rejects the default request."
            )
            Spacer(Modifier.height(13.dp))
            ChipRow(
                options = YouTubeClient.entries,
                selected = state.linkClient,
                label = { it.label },
                onSelect = vm::setLinkClient
            )
        }

        PremiumCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Protected-link cookies", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Load a browser-exported cookies.txt only when a supported site requires sign-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(if (state.cookiesLoaded) "Loaded" else "Not loaded",
                    if (state.cookiesLoaded) SiphonGreen else SiphonTextMuted)
            }
            Spacer(Modifier.height(14.dp))
            if (state.cookiesLoaded) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { cookiePicker.launch(arrayOf("text/plain", "*/*")) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Replace")
                    }
                    OutlinedButton(
                        onClick = vm::clearCookies,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SiphonRed)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Remove")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { cookiePicker.launch(arrayOf("text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Cookie, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Load cookies.txt")
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SiphonPurple.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(1.dp, SiphonPurple.copy(alpha = 0.2f))
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Shield, null, tint = SiphonPurpleSoft, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Only extract content you own or have the right to use. Cookies remain in Siphon’s private app storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
