package io.github.radixtrator.resync

import io.github.radixtrator.resync.data.RECENT_SETTINGS_SEARCHES
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.rememberSettingsSearch
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentSettingsSearchesTest {
    @Test
    fun `picked searches come first, once, trimmed and capped`() {
        var s = Settings()
        s = s.rememberSettingsSearch(" dark ")
        s = s.rememberSettingsSearch("font")
        assertEquals(listOf("font", "dark"), s.recentSettingsSearches)
        // Again, in another case: it moves to the front instead of showing twice.
        s = s.rememberSettingsSearch("Dark")
        assertEquals(listOf("Dark", "font"), s.recentSettingsSearches)
        // Blank queries aren't kept.
        assertEquals(s, s.rememberSettingsSearch("   "))
        repeat(10) { s = s.rememberSettingsSearch("q$it") }
        assertEquals(RECENT_SETTINGS_SEARCHES, s.recentSettingsSearches.size)
        assertEquals("q9", s.recentSettingsSearches.first())
    }
}
