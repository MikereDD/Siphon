package com.typezero.siphon.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SiphonPurple = Color(0xFF9B5CFF)
val SiphonPurpleBright = Color(0xFFB979FF)
val SiphonPurpleSoft = Color(0xFFD6B7FF)
val SiphonGreen = Color(0xFF57D88A)
val SiphonRed = Color(0xFFFF6173)
val SiphonBackground = Color(0xFF07080D)
val SiphonSurface = Color(0xFF0E1118)
val SiphonSurfaceRaised = Color(0xFF151925)
val SiphonSurfaceBright = Color(0xFF1C2030)
val SiphonOutline = Color(0xFF2A3040)
val SiphonText = Color(0xFFF2F0F7)
val SiphonTextMuted = Color(0xFF9A9EAD)

private val PremiumDarkColors = darkColorScheme(
    primary = SiphonPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A176D),
    onPrimaryContainer = SiphonPurpleSoft,
    secondary = SiphonPurpleBright,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A2140),
    onSecondaryContainer = Color(0xFFE5D5FF),
    tertiary = SiphonGreen,
    onTertiary = Color(0xFF062312),
    error = SiphonRed,
    onError = Color.White,
    background = SiphonBackground,
    onBackground = SiphonText,
    surface = SiphonSurface,
    onSurface = SiphonText,
    surfaceVariant = SiphonSurfaceRaised,
    onSurfaceVariant = SiphonTextMuted,
    outline = SiphonOutline,
    outlineVariant = Color(0xFF202534),
    scrim = Color.Black
)

private val PremiumTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5f).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

private val PremiumShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)

@Composable
fun SiphonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PremiumDarkColors,
        typography = PremiumTypography,
        shapes = PremiumShapes
    ) {
        CompositionLocalProvider(
            LocalContentColor provides PremiumDarkColors.onBackground,
            content = content
        )
    }
}
