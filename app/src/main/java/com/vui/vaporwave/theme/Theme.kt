package com.vui.vaporwave.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val VaporwaveDarkColorScheme = darkColorScheme(
    primary = VaporPink,
    onPrimary = Color(0xFF380036),
    primaryContainer = VaporPinkDark,
    onPrimaryContainer = VaporPinkLight,

    secondary = VaporCyan,
    onSecondary = Color(0xFF003646),
    secondaryContainer = VaporCyanDark,
    onSecondaryContainer = VaporCyanLight,

    tertiary = VaporMint,
    onTertiary = Color(0xFF003821),
    tertiaryContainer = VaporMintDark,
    onTertiaryContainer = VaporMintLight,

    background = VaporVoid,
    onBackground = VaporOnSurface,

    surface = VaporSurface,
    onSurface = VaporOnSurface,

    surfaceVariant = VaporSurfaceVariant,
    onSurfaceVariant = VaporOnSurfaceVariant,

    surfaceContainer = VaporSurfaceContainer,
    surfaceContainerHigh = VaporSurfaceContainerHigh,
    surfaceContainerHighest = VaporSurfaceContainerHighest,

    outline = VaporOutline,
    outlineVariant = VaporOutlineVariant
)

val VaporwaveLightColorScheme = lightColorScheme(
    primary = PastelPink,
    onPrimary = Color.White,
    primaryContainer = PastelPinkContainer,
    onPrimaryContainer = Color(0xFF3B002D),

    secondary = PastelCyan,
    onSecondary = Color.White,
    secondaryContainer = PastelCyanContainer,
    onSecondaryContainer = Color(0xFF001F29),

    tertiary = Color(0xFF006C4C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF88F8C8),
    onTertiaryContainer = Color(0xFF002114),

    background = LightSurface,
    onBackground = Color(0xFF1F1A24),

    surface = LightSurface,
    onSurface = Color(0xFF1F1A24),

    surfaceVariant = Color(0xFFEADBEC),
    onSurfaceVariant = Color(0xFF4C444F),

    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = Color(0xFFEEE7F1),
    surfaceContainerHighest = Color(0xFFE8E1EB),

    outline = Color(0xFF7E7381),
    outlineVariant = Color(0xFFD0C3D2)
)

@Composable
fun VaporwaveTheme(
    darkTheme: Boolean = true, // Default to stunning dark aesthetic
    useDynamicColor: Boolean = false, // Set to true to use Android 12+ wallpaper colors
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Remembered because dynamicDark/LightColorScheme build a whole ColorScheme from the
    // wallpaper palette on each call; without this it re-runs on every recomposition of the
    // theme, which wraps the entire app.
    val colorScheme = remember(darkTheme, useDynamicColor, context) {
        when {
            useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> VaporwaveDarkColorScheme
            else -> VaporwaveLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
