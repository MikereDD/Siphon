package com.typezero.siphon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.typezero.siphon.ui.theme.*

@Composable
fun PremiumBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(SiphonBackground)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SiphonPurple.copy(alpha = 0.13f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.08f),
                radius = size.minDimension * 0.62f
            ),
            radius = size.minDimension * 0.62f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.08f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF47258A).copy(alpha = 0.10f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.78f),
                radius = size.minDimension * 0.55f
            ),
            radius = size.minDimension * 0.55f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.78f)
        )
    }
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .then(interactionModifier),
        color = SiphonSurfaceRaised.copy(alpha = 0.96f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, SiphonOutline.copy(alpha = 0.72f)),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionHeading(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color = SiphonPurple, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.07f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun SourceChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingIcon: ImageVector
) {
    PremiumCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(trailingIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PremiumPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SiphonPurple,
            contentColor = Color.White,
            disabledContainerColor = SiphonSurfaceBright,
            disabledContentColor = SiphonTextMuted
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                .background(SiphonPurple.copy(alpha = if (enabled) 0.13f else 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = if (enabled) SiphonPurpleSoft else SiphonTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else SiphonTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!badge.isNullOrBlank()) {
            StatusPill(badge, SiphonPurpleBright)
            Spacer(Modifier.width(8.dp))
        }
        if (!value.isNullOrBlank()) {
            Text(value, style = MaterialTheme.typography.labelMedium, color = SiphonTextMuted)
            Spacer(Modifier.width(8.dp))
        }
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Default.ChevronRight,
            null,
            tint = SiphonTextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
