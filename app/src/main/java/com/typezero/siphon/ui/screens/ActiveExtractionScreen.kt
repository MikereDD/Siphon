package com.typezero.siphon.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typezero.siphon.data.model.JobState
import com.typezero.siphon.ui.components.PremiumCard
import com.typezero.siphon.ui.components.StatusPill
import com.typezero.siphon.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveExtractionScreen(job: JobState, onBack: () -> Unit, onCancel: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Extracting", style = MaterialTheme.typography.headlineSmall) }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            StatusPill(
                if (job.status == JobState.Status.QUEUED) "Foreground job • Queued" else "Foreground job • Running",
                SiphonGreen
            )
            Spacer(Modifier.height(24.dp))

            ExtractionVisualizer(job.progress)

            Spacer(Modifier.height(22.dp))
            Text(
                job.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${job.formatLabel} • ${job.qualityLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Overall progress", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        if (job.progress < 0f) "Preparing" else "${(job.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = SiphonPurpleSoft
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (job.progress < 0f) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().height(7.dp),
                        color = SiphonPurpleBright,
                        trackColor = SiphonSurfaceBright
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { job.progress },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = SiphonPurpleBright,
                        trackColor = SiphonSurfaceBright
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    job.line.ifBlank { if (job.status == JobState.Status.QUEUED) "Waiting to begin…" else "Preparing extraction…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))
            PremiumCard(Modifier.fillMaxWidth()) {
                DetailRow("Format", job.formatLabel)
                DetailRow("Quality", job.qualityLabel)
                DetailRow("Source", job.sourceLabel)
                DetailRow("Output", "Music/Siphon", showDivider = false)
            }

            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SiphonRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, SiphonRed.copy(alpha = 0.55f))
            ) {
                Icon(Icons.Default.StopCircle, null)
                Spacer(Modifier.width(9.dp))
                Text("Cancel extraction", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ExtractionVisualizer(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f).takeIf { progress >= 0f } ?: 0.08f
    Box(Modifier.size(244.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.36f
            val bars = 72
            for (index in 0 until bars) {
                val angle = (index.toDouble() / bars.toDouble()) * (PI * 2.0) - PI / 2.0
                val pulse = 0.52f + 0.48f * kotlin.math.abs(sin(index.toDouble() * 0.47)).toFloat()
                val cosAngle = cos(angle).toFloat()
                val sinAngle = sin(angle).toFloat()
                val inner = baseRadius + 8f
                val outer = inner + 12f + pulse * 22f
                val start = Offset(
                    center.x + cosAngle * inner,
                    center.y + sinAngle * inner
                )
                val end = Offset(
                    center.x + cosAngle * outer,
                    center.y + sinAngle * outer
                )
                drawLine(
                    color = if (index.toFloat() / bars <= safeProgress) SiphonPurpleBright else SiphonPurple.copy(alpha = 0.28f),
                    start = start,
                    end = end,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF472273), SiphonSurfaceRaised),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )
            drawCircle(
                color = SiphonPurple.copy(alpha = 0.25f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 3f)
            )
            drawArc(
                color = SiphonPurpleBright,
                startAngle = -90f,
                sweepAngle = 360f * safeProgress,
                useCenter = false,
                topLeft = Offset(center.x - baseRadius - 5f, center.y - baseRadius - 5f),
                size = Size((baseRadius + 5f) * 2f, (baseRadius + 5f) * 2f),
                style = Stroke(width = 7f, cap = StrokeCap.Round)
            )
        }
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(Color(0xFF2B1745), Color(0xFF0D0E15)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.GraphicEq, null, tint = SiphonPurpleSoft, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (progress < 0f) "…" else "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, showDivider: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
    if (showDivider) HorizontalDivider(color = SiphonOutline.copy(alpha = 0.65f))
}
