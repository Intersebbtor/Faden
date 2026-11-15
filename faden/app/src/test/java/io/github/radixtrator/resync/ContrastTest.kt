package io.github.radixtrator.resync

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.radixtrator.resync.data.ThemePalette
import io.github.radixtrator.resync.ui.theme.FadenColors
import io.github.radixtrator.resync.ui.theme.THEME_SEEDS
import io.github.radixtrator.resync.ui.theme.ToneScheme
import io.github.radixtrator.resync.ui.theme.Tones
import io.github.radixtrator.resync.ui.theme.fadenColorScheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** WCAG contrast of the colors the theme generates, over every seed, mode, intensity and Extra colorful. */
class ContrastTest {
    private fun Color.argb() = toArgb()

    private class Case(val name: String, val seed: Int?, val palette: ThemePalette)

    private val cases = buildList {
        add(Case("default", null, ThemePalette.FADEN))
        add(Case("classic", null, ThemePalette.CLASSIC))
        // Intensity and Extra colorful don't touch the default scheme, but Faden blue as a picked seed takes them.
        add(Case("faden blue seed", ToneScheme.FADEN_SEED, ThemePalette.FADEN))
        THEME_SEEDS.forEach { add(Case("seed %06X".format(it and 0xFFFFFF), it, ThemePalette.FADEN)) }
    }

    /** Failures of [check] over all cases and settings; Classic's are returned apart, it being Sync's fixed palette. */
    private fun failures(check: (scheme: androidx.compose.material3.ColorScheme) -> String?): Pair<List<String>, List<String>> {
        val bad = mutableListOf<String>()
        val classic = mutableListOf<String>()
        for (case in cases) for (mode in listOf("light", "dark", "black")) {
            for (intensity in listOf(0f, 1f, 2f)) for (extra in listOf(false, true)) {
                val scheme = fadenColorScheme(
                    dark = mode != "light", black = mode == "black", seed = case.seed, extra = extra,
                    intensity = intensity, palette = case.palette,
                )
                val problem = check(scheme) ?: continue
                val line = "${case.name} $mode intensity=$intensity extra=$extra: $problem"
                if (case.palette == ThemePalette.CLASSIC) classic += line else bad += line
            }
        }
        // Classic ignores intensity and Extra colorful, so report each of its failures once.
        return bad to classic.map { it.replace(Regex(" intensity=\\S+ extra=\\S+"), "") }.distinct()
    }

    private fun ratio(a: Color, b: Color) = Tones.contrast(a.argb(), b.argb())

    private fun need(label: String, a: Color, b: Color, min: Double): String? =
        ratio(a, b).let { if (it < min) "$label %.2f < $min".format(it) else null }

    @Test
    fun generatedSchemesMeetContrast() {
        val (bad, classic) = failures { s ->
            listOfNotNull(
                need("onPrimary/primary", s.onPrimary, s.primary, 4.5),
                need("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer, 4.5),
                need("onSurface/surface", s.onSurface, s.surface, 7.0),
                need("onSurfaceVariant/surface", s.onSurfaceVariant, s.surface, 4.5),
                need("onSurfaceVariant/surfaceContainerLow", s.onSurfaceVariant, s.surfaceContainerLow, 4.5),
                need("primary/surface", s.primary, s.surface, 3.0),
            ).joinToString("; ").ifEmpty { null }
        }
        // Classic is Sync's fixed palette: its failures are how Sync shipped, so they're printed, not asserted.
        if (classic.isNotEmpty()) println("Classic (Sync's palette) contrast shortfalls:\n" + classic.joinToString("\n"))
        assertTrue(bad.isEmpty(), "Contrast failures:\n" + bad.joinToString("\n"))
    }

    @Test
    fun depthRailsStandOutOnCards() {
        for (dark in listOf(false, true)) {
            val card = fadenColorScheme(dark, black = false, seed = null, extra = false).surfaceContainerLow
            FadenColors.Depth.forEachIndexed { depth, rail ->
                val ratio = ratio(rail, card)
                assertTrue(ratio >= 3.0, "rail $depth vs ${if (dark) "dark" else "light"} card: %.2f".format(ratio))
            }
        }
        assertTrue(FadenColors.Depth.size == 5)
    }

    @Test
    fun fadenSeedMatchesDefaultScheme() {
        for (dark in listOf(false, true)) {
            val generated = ToneScheme.faden(dark)
            val default = fadenColorScheme(dark, black = false, seed = null, extra = false)
            assertTrue(generated.primary == default.primary.argb() && generated.background == default.background.argb())
        }
    }
}
