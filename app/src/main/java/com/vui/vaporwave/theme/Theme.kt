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

private val OledCardGray = Color(0xFF121212)
private val OledSeekbarGray = Color(0xFF1E1E1E)

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
    useVaporwaveTheme: Boolean = true, // Neon palette -- takes priority over Material You below
    useDynamicColor: Boolean = false, // Set to true to use Android 12+ wallpaper colors
    useOledBlack: Boolean = false, // Pure black surfaces/background, only meaningful while dark
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Remembered because dynamicDark/LightColorScheme build a whole ColorScheme from the
    // wallpaper palette on each call; without this it re-runs on every recomposition of the
    // theme, which wraps the entire app.
    val colorScheme = remember(darkTheme, useVaporwaveTheme, useDynamicColor, useOledBlack, context) {
        // Neon and Material You are two different ways of picking a whole palette -- they can't
        // both apply at once, so Neon wins when both are on (Material You has nothing to do in
        // that case; SettingsScreen dims its toggle to make that explicit rather than silent).
        val base = when {
            useVaporwaveTheme -> if (darkTheme) VaporwaveDarkColorScheme else VaporwaveLightColorScheme
            useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            // Neither Neon nor Material You (or Material You unavailable pre-Android 12): a
            // plain, unbranded Material 3 baseline rather than silently falling back to neon.
            darkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }

        if (useOledBlack && darkTheme) {
            // background/surface go pure black (the actual OLED power-saving win); Card
            // containers (surfaceContainer/Low/High) go dark gray instead, so they're still
            // visible against that black backdrop. surfaceContainerHighest is kept as its own,
            // slightly lighter gray -- it's what the seek bar's unseeked track (and the volume
            // slider) are themed off, and that one was asked to stay at the original shade
            // rather than following the cards' later darkening.
            base.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = OledCardGray,
                surfaceContainerLow = OledCardGray,
                surfaceContainerHigh = OledCardGray,
                surfaceContainerHighest = OledSeekbarGray
            )
        } else {
            base
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
