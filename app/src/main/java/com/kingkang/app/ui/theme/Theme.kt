package com.kingkang.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * A deliberately quiet palette: a pure white canvas, neutral gray cards, near-black
 * primary actions, and a single muted green reserved for "running / ready" state. The
 * terminal keeps its own dark canvas, but the chat around it stays bright and uncrowded.
 *
 * The dark theme is the same idea after sundown: soft charcoal surfaces rather than
 * pure black, the same muted green, and the primary action simply inverts to light.
 */

data class KingKangPalette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceVariant: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentDim: Color,
    val onAccent: Color,
    val success: Color,
    val blue: Color,
    val amber: Color,
    val red: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val errorContainer: Color,
    val errorBorder: Color,
    val errorText: Color,
    val errorTextMuted: Color
)

private val LightPalette = KingKangPalette(
    dark = false,
    // Pure white canvas; cards and controls stay a neutral gray so they read as
    // surfaces without reintroducing a warm tint.
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF8F8F8),
    surfaceRaised = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFFECECEC),
    border = Color(0xFFE4E4E4),
    borderStrong = Color(0xFFC9C9C9),
    accent = Color(0xFF2B2E2F),
    accentSoft = Color(0xFF5B6060),
    accentDim = Color(0xFFF1F1F1),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF2F7A57),
    blue = Color(0xFF3D6592),
    amber = Color(0xFF8A641E),
    red = Color(0xFFA34646),
    textPrimary = Color(0xFF1D1F1F),
    textSecondary = Color(0xFF5C6060),
    textMuted = Color(0xFF8A8D8A),
    errorContainer = Color(0xFFFBE9E9),
    errorBorder = Color(0xFFEBC9C9),
    errorText = Color(0xFF8F3B3B),
    errorTextMuted = Color(0xFFA85A5A)
)

private val DarkPalette = KingKangPalette(
    dark = true,
    background = Color(0xFF141517),
    surface = Color(0xFF1B1D1F),
    surfaceRaised = Color(0xFF1F2123),
    surfaceVariant = Color(0xFF26292B),
    border = Color(0xFF2C2F31),
    borderStrong = Color(0xFF3B3F41),
    accent = Color(0xFFE7E8E3),
    accentSoft = Color(0xFFC3C7C3),
    accentDim = Color(0xFF24272A),
    onAccent = Color(0xFF16181A),
    success = Color(0xFF6FB593),
    blue = Color(0xFF87ABD1),
    amber = Color(0xFFCDA962),
    red = Color(0xFFDB8D8D),
    textPrimary = Color(0xFFE8EAE6),
    textSecondary = Color(0xFFADB1AF),
    textMuted = Color(0xFF828785),
    errorContainer = Color(0xFF332223),
    errorBorder = Color(0xFF4D3233),
    errorText = Color(0xFFEBB2B2),
    errorTextMuted = Color(0xFFCB9292)
)

private val LocalPalette = staticCompositionLocalOf { LightPalette }

/** True while the dark palette is active; useful for platform surfaces like the WebView. */
val LocalKingKangDark = staticCompositionLocalOf { false }

val Background: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.background
val Surface: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
val SurfaceRaised: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceRaised
val SurfaceVariant: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceVariant
val Border: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.border
val BorderStrong: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.borderStrong

/** The primary fill; pair it with [OnAccent]. */
val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val AccentSoft: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.accentSoft
val AccentDim: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.accentDim
val OnAccent: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.onAccent
val Success: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.success
val Blue: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.blue
val Amber: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.amber
val Red: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.red
val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.textPrimary
val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.textSecondary
val TextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.textMuted

val ErrorContainer: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.errorContainer
val ErrorBorder: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.errorBorder
val ErrorText: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.errorText
val ErrorTextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.errorTextMuted

/** The terminal canvas is the one surface that intentionally stays dark. */
val TerminalBackground = Color(0xFF0F1112)
val TerminalOverlay = Color(0xFF241A05)

private fun paletteScheme(palette: KingKangPalette) = with(palette) {
    if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentDim,
            onPrimaryContainer = textPrimary,
            secondary = success,
            onSecondary = onAccent,
            tertiary = amber,
            onTertiary = onAccent,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = textSecondary,
            outline = borderStrong,
            outlineVariant = border,
            error = red,
            onError = onAccent,
            errorContainer = errorContainer,
            onErrorContainer = errorText,
            inverseSurface = textPrimary,
            inverseOnSurface = background,
            scrim = Color(0x99000000)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentDim,
            onPrimaryContainer = textPrimary,
            secondary = success,
            onSecondary = onAccent,
            tertiary = amber,
            onTertiary = onAccent,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = textSecondary,
            outline = borderStrong,
            outlineVariant = border,
            error = red,
            onError = onAccent,
            errorContainer = errorContainer,
            onErrorContainer = errorText,
            inverseSurface = textPrimary,
            inverseOnSurface = background,
            scrim = Color(0x66000000)
        )
    }
}

private val LightColorScheme = paletteScheme(LightPalette)
private val DarkColorScheme = paletteScheme(DarkPalette)

private val KingKangTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.6).sp
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 25.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 23.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
        labelSmall = base.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Medium
        )
    )
}

private val KingKangShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

/**
 * Theme mode values persisted by [com.kingkang.app.data.SettingsStore]. "system" resolves
 * through the platform's light/dark setting; the Harness WebView receives the same value.
 */
enum class ThemeMode(val key: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: System
    }
}

@Composable
fun KingKangTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalKingKangDark provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = KingKangTypography,
            shapes = KingKangShapes,
            content = content
        )
    }
}
