package io.github.radixtrator.resync.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.github.radixtrator.resync.R
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.ThemeMode
import io.github.radixtrator.resync.data.ThemePalette
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

/** Where Faden puts its signature accent: every FAB is the tertiary container, which is the upvote orange in the default palette. */
object FadenAccents {
    val fabContainer: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
    val onFab: Color @Composable get() = MaterialTheme.colorScheme.onTertiaryContainer
}

object FadenColors {
    val Upvote = Color(0xFFFF7500)
    val Downvote = Color(0xFF9494FF)
    val Nsfw = Color(0xFFE5484D)
    val Moderator = Color(0xFF4CAF50)
    val Saved = Color(0xFFECC940)
    val Reply = Color(0xFF3ABF6D)
    val Hide = Color(0xFFB71C1C)

    /**
     * Comment thread rails, cycling by depth: the orange thread first (depth 0, the upvote orange a little deeper), then
     * teal, blue, violet and rose. Each keeps 3:1 against a card and against the window, in light and dark.
     */
    val Depth = listOf(
        Color(0xFFE36000), // orange thread
        Color(0xFF239379), // teal
        Color(0xFF4F7FE0), // blue
        Color(0xFF9B6BD6), // violet
        Color(0xFFD4478A), // rose
    )
}

/**
 * Figtree, Faden's own typeface: res/font/figtree.ttf and figtree_italic.ttf are the variable fonts (wght 300-900,
 * OFL, third_party/figtree), so one file serves every weight. It has nothing below 300, so lighter weights clamp to Light.
 */
private val Figtree = FontFamily(
    (100..900 step 100).flatMap { weight ->
        val axis = FontVariation.Settings(FontVariation.weight(weight.coerceAtLeast(300)))
        listOf(
            Font(R.font.figtree, FontWeight(weight), FontStyle.Normal, variationSettings = axis),
            Font(R.font.figtree_italic, FontWeight(weight), FontStyle.Italic, variationSettings = axis),
        )
    },
)

/**
 * Roboto, for those who pick it (Faden's own face is Figtree). Sync draws everything in it rather than the device
 * font (One UI's is wider, and drew italics upright). res/font/roboto.ttf is Android's variable Roboto: one file,
 * every weight.
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
 * 34sp header all measure so in sync-reference), so the Roboto and system families use this in place of
 * Material's own letter spacing. Figtree keeps Material's.
 */
private val SYNC_LETTER_SPACING = 0.015.em

/** Material's roles in [family], with [spacing] in place of Material's letter spacing when given. */
private fun typographyFor(family: FontFamily, spacing: TextUnit? = null) = Typography().run {
    fun TextStyle.styled() = if (spacing == null) copy(fontFamily = family) else copy(fontFamily = family, letterSpacing = spacing)
    copy(
        displayLarge = displayLarge.styled(),
        displayMedium = displayMedium.styled(),
        displaySmall = displaySmall.styled(),
        headlineLarge = headlineLarge.styled(),
        headlineMedium = headlineMedium.styled(),
        headlineSmall = headlineSmall.styled(),
        titleLarge = titleLarge.styled(),
        titleMedium = titleMedium.styled(),
        titleSmall = titleSmall.styled(),
        bodyLarge = bodyLarge.styled(),
        bodyMedium = bodyMedium.styled(),
        bodySmall = bodySmall.styled(),
        labelLarge = labelLarge.styled(),
        labelMedium = labelMedium.styled(),
        labelSmall = labelSmall.styled(),
    )
}

private val Typographies by lazy {
    mapOf(
        Typeface.FIGTREE to typographyFor(Figtree),
        Typeface.ROBOTO to typographyFor(Roboto, SYNC_LETTER_SPACING),
        Typeface.SANS to typographyFor(FontFamily.SansSerif, SYNC_LETTER_SPACING),
        Typeface.SERIF to typographyFor(FontFamily.Serif, SYNC_LETTER_SPACING),
        Typeface.MONOSPACE to typographyFor(FontFamily.Monospace, SYNC_LETTER_SPACING),
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

/**
 * Faden's shape scale, rounder than Sync's 6-16dp corners. Which role is for what:
 *  - [Shapes.extraSmall] (6dp): tiny things inside other things: badges, flair tags, mini previews' inner blocks, tooltips.
 *  - [Shapes.small] (10dp): chips and pills (header and filter chips, segmented buttons), menus, text fields, snackbars.
 *  - [Shapes.medium] (16dp): cards and tiles: posts, comment cards, media, swipe rows, list groups.
 *  - [Shapes.large] (24dp): sheets and panels that sit on an edge, large buttons and floating buttons.
 *  - [Shapes.extraLarge] (32dp): dialogs and full-height modal surfaces.
 * The "increased" and extra-extra-large steps sit between and beyond those, for the components that ask for them
 * (expressive buttons, large app bars, bottom sheets).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val FadenShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
    extraLargeIncreased = RoundedCornerShape(40.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/**
 * The accent choices offered in Settings › Theme color, after "Default" (Faden blue) and "Classic" (ARGB):
 * Faden red (#B4233C, the old default), rust, ochre, moss, teal, blue, violet and plum.
 */
val THEME_SEEDS = listOf(
    0xFFB4233C.toInt(), 0xFFC2581C.toInt(), 0xFFA0841A.toInt(), 0xFF3E8E41.toInt(), 0xFF00897B.toInt(),
    0xFF2F6FB5.toInt(), 0xFF6750A4.toInt(), 0xFF8E4585.toInt(),
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
 * Faden's own palette (not Material You), and pure black when [black] (dark only). With a [seed] (ARGB) picked it is
 * generated from it ([extra] colorful or not, at Color [intensity]), whichever [palette]; without one it is
 * [palette]'s own: Faden's blue and orange, or Sync's sampled "Classic" colors, which are fixed. Theme management draws
 * its previews with this too.
 */
fun fadenColorScheme(
    dark: Boolean,
    black: Boolean,
    seed: Int?,
    extra: Boolean,
    intensity: Float = 1f,
    palette: ThemePalette = ThemePalette.FADEN,
): ColorScheme {
    val base = when {
        seed != null -> ToneScheme.generate(seed, dark, extra, intensity).toColorScheme(dark)
        palette == ThemePalette.CLASSIC -> if (dark) DarkColors else LightColors
        // Extra colorful and the intensity only apply to a picked accent.
        else -> ToneScheme.faden(dark).toColorScheme(dark)
    }
    return if (black && dark) base.blackened() else base
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
    val palette: ThemePalette = ThemePalette.FADEN,
    val typeface: Typeface = Typeface.FIGTREE,
) {
    companion object {
        fun of(settings: Settings) = ThemeSpec(
            settings.theme, settings.dynamicColor, settings.seedColor, settings.extraColorful, settings.colorIntensity, settings.palette, settings.typeface,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val scheme = remember(dark, black, spec.dynamicColor, spec.seedColor, spec.extraColorful, spec.colorIntensity, spec.palette, context) {
        if (spec.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (black) dynamic.blackened() else dynamic
        } else {
            fadenColorScheme(dark, black, spec.seedColor, spec.extraColorful, spec.colorIntensity, spec.palette)
        }
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = FadenShapes,
            typography = Typographies.getValue(spec.typeface),
            content = content,
        )
    }
}

/** Whether the resolved theme is dark, for actions that flip it (Sync's "Dark mode" quick action). */
val LocalDarkTheme = staticCompositionLocalOf { false }
