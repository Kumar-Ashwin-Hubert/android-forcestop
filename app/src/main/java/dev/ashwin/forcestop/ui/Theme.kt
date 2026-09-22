package dev.ashwin.forcestop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.ashwin.forcestop.R

/**
 * Near-monochrome warm neutrals with a single restrained accent.
 *
 * Authored rather than dynamic so the app looks the same in a screenshot as on a phone,
 * and split light/dark so it can follow the system setting.
 */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val raised: Color,
    val outline: Color,
    val outlineSoft: Color,
    val text: Color,
    val textMuted: Color,
    /** Decorative and disabled use only — too light to carry information at AA. */
    val textFaint: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val danger: Color,
    val dangerSoft: Color,
    val actionFill: Color,
    val actionInk: Color,
    val isLight: Boolean,
)

private val LightColors = AppColors(
    background = Color(0xFFFAF8F5),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1EDE6),
    raised = Color(0xFFFFFFFF),
    outline = Color(0xFFE2DCD2),
    outlineSoft = Color(0xFFEDE8E0),
    text = Color(0xFF1C1A17),
    textMuted = Color(0xFF6B655C),
    textFaint = Color(0xFF8A837A),
    accent = Color(0xFF3F6152),
    accentSoft = Color(0xFFE7EEE9),
    onAccent = Color(0xFFF6F8F6),
    danger = Color(0xFFA1473A),
    dangerSoft = Color(0xFFF7EAE7),
    actionFill = Color(0xFF1C1A17),
    actionInk = Color(0xFFFAF8F5),
    isLight = true,
)

private val DarkColors = AppColors(
    background = Color(0xFF131211),
    surface = Color(0xFF1A1917),
    surfaceAlt = Color(0xFF222120),
    raised = Color(0xFF34312C),
    outline = Color(0xFF34312C),
    outlineSoft = Color(0xFF272522),
    text = Color(0xFFF2EEE7),
    textMuted = Color(0xFFA39C92),
    textFaint = Color(0xFF857E74),
    accent = Color(0xFF8FB9A3),
    accentSoft = Color(0xFF1E2724),
    onAccent = Color(0xFF13201A),
    danger = Color(0xFFE08975),
    dangerSoft = Color(0xFF2A1D1A),
    actionFill = Color(0xFFF2EEE7),
    actionInk = Color(0xFF161411),
    isLight = false,
)

private val LocalAppColors = staticCompositionLocalOf { LightColors }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: Int, optical: Float) = Font(
    resId = R.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", optical),
        FontVariation.Setting("SOFT", 20f),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int) = Font(
    resId = R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(100f),
    ),
)

// Fraunces carries an optical-size axis, so it needs one cut per size band. Using the
// display cut at text sizes is what makes a serif look spindly and mis-set.
private val DisplayText = FontFamily(fraunces(500, 26f), fraunces(600, 26f))
private val DisplayLarge = FontFamily(fraunces(600, 72f))

private val Sans = FontFamily(archivo(400), archivo(500), archivo(600))

private val ForceStopTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayLarge,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayText,
        fontWeight = FontWeight.Medium,
        fontSize = 23.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.005).em,
    ),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.5.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

private fun materialScheme(colors: AppColors) = if (colors.isLight) {
    lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        surfaceVariant = colors.surfaceAlt,
        onSurfaceVariant = colors.textMuted,
        outline = colors.outline,
        outlineVariant = colors.outlineSoft,
        error = colors.danger,
    )
} else {
    darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        surfaceVariant = colors.surfaceAlt,
        onSurfaceVariant = colors.textMuted,
        outline = colors.outline,
        outlineVariant = colors.outlineSoft,
        error = colors.danger,
    )
}

@Composable
fun ForceStopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme(colors),
            typography = ForceStopTypography,
            content = content,
        )
    }
}
