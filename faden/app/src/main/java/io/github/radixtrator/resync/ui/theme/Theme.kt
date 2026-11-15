package io.github.radixtrator.resync.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import io.github.radixtrator.resync.R
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.ThemeMode
import io.github.radixtrator.resync.data.Typeface

// Sync builds its theme from Monet tonal palettes (N1/N2 neutrals, A1–A3 accents). These are the tones
// Sync showed on the reference device (sync-reference/SYNC_PARITY_LOG.md §1), mapped onto M3 roles the
// way Sync uses them: window, cards/sheets, nav bar and selection pills are successive neutral steps.
private val LightColors = lightColorScheme(
    primary = Color(0xFF505E81), // A1-600
    onPrimary = Color.White,
    primaryContainer = Color(0xFF505E81), // Sync's FAB is the saturated accent in light mode too
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF575E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC0C6DC), // A2-200: nav indicator
    onSecondaryContainer = Color(0xFF151B2C),
    tertiary = Color(0xFF6A567F),
    tertiaryContainer = Color(0xFFEFDBFF),
    background = Color(0xFFEEEDF3),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFEEEDF3),
    onSurface = Color(0xFF1A1B20),
    surfaceContainerLowest = Color(0xFFEEEDF3),
    surfaceContainerLow = Color(0xFFFEFBFF), // cards, sheets, chips
    surfaceContainer = Color(0xFFE1E2ED), // bottom nav
    surfaceContainerHigh = Color(0xFFE3E2E7),
    surfaceContainerHighest = Color(0xFFDCDBE2),
    surfaceVariant = Color(0xFFF1F0F6), // neutral flair chips
    onSurfaceVariant = Color(0xFF5C5E67),
    outline = Color(0xFF8E909A),
    outlineVariant = Color(0xFFC5C6D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DABD1), // A1-300: Sync's accent text (sub names, links, headers)
    onPrimary = Color(0xFF0B1A3A),
    primaryContainer = Color(0xFF394668), // A1-700: FAB
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFC0C6DC),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF454957), // nav indicator
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFD5BDEC),
    tertiaryContainer = Color(0xFF513F66),
    background = Color(0xFF121418),
    onBackground = Color(0xFFE6E5EC),
    surface = Color(0xFF121418),
    onSurface = Color(0xFFE6E5EC),
    surfaceContainerLowest = Color(0xFF121418),
    surfaceContainerLow = Color(0xFF1C1D21), // cards, sheets, chips
    surfaceContainer = Color(0xFF27292D), // bottom nav
    surfaceContainerHigh = Color(0xFF2D2F3A), // selected rows, highlighted comment
    surfaceContainerHighest = Color(0xFF34363E),
    surfaceVariant = Color(0xFF282A2E), // neutral flair chips
    onSurfaceVariant = Color(0xFFA3A5AD),
    outline = Color(0xFF8E909A),
    outlineVariant = Color(0xFF2E3038),
)

object FadenColors {
    val Upvote = Color(0xFFFF7500)
    val Downvote = Color(0xFF9494FF)
    val Nsfw = Color(0xFFE5484D)
    val Moderator = Color(0xFF4CAF50)
    val Saved = Color(0xFFECC940)
    val Reply = Color(0xFF3ABF6D)
    val Hide = Color(0xFFB71C1C)

    /** Comment thread rails, cycling by depth (Sync's order: blue, purple, green, orange, pink). */
    val Depth = listOf(
        Color(0xFF4484F6),
        Color(0xFF9C27B0),
        Color(0xFF4CAF50),
        Color(0xFFFF9800),
        Color(0xFFE91E63),
    )
}

/**
 * Sync bundles Roboto and draws everything in it rather than the device font (One UI's is wider, and
 * drew italics upright). res/font/roboto.ttf is Android's variable Roboto: one file, every weight.
 */
private val Roboto = FontFamily(
    (100..900 step 100).flatMap { weight ->
        listOf(FontStyle.Normal, FontStyle.Italic).map { style ->
            Font(R.font.roboto, FontWeight(weight), style, variationSettings = FontVariation.Settings(FontWeight(weight), style))
        }
    },
)

/**
 * Sync's TextViews set Roboto about 1.5% wider than Compose does at the same size (titles, bodies and the
 * 34sp header all measure so in sync-reference), so this stands in for Material's own letter spacing.
 */
private val SYNC_LETTER_SPACING = 0.015.em

/** Material's roles in [family], spaced like Sync's text. */
private fun typographyFor(family: FontFamily) = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        displayMedium = displayMedium.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        displaySmall = displaySmall.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        headlineLarge = headlineLarge.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        headlineMedium = headlineMedium.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        headlineSmall = headlineSmall.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        titleLarge = titleLarge.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        titleMedium = titleMedium.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        titleSmall = titleSmall.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        bodyLarge = bodyLarge.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        bodyMedium = bodyMedium.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        bodySmall = bodySmall.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        labelLarge = labelLarge.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        labelMedium = labelMedium.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
        labelSmall = labelSmall.copy(fontFamily = family, letterSpacing = SYNC_LETTER_SPACING),
    )
}

