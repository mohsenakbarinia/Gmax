package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WildwoodDarkColorScheme = darkColorScheme(
    primary = SunlitAmber,
    onPrimary = OnAmberInk,
    primaryContainer = SunlitAmberDark,
    onPrimaryContainer = BoneWhite,
    secondary = AlpineEmerald,
    onSecondary = BoneWhite,
    secondaryContainer = AlpineEmeraldContainer,
    onSecondaryContainer = BoneWhite,
    tertiary = HunterCyan,
    onTertiary = ObsidianPine,
    tertiaryContainer = HunterCyanContainer,
    onTertiaryContainer = BoneWhite,
    background = ObsidianPine,
    onBackground = BoneWhite,
    surface = DeepForestSurface,
    onSurface = BoneWhite,
    surfaceVariant = CharcoalMoss,
    onSurfaceVariant = SlateMist,
    outline = GlassBorder,
    error = CrimsonEmber,
    onError = BoneWhite,
    errorContainer = CrimsonContainer,
    onErrorContainer = BoneWhite
)

@Composable
fun WildwoodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WildwoodDarkColorScheme,
        typography = WildwoodTypography,
        content = content
    )
}
