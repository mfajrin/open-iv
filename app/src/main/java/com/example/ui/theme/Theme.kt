package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ElectricEmerald,
    onPrimary = OnEmerald,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = ElectricEmerald,
    secondary = CyberCyan,
    onSecondary = Color(0xFF001E2E),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyberCyan,
    tertiary = AmberGold,
    onTertiary = Color(0xFF261A00),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberGold,
    error = AlertRed,
    onError = Color(0xFF3B0004),
    errorContainer = RedContainer,
    onErrorContainer = AlertRed,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = ElevatedSlate,
    surfaceContainerHigh = HighestSlate,
    outline = OutlineSlate,
    outlineVariant = Color(0xFF21262D)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldLightPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF0277BD),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6EEFF),
    onSecondaryContainer = Color(0xFF001E2E),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF3B1B00),
    error = Color(0xFFDC2626),
    onError = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline
)

val OpenVShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun OpenVStudioTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OpenVTypography,
        shapes = OpenVShapes,
        content = content
    )
}
