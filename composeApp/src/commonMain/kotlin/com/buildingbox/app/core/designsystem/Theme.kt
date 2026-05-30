package com.buildingbox.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors beyond Material's scheme (money flow, surfaces) — fintech direction. */
@Immutable
data class AppColors(
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color = Color(0xFF1FA98A),
    val flowIn: Color,
    val flowInSoft: Color,
    val flowOut: Color,
    val flowOutSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val surface: Color,
    val surfaceInset: Color,
    val hairline: Color,
    val textSecondary: Color,
    val textTertiary: Color,
)

private val LightAppColors = AppColors(
    accent = Color(0xFF12A88A),
    accentStrong = Color(0xFF0E8E76),
    accentSoft = Color(0xFFDCF2EB),
    flowIn = Color(0xFF12A37C),
    flowInSoft = Color(0xFFDFF3EC),
    flowOut = Color(0xFFD9533D),
    flowOutSoft = Color(0xFFF7E3DE),
    warn = Color(0xFFB8842A),
    warnSoft = Color(0xFFF6EAD2),
    surface = Color(0xFFFFFFFF),
    surfaceInset = Color(0xFFF0F2F5),
    hairline = Color(0xFFE2E5EA),
    textSecondary = Color(0xFF5B636E),
    textTertiary = Color(0xFF8A919C),
)

private val DarkAppColors = AppColors(
    accent = Color(0xFF35D0AD),
    accentStrong = Color(0xFF4FE0BE),
    accentSoft = Color(0xFF103A30),
    flowIn = Color(0xFF45D79F),
    flowInSoft = Color(0xFF173A30),
    flowOut = Color(0xFFFF8169),
    flowOutSoft = Color(0xFF3D211B),
    warn = Color(0xFFE8B765),
    warnSoft = Color(0xFF3A2F18),
    surface = Color(0xFF1C2127),
    surfaceInset = Color(0xFF161A1F),
    hairline = Color(0xFF2A2F37),
    textSecondary = Color(0xFFADB4BE),
    textTertiary = Color(0xFF727A85),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

private val LightScheme = lightColorScheme(
    primary = LightAppColors.accent,
    onPrimary = Color.White,
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF1A1E24),
    surface = LightAppColors.surface,
    onSurface = Color(0xFF1A1E24),
)

private val DarkScheme = darkColorScheme(
    primary = DarkAppColors.accent,
    onPrimary = Color(0xFF052019),
    background = Color(0xFF11151A),
    onBackground = Color(0xFFF2F4F7),
    surface = DarkAppColors.surface,
    onSurface = Color(0xFFF2F4F7),
)

@Composable
fun BuildingBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val fonts = rememberAppFonts()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalAppFonts provides fonts,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = appTypography(fonts),
            content = content,
        )
    }
}
