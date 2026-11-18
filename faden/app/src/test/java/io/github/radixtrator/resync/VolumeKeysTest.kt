package io.github.radixtrator.resync

import android.view.KeyEvent
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.ui.common.VolumeKeys
import io.github.radixtrator.resync.ui.settings.SettingsIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VolumeKeysTest {
    private class Recorder : VolumeKeys.Handler {
        val calls = mutableListOf<String>()
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
    }

    private fun press(keyCode: Int, repeat: Int = 0): List<Boolean> = listOf(
        VolumeKeys.handle(keyCode, KeyEvent.ACTION_DOWN, repeat),
        VolumeKeys.handle(keyCode, KeyEvent.ACTION_UP, 0),
    )

    @Test
    fun `volume buttons jump while a page holds them, and change the volume otherwise`() {
        // Nobody holds them: the system gets the press.
        assertEquals(listOf(false, false), press(KeyEvent.KEYCODE_VOLUME_DOWN))

        val page = Recorder()
        VolumeKeys.hold(page)
        try {
            assertEquals(listOf(true, true), press(KeyEvent.KEYCODE_VOLUME_DOWN))
            assertEquals(listOf(true, true), press(KeyEvent.KEYCODE_VOLUME_UP))
            // Holding the button repeats key down; only the first press jumps.
            assertEquals(listOf(true, true), press(KeyEvent.KEYCODE_VOLUME_DOWN, repeat = 3))
            assertEquals(listOf("next", "previous"), page.calls)
            // Other keys pass through.
            assertTrue(!VolumeKeys.handle(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 0))

            // A newer page takes over, and hands them back when it lets go.
            val top = Recorder()
            VolumeKeys.hold(top)
            press(KeyEvent.KEYCODE_VOLUME_DOWN)
            VolumeKeys.release(top)
            press(KeyEvent.KEYCODE_VOLUME_DOWN)
            assertEquals(listOf("next"), top.calls)
            assertEquals(listOf("next", "previous", "next"), page.calls)
        } finally {
            VolumeKeys.release(page)
        }
        assertEquals(listOf(false, false), press(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun `volume key navigation is off by default and in settings search`() {
        assertTrue(!Settings().volumeKeyNavigation)
        assertTrue("Volume key navigation" in SettingsIndex.search("volume").map { it.title })
    }
}
