package com.velvet.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.velvet.music.R
import com.velvet.music.data.VelvetAccent

val Ink = Color(0xFF07060A)
val InkLift = Color(0xFF0C0A11)
val Surface1 = Color(0xFF100D16)
val Surface2 = Color(0xFF17131F)
val Surface3 = Color(0xFF1F1A29)
val Hairline = Color(0xFF2B2536)
val TextPrimary = Color(0xFFF6F3FA)
val TextSecondary = Color(0xFFA69DB2)
val TextTertiary = Color(0xFF6F677B)

data class AccentPalette(
    val primary: Color,
    val bright: Color,
    val deep: Color,
    val glow: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val onAccent: Color = Color(0xFF140B1F)
)

fun paletteFor(accent: VelvetAccent): AccentPalette = when (accent) {
    VelvetAccent.Amethyst -> AccentPalette(
        primary = Color(0xFFA78BFA),
        bright = Color(0xFFC9BCFF),
        deep = Color(0xFF6D28D9),
        glow = Color(0xFF7C3AED),
        gradientStart = Color(0xFFB39DFF),
        gradientEnd = Color(0xFF6D28D9)
    )
    VelvetAccent.Gold -> AccentPalette(
        primary = Color(0xFFE8C97A),
        bright = Color(0xFFF8E7AE),
        deep = Color(0xFFA87F2E),
        glow = Color(0xFFD4AF37),
        gradientStart = Color(0xFFF3DCA0),
        gradientEnd = Color(0xFFA87A25)
    )
    VelvetAccent.Crimson -> AccentPalette(
        primary = Color(0xFFC21E2C),
        bright = Color(0xFFFF5B5B),
        deep = Color(0xFF5A0710),
        glow = Color(0xFF8E101C),
        gradientStart = Color(0xFFE23A3E),
        gradientEnd = Color(0xFF47060D),
        onAccent = Color(0xFFFFF1F1)
    )
    VelvetAccent.Azure -> AccentPalette(
        primary = Color(0xFF6EA8FF),
        bright = Color(0xFFA9C9FF),
        deep = Color(0xFF1D4ED8),
        glow = Color(0xFF3B82F6),
        gradientStart = Color(0xFF8CBDFF),
        gradientEnd = Color(0xFF1D4ED8)
    )
}

val LocalAccent = staticCompositionLocalOf { paletteFor(VelvetAccent.Amethyst) }

val InstrumentSerif = FontFamily(Font(R.font.instrument_serif_regular))
val InstrumentSerifItalic = FontFamily(Font(R.font.instrument_serif_italic))
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

val VelvetTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 54.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.4).sp
    ),
    displayMedium = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 44.sp,
        lineHeight = 47.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 34.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 27.sp,
        lineHeight = 31.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 22.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.7.sp
    )
)

@Composable
fun VelvetTheme(accent: VelvetAccent, content: @Composable () -> Unit) {
    val palette = paletteFor(accent)
    val scheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onAccent,
        primaryContainer = palette.deep,
        onPrimaryContainer = Color.White,
        secondary = palette.bright,
        onSecondary = palette.onAccent,
        background = Ink,
        onBackground = TextPrimary,
        surface = Surface1,
        onSurface = TextPrimary,
        surfaceVariant = Surface2,
        onSurfaceVariant = TextSecondary,
        outline = Hairline,
        outlineVariant = Hairline,
        error = Color(0xFFFF7A8A)
    )
    CompositionLocalProvider(LocalAccent provides palette) {
        MaterialTheme(colorScheme = scheme, typography = VelvetTypography, content = content)
    }
}
