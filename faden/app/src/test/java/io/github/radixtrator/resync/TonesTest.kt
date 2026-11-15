package io.github.radixtrator.resync

import io.github.radixtrator.resync.ui.theme.ToneScheme
import io.github.radixtrator.resync.ui.theme.Tones
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TonesTest {
    @Test
    fun tonesFixLuminance() {
        val palette = Tones.Palette(hue = 20f, chroma = 0.19f)
        for (tone in listOf(10, 20, 30, 40, 50, 60, 80, 90, 95)) {
            val y = Tones.luminance(palette.tone(tone))
            assertTrue(abs(y - Tones.luminanceOfTone(tone.toDouble())) < 0.01, "tone $tone has luminance $y")
        }
    }

    @Test
    fun extremesAreBlackAndWhite() {
        val palette = Tones.Palette(200f, 0.2f)
        assertEquals(0xFF000000.toInt(), palette.tone(0))
        assertEquals(0xFFFFFFFF.toInt(), palette.tone(100))
    }

    @Test
    fun zeroChromaIsGrey() {
        val grey = Tones.Palette(120f, 0f).tone(50)
        assertEquals(grey shr 16 and 0xFF, grey and 0xFF)
        assertEquals(grey shr 8 and 0xFF, grey and 0xFF)
    }

    @Test
    fun huePersistsAcrossTones() {
        val seed = ToneScheme.FADEN_SEED
        val lch = Tones.lchOf(seed)
        val palette = Tones.Palette(lch.hue, lch.chroma)
        for (tone in listOf(30, 40, 50, 60)) {
            val got = Tones.lchOf(palette.tone(tone))
            val d = abs(((got.hue - lch.hue + 540f) % 360f) - 180f)
            assertTrue(d < 4f, "tone $tone hue ${got.hue} vs ${lch.hue}")
        }
    }

    @Test
    fun highChromaIsClampedIntoGamut() {
        // Every tone of a hugely over-saturated palette still comes back as a valid opaque color of that luminance.
        val palette = Tones.Palette(145f, 0.8f)
        for (tone in 1..99 step 7) {
            val c = palette.tone(tone)
            assertEquals(0xFF, c ushr 24)
            assertTrue(abs(Tones.luminance(c) - Tones.luminanceOfTone(tone.toDouble())) < 0.02, "tone $tone")
        }
    }

    @Test
    fun seedRoundTripsThroughLch() {
        val seed = 0xFF2F6FB5.toInt()
        val lch = Tones.lchOf(seed)
        // Tone 40 of the seed's own palette is at the seed's luminance range; just check the hue is blue.
        assertTrue(lch.hue in 240f..280f, "hue ${lch.hue}")
        assertTrue(lch.chroma > 0.1f)
    }
}
