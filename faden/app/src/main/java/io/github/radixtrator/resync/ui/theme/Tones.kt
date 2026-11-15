package io.github.radixtrator.resync.ui.theme

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Perceptual tone-based palettes, with no Android dependency so the maths is unit-testable on the JVM.
 *
 * Colors are built in OKLCH (Bjorn Ottosson's OKLab, https://bottosson.github.io/posts/oklab/): a hue and a
 * chroma that stay put while the tone changes. A tone is 0 (black) to 100 (white) like Material's HCT tone, i.e.
 * CIE L*: it fixes the color's relative luminance, so a pair of tones always has the same WCAG contrast, whatever
 * the hue. Chroma is lowered, when it has to be, until the color fits in sRGB.
 */
object Tones {
    /** The color's OKLCH hue in degrees and its chroma, from an opaque or ARGB [argb]. */
    class Lch(val hue: Float, val chroma: Float)

    fun lchOf(argb: Int): Lch {
        val lab = oklab(linear(argb shr 16 and 0xFF), linear(argb shr 8 and 0xFF), linear(argb and 0xFF))
        val chroma = sqrt(lab[1] * lab[1] + lab[2] * lab[2])
        var hue = Math.toDegrees(atan2(lab[2].toDouble(), lab[1].toDouble())).toFloat()
        if (hue < 0f) hue += 360f
        return Lch(hue, chroma)
    }

    /** A palette of one hue and chroma: [tone] gives the opaque ARGB color at any tone. */
    class Palette(val hue: Float, val chroma: Float) {
        private val cache = HashMap<Int, Int>()

        fun tone(tone: Int): Int = cache.getOrPut(tone) { solve(hue, chroma, tone) }
    }

    /** CIE L* [tone] to relative luminance (0–1). */
    fun luminanceOfTone(tone: Double): Double {
        val fy = (tone + 16.0) / 116.0
        return if (tone > 8.0) fy * fy * fy else tone / 903.2962962962963
    }

    /** WCAG relative luminance of an ARGB color (alpha ignored). */
    fun luminance(argb: Int): Double =
        0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) + 0.0722 * linear(argb and 0xFF)

    /** WCAG contrast ratio of two ARGB colors, 1–21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun encode(linear: Double): Int {
        val c = linear.coerceIn(0.0, 1.0)
        val v = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055
        return (v * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }

    private fun oklab(r: Double, g: Double, b: Double): FloatArray {
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return floatArrayOf(
            (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s).toFloat(),
            (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s).toFloat(),
            (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s).toFloat(),
        )
    }

    /** Linear sRGB of the OKLCH color, unclamped (components outside 0–1 mean it is out of gamut). */
    private fun linearRgb(lightness: Double, chroma: Double, hueRad: Double, out: DoubleArray) {
        val a = chroma * cos(hueRad)
        val b = chroma * sin(hueRad)
        val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).let { it * it * it }
        val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).let { it * it * it }
        val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).let { it * it * it }
        out[0] = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        out[1] = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        out[2] = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    }

    private fun inGamut(c: DoubleArray) = c.all { it >= -1e-6 && it <= 1 + 1e-6 }

    /** The linear rgb at [lightness] and [hueRad] with the most chroma up to [chroma] that fits in sRGB. */
    private fun clamped(lightness: Double, chroma: Double, hueRad: Double, out: DoubleArray) {
        linearRgb(lightness, chroma, hueRad, out)
        if (inGamut(out)) return
        var lo = 0.0
        var hi = chroma
        repeat(24) {
            val mid = (lo + hi) / 2
            linearRgb(lightness, mid, hueRad, out)
            if (inGamut(out)) lo = mid else hi = mid
        }
        linearRgb(lightness, lo, hueRad, out)
    }

    /** The in-gamut color of [hue], at most [chroma], whose relative luminance is that of [tone]. */
    private fun solve(hue: Float, chroma: Float, tone: Int): Int {
        if (tone <= 0) return 0xFF000000.toInt()
        if (tone >= 100) return 0xFFFFFFFF.toInt()
        val hueRad = Math.toRadians(hue.toDouble())
        val target = luminanceOfTone(tone.toDouble())
        val rgb = DoubleArray(3)
        var lo = 0.0
        var hi = 1.0
        repeat(32) {
            val mid = (lo + hi) / 2
            clamped(mid, chroma.toDouble(), hueRad, rgb)
            val y = 0.2126 * rgb[0].coerceIn(0.0, 1.0) + 0.7152 * rgb[1].coerceIn(0.0, 1.0) + 0.0722 * rgb[2].coerceIn(0.0, 1.0)
            if (y < target) lo = mid else hi = mid
        }
        clamped((lo + hi) / 2, chroma.toDouble(), hueRad, rgb)
        return (0xFF shl 24) or (encode(rgb[0]) shl 16) or (encode(rgb[1]) shl 8) or encode(rgb[2])
    }
}

