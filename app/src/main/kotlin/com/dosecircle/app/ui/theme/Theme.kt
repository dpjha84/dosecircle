package com.dosecircle.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** True when "Comfort mode" is on: larger text and bigger buttons for anyone who finds the standard size hard to read or tap. */
val LocalComfort = staticCompositionLocalOf { false }

// Rx green on gauze white, with an amber-bottle secondary.
private val Light: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B6E5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEE3), onPrimaryContainer = Color(0xFF00382D),
    secondary = Color(0xFF8A5100), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB3), onSecondaryContainer = Color(0xFF2C1700),
    tertiary = Color(0xFF3F5FA8), onTertiary = Color.White,
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFFADAD7), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF5F8F4), onBackground = Color(0xFF161D19),
    surface = Color(0xFFF5F8F4), onSurface = Color(0xFF161D19),
    surfaceVariant = Color(0xFFE4EBE6), onSurfaceVariant = Color(0xFF3E4943),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAFCF9),
    surfaceContainer = Color(0xFFEEF3EF), surfaceContainerHigh = Color(0xFFE8EEEA), surfaceContainerHighest = Color(0xFFE1E8E3),
    outline = Color(0xFF707A74), outlineVariant = Color(0xFFC8D2CC),
)

private val Dark: ColorScheme = darkColorScheme(
    primary = Color(0xFF7FD8C0), onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF0B5142), onPrimaryContainer = Color(0xFFCDEEE3),
    secondary = Color(0xFFFFB95C), onSecondary = Color(0xFF4A2E00),
    secondaryContainer = Color(0xFF4A2E00), onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = Color(0xFFB2C5FF), onTertiary = Color(0xFF0E2F6F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF601410),
    errorContainer = Color(0xFF7A1A14), onErrorContainer = Color(0xFFFADAD7),
    background = Color(0xFF0E1411), onBackground = Color(0xFFE0E7E2),
    surface = Color(0xFF0E1411), onSurface = Color(0xFFE0E7E2),
    surfaceVariant = Color(0xFF26302B), onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceContainerLowest = Color(0xFF171E1A), surfaceContainerLow = Color(0xFF141A17),
    surfaceContainer = Color(0xFF1B2320), surfaceContainerHigh = Color(0xFF222B27), surfaceContainerHighest = Color(0xFF2A3430),
    outline = Color(0xFF8A9690), outlineVariant = Color(0xFF3D4943),
)

// Generous sizes by default: the audience includes people reading at arm's length.
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DoseCircleTheme(comfort: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = Density(density.density, density.fontScale * if (comfort) 1.2f else 1f)
    CompositionLocalProvider(LocalComfort provides comfort, LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Colours that tell medicines (and people) apart at a glance. */
val MedPalette = listOf(
    Color(0xFF0B6E5B), Color(0xFFB26A00), Color(0xFFB4445A), Color(0xFF3F5FA8),
    Color(0xFF6B7A1E), Color(0xFF7A4FA0), Color(0xFF00798C), Color(0xFF9A5B3A),
)