private val Typographies by lazy {
    mapOf(
        Typeface.ROBOTO to typographyFor(Roboto),
        Typeface.SANS to typographyFor(FontFamily.SansSerif),
        Typeface.SERIF to typographyFor(FontFamily.Serif),
        Typeface.MONOSPACE to typographyFor(FontFamily.Monospace),
    )
}

/** A [ToneScheme] as Material roles. */
private fun ToneScheme.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary), onPrimary = Color(onPrimary), primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary), secondary = Color(secondary), onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer), onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary), onTertiary = Color(onTertiary), tertiaryContainer = Color(tertiaryContainer), onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background), onBackground = Color(onBackground), surface = Color(surface), onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant), onSurfaceVariant = Color(onSurfaceVariant), surfaceTint = Color(primary),
        inverseSurface = Color(inverseSurface), inverseOnSurface = Color(inverseOnSurface),
        error = Color(error), onError = Color(onError), errorContainer = Color(errorContainer), onErrorContainer = Color(onErrorContainer),
        outline = Color(outline), outlineVariant = Color(outlineVariant),
        surfaceDim = Color(surfaceDim), surfaceBright = Color(surfaceBright),
        surfaceContainerLowest = Color(surfaceContainerLowest), surfaceContainerLow = Color(surfaceContainerLow), surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh), surfaceContainerHighest = Color(surfaceContainerHighest),
    )
}

/** The accent choices offered in Settings › Theme color, after "Default" (ARGB). */
val THEME_SEEDS = listOf(
    0xFF4484F6.toInt(), 0xFF9C27B0.toInt(), 0xFFE91E63.toInt(), 0xFFE5484D.toInt(),
    0xFFFF9800.toInt(), 0xFF4CAF50.toInt(), 0xFF009688.toInt(), 0xFF795548.toInt(),
)

/** Pure black surfaces for OLED screens; cards stay a hair lighter so they still read as cards. */
private fun ColorScheme.blackened() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0C0E),
    surfaceContainer = Color(0xFF111316),
    outlineVariant = Color(0xFF1F2329),
)

/**
 * Faden's own palette (not Material You): Sync's sampled Monet colors, or tone-based colors generated from [seed]
 * (ARGB) when one is picked ([extra] colorful or not, at Color [intensity]), and pure black when [black] (dark only). Theme management
 * draws its previews with this too.
 */
fun fadenColorScheme(dark: Boolean, black: Boolean, seed: Int?, extra: Boolean, intensity: Float = 1f): ColorScheme {
    val base = if (dark) DarkColors else LightColors
    val tinted = if (seed != null) ToneScheme.generate(seed, dark, extra, intensity).toColorScheme(dark) else base
    return if (black && dark) tinted.blackened() else tinted
}

/**
 * Sync's "Base font size" is "Smaller" by default on phones, and it shrinks every sp in the app by this
 * much: its 14sp rows and bodies, 16sp titles and 20sp sheet headers all measure at 0.9x the device's
 * font scale in sync-reference. MainActivity applies it to its configuration, so sheets, menus and
 * dialogs (their own windows) get it too. Sync's settings screen keeps full-size text; see [FullSizeText].
 */
const val BASE_FONT_SCALE = 0.9f

/** The base font scale in force (the chosen [BaseFontSize]), which MainActivity applied to its configuration. */
val LocalBaseFontScale = staticCompositionLocalOf { BASE_FONT_SCALE }

/** [content] at the device's own font scale, without the base font size, like Sync's settings. */
@Composable
fun FullSizeText(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale / LocalBaseFontScale.current), content = content)
}

/** The settings that decide how the app is themed; [FadenTheme] takes only these so unrelated settings writes leave the theme alone. */
@Immutable
data class ThemeSpec(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val seedColor: Int? = null,
    val extraColorful: Boolean = false,
    val colorIntensity: Float = 1.0f,
    val typeface: Typeface = Typeface.ROBOTO,
) {
    companion object {
        fun of(settings: Settings) = ThemeSpec(
            settings.theme, settings.dynamicColor, settings.seedColor, settings.extraColorful, settings.colorIntensity, settings.typeface,
        )
    }
}

@Composable
fun FadenTheme(spec: ThemeSpec, content: @Composable () -> Unit) {
    val dark = when (spec.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val context = LocalContext.current
    val black = spec.theme == ThemeMode.BLACK
    // MaterialTheme provides its colors through a static local and ColorScheme has no equals, so a fresh instance
    // on every call would recompose the whole app on any settings write.
    val scheme = remember(dark, black, spec.dynamicColor, spec.seedColor, spec.extraColorful, spec.colorIntensity, context) {
        if (spec.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (black) dynamic.blackened() else dynamic
        } else {
            fadenColorScheme(dark, black, spec.seedColor, spec.extraColorful, spec.colorIntensity)
        }
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typographies.getValue(spec.typeface),
            content = content,
        )
    }
}

/** Whether the resolved theme is dark, for actions that flip it (Sync's "Dark mode" quick action). */
val LocalDarkTheme = staticCompositionLocalOf { false }