/** Every Material 3 color role Faden sets, as opaque ARGB. Theme.kt turns it into a `ColorScheme`. */
class ToneScheme(
    val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
    val secondary: Int, val onSecondary: Int, val secondaryContainer: Int, val onSecondaryContainer: Int,
    val tertiary: Int, val onTertiary: Int, val tertiaryContainer: Int, val onTertiaryContainer: Int,
    val error: Int, val onError: Int, val errorContainer: Int, val onErrorContainer: Int,
    val background: Int, val onBackground: Int, val surface: Int, val onSurface: Int,
    val surfaceVariant: Int, val onSurfaceVariant: Int, val outline: Int, val outlineVariant: Int,
    val surfaceDim: Int, val surfaceBright: Int,
    val surfaceContainerLowest: Int, val surfaceContainerLow: Int, val surfaceContainer: Int,
    val surfaceContainerHigh: Int, val surfaceContainerHighest: Int,
    val inverseSurface: Int, val inverseOnSurface: Int, val inversePrimary: Int,
) {
    companion object {
        /** Faden's own red: the thread. The palette when no accent is picked. */
        const val FADEN_SEED = 0xFFB4233C.toInt()

        private const val ERROR_HUE = 27f
        private const val ERROR_CHROMA = 0.19f

        /**
         * The scheme for [seed] (ARGB). The accent palette has the seed's hue and chroma, the secondary a third of
         * it, the tertiary half of it at hue +60; neutrals are the seed's hue at very low chroma ([extra]: a lot
         * more of it, so backgrounds and cards are visibly tinted). [intensity] scales every chroma: 0 gives greys,
         * 2 twice the colour.
         */
        fun generate(seed: Int, dark: Boolean, extra: Boolean, intensity: Float): ToneScheme {
            val scale = intensity.coerceIn(0f, 2f)
            val lch = Tones.lchOf(seed)
            val chroma = lch.chroma.coerceIn(0.08f, 0.30f)
            val accent = Tones.Palette(lch.hue, chroma * scale)
            val secondary = Tones.Palette(lch.hue, chroma / 3 * scale)
            val tertiary = Tones.Palette((lch.hue + 60f) % 360f, chroma / 2 * scale)
            val neutral = Tones.Palette(lch.hue, (if (extra) 0.035f else 0.010f) * scale)
            val variant = Tones.Palette(lch.hue, (if (extra) 0.050f else 0.018f) * scale)
            val error = Tones.Palette(ERROR_HUE, ERROR_CHROMA)
            return if (dark) {
                ToneScheme(
                    accent.tone(80), accent.tone(20), accent.tone(30), accent.tone(90),
                    secondary.tone(80), secondary.tone(20), secondary.tone(30), secondary.tone(90),
                    tertiary.tone(80), tertiary.tone(20), tertiary.tone(30), tertiary.tone(90),
                    error.tone(80), error.tone(20), error.tone(30), error.tone(90),
                    neutral.tone(6), neutral.tone(90), neutral.tone(6), neutral.tone(90),
                    variant.tone(30), variant.tone(80), variant.tone(60), variant.tone(30),
                    neutral.tone(6), neutral.tone(24),
                    neutral.tone(4), neutral.tone(10), neutral.tone(12), neutral.tone(17), neutral.tone(22),
                    neutral.tone(90), neutral.tone(20), accent.tone(40),
                )
            } else {
                ToneScheme(
                    accent.tone(40), accent.tone(100), accent.tone(90), accent.tone(10),
                    secondary.tone(40), secondary.tone(100), secondary.tone(90), secondary.tone(10),
                    tertiary.tone(40), tertiary.tone(100), tertiary.tone(90), tertiary.tone(10),
                    error.tone(40), error.tone(100), error.tone(90), error.tone(10),
                    neutral.tone(98), neutral.tone(10), neutral.tone(98), neutral.tone(10),
                    variant.tone(90), variant.tone(30), variant.tone(50), variant.tone(80),
                    neutral.tone(87), neutral.tone(98),
                    neutral.tone(100), neutral.tone(96), neutral.tone(94), neutral.tone(92), neutral.tone(90),
                    neutral.tone(20), neutral.tone(95), accent.tone(80),
                )
            }
        }

        /** [FADEN_SEED]'s scheme, at the default intensity: Faden's default palette. */
        fun faden(dark: Boolean) = generate(FADEN_SEED, dark, extra = false, intensity = 1f)
    }
}
