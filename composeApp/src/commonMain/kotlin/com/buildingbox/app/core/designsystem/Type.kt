package com.buildingbox.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.buildingbox.app.resources.Res
import com.buildingbox.app.resources.inter
import com.buildingbox.app.resources.jetbrains_mono
import com.buildingbox.app.resources.sora
import org.jetbrains.compose.resources.Font

/** display = Sora, body = Inter, mono = JetBrains Mono (matches the design tokens). */
@Immutable
data class AppFonts(val display: FontFamily, val body: FontFamily, val mono: FontFamily)

val LocalAppFonts = staticCompositionLocalOf<AppFonts> { error("AppFonts not provided") }

@Composable
fun rememberAppFonts(): AppFonts {
    val sora = FontFamily(
        Font(Res.font.sora, FontWeight.Medium),
        Font(Res.font.sora, FontWeight.SemiBold),
        Font(Res.font.sora, FontWeight.Bold),
        Font(Res.font.sora, FontWeight.ExtraBold),
    )
    val inter = FontFamily(
        Font(Res.font.inter, FontWeight.Normal),
        Font(Res.font.inter, FontWeight.Medium),
        Font(Res.font.inter, FontWeight.SemiBold),
        Font(Res.font.inter, FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(Res.font.jetbrains_mono, FontWeight.Medium),
        Font(Res.font.jetbrains_mono, FontWeight.SemiBold),
        Font(Res.font.jetbrains_mono, FontWeight.Bold),
    )
    return AppFonts(sora, inter, mono)
}

fun appTypography(fonts: AppFonts): Typography {
    val d = fonts.display
    val b = fonts.body
    return Typography(
        displayLarge = TextStyle(fontFamily = d, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, letterSpacing = (-1.2).sp, lineHeight = 46.sp),
        headlineLarge = TextStyle(fontFamily = d, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.8).sp),
        headlineMedium = TextStyle(fontFamily = d, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, letterSpacing = (-0.6).sp),
        headlineSmall = TextStyle(fontFamily = d, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.4).sp),
        titleLarge = TextStyle(fontFamily = d, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.3).sp),
        titleMedium = TextStyle(fontFamily = d, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.2).sp),
        bodyLarge = TextStyle(fontFamily = b, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = b, fontWeight = FontWeight.Normal, fontSize = 15.sp),
        bodySmall = TextStyle(fontFamily = b, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = b, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = b, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = b, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.4.sp),
    )
}
