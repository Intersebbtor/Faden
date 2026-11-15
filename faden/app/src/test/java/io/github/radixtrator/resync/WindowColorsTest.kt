package io.github.radixtrator.resync

import androidx.compose.ui.graphics.toArgb
import io.github.radixtrator.resync.ui.theme.fadenColorScheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The window shown before Compose draws (themes.xml in res/values and values-night) must be the default theme's `background`, or the
 * app flashes a different color at startup. If the palette changes, this says what to put in the XML.
 */
class WindowColorsTest {
    private fun res(path: String, name: String): String {
        val xml = File("src/main/res/$path/themes.xml").readText()
        return Regex("""<color name="$name">(#[0-9A-Fa-f]{8})</color>""").find(xml)?.groupValues?.get(1)
            ?: error("no $name in $path/themes.xml")
    }

    private fun hex(argb: Int) = "#%08X".format(argb)

    @Test
    fun startupWindowMatchesDefaultBackground() {
        val light = hex(fadenColorScheme(dark = false, black = false, seed = null, extra = false).background.toArgb())
        val dark = hex(fadenColorScheme(dark = true, black = false, seed = null, extra = false).background.toArgb())
        assertEquals(light, res("values", "window_background").uppercase(), "values/themes.xml window_background should be $light")
        assertEquals(dark, res("values-night", "window_background").uppercase(), "values-night/themes.xml window_background should be $dark")
    }
}
