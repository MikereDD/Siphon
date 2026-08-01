package com.typezero.siphon.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.typezero.siphon.data.model.JobState
import com.typezero.siphon.ui.SiphonViewModel
import com.typezero.siphon.ui.components.PremiumBackdrop
import com.typezero.siphon.ui.theme.*

enum class AppSection(val label: String, val icon: ImageVector) {
    EXTRACT("Extract", Icons.Default.Home),
    HISTORY("History", Icons.Default.History),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    SETTINGS("Settings", Icons.Default.Settings)
}

private enum class ExtractRoute { DASHBOARD, LOCAL, LINK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiphonRoot(vm: SiphonViewModel, onRequestPermission: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var section by rememberSaveable { mutableStateOf(AppSection.EXTRACT) }
    var extractRoute by rememberSaveable { mutableStateOf(ExtractRoute.DASHBOARD) }
    var activeDetailsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeSnackbar()
        }
    }

    LaunchedEffect(state.activeJob?.id) {
        activeDetailsOpen = state.activeJob != null
    }

    LaunchedEffect(state.tab) {
        if (state.tab == com.typezero.siphon.ui.Tab.LINK) {
            section = AppSection.EXTRACT
            extractRoute = ExtractRoute.LINK
            vm.consumeRequestedTab()
        }
    }

    BackHandler(activeDetailsOpen) { activeDetailsOpen = false }
    BackHandler(!activeDetailsOpen && section == AppSection.EXTRACT && extractRoute != ExtractRoute.DASHBOARD) {
        extractRoute = ExtractRoute.DASHBOARD
    }

    Box(Modifier.fillMaxSize()) {
        PremiumBackdrop()

        if (activeDetailsOpen && state.activeJob != null) {
            ActiveExtractionScreen(
                job = requireNotNull(state.activeJob),
                onBack = { activeDetailsOpen = false },
                onCancel = vm::cancelActive
            )
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    PremiumTopBar(
                        section = section,
                        extractRoute = extractRoute,
                        onBack = { extractRoute = ExtractRoute.DASHBOARD }
                    )
                },
                bottomBar = {
                    Column {
                        state.activeJob?.let { job ->
                            MiniActiveJob(job = job, onClick = { activeDetailsOpen = true })
                        }
                        PremiumBottomNavigation(
                            selected = section,
                            onSelect = {
                                section = it
                                if (it != AppSection.EXTRACT) extractRoute = ExtractRoute.DASHBOARD
                            }
                        )
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (section) {
                        AppSection.EXTRACT -> when (extractRoute) {
                            ExtractRoute.DASHBOARD -> ExtractDashboardScreen(
                                state = state,
                                vm = vm,
                                onLocal = { extractRoute = ExtractRoute.LOCAL },
                                onLink = { extractRoute = ExtractRoute.LINK },
                                onOpenActive = { activeDetailsOpen = true }
                            )
                            ExtractRoute.LOCAL -> LocalVideosScreen(state, vm, onRequestPermission)
                            ExtractRoute.LINK -> LinkScreen(state, vm)
                        }
                        AppSection.HISTORY -> HistoryScreen(state.history, vm::clearHistory)
                        AppSection.LIBRARY -> LibraryScreen(state.history)
                        AppSection.SETTINGS -> SettingsScreen(state, vm)
                    }
                }
            }
        }
    }

    if (state.sheetOpen && state.pendingSource != null) ExtractSheet(state, vm)
    if (state.legacyPromptOpen && !state.storageDialogOpen) {
        LegacyFilesPrompt(state, vm::reviewLegacyFiles, vm::dismissLegacyPrompt)
    }
    if (state.storageDialogOpen) StorageCleanupScreen(state, vm)
    state.cleanupConfirmation?.let { CleanupConfirmationDialog(it, state, vm) }

    if (state.licensesOpen) LicensesDialog(onClose = vm::closeLicenses)
    else if (state.aboutOpen) AboutScreen(state, vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumTopBar(
    section: AppSection,
    extractRoute: ExtractRoute,
    onBack: () -> Unit
) {
    val isSubRoute = section == AppSection.EXTRACT && extractRoute != ExtractRoute.DASHBOARD
    val title = when {
        extractRoute == ExtractRoute.LOCAL -> "Local videos"
        extractRoute == ExtractRoute.LINK -> "Link extraction"
        else -> section.label
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (isSubRoute) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        },
        title = {
            Column {
                Text(
                    if (section == AppSection.EXTRACT && extractRoute == ExtractRoute.DASHBOARD) "Siphon" else title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (section == AppSection.EXTRACT && extractRoute == ExtractRoute.DASHBOARD) {
                    Text(
                        "Extract audio. Keep what matters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SiphonPurpleSoft
                    )
                }
            }
        },
        actions = {
            if (section == AppSection.EXTRACT && extractRoute == ExtractRoute.DASHBOARD) {
                Box(
                    Modifier.padding(end = 18.dp).size(10.dp).clip(RoundedCornerShape(99.dp))
                        .background(SiphonPurple)
                )
            }
        }
    )
}

@Composable
private fun PremiumBottomNavigation(selected: AppSection, onSelect: (AppSection) -> Unit) {
    NavigationBar(
        containerColor = SiphonSurface.copy(alpha = 0.98f),
        tonalElevation = 0.dp
    ) {
        AppSection.entries.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, null, modifier = Modifier.size(23.dp)) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SiphonPurpleSoft,
                    selectedTextColor = SiphonPurpleSoft,
                    indicatorColor = SiphonPurple.copy(alpha = 0.16f),
                    unselectedIconColor = SiphonTextMuted,
                    unselectedTextColor = SiphonTextMuted
                )
            )
        }
    }
}

@Composable
private fun MiniActiveJob(job: JobState, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(17.dp))
            .clickable(onClick = onClick),
        color = SiphonSurfaceRaised,
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SiphonPurple.copy(alpha = 0.32f)),
        shadowElevation = 8.dp
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(
                    Brush.linearGradient(listOf(SiphonPurple, Color(0xFF5E2EBE)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                if (job.progress < 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { job.progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = SiphonPurpleBright,
                        trackColor = SiphonSurfaceBright
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (job.progress < 0f) "…" else "${(job.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = SiphonPurpleSoft
            )
        }
    }
}
