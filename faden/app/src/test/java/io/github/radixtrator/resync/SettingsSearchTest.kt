package io.github.radixtrator.resync

import io.github.radixtrator.resync.i18n.AppLanguage
import io.github.radixtrator.resync.i18n.I18n
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.ui.settings.SettingsIndex
import org.junit.After
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SettingsSearchTest {
    @After
    fun reset() = I18n.use(AppLanguage.ENGLISH)

    private fun titles(query: String) = SettingsIndex.search(query).map { it.title }

    private fun assertFinds(query: String, vararg expected: String) {
        val found = titles(query)
        expected.forEach { title -> assertTrue(t(title) in found, "\"$query\" should find \"$title\", found $found") }
    }

    @Test
    fun `every switch and choice on the settings pages is in the index`() {
        // The pages build their rows directly and the index is kept beside them by hand, so this catches a row that
        // was added to a page but not to the index.
        val dir = File("src/main/java/io/github/radixtrator/resync/ui/settings")
        val row = Regex("""\b(?:Toggle|Choice)\(\s*t\("((?:[^"\\]|\\.)*)"""")
        val onPages = dir.listFiles { f -> f.extension == "kt" && f.name != "SettingsSearch.kt" }!!
            .flatMap { f -> row.findAll(f.readText()).map { it.groupValues[1] }.toList() }
            .toSet()
        assertTrue(onPages.size > 40, "found only ${onPages.size} rows; did the pages move?")
        val indexed = SettingsIndex.entries.map { it.title }.toSet()
        val missing = onPages.filter { t(it) !in indexed }
        assertTrue(missing.isEmpty(), "not in SettingsIndex: $missing")
    }

    @Test
    fun `plain English words find their settings`() {
        assertFinds("dark", "Theme", "Dark overlay", "Dark mode")
        assertFinds("font", "Typeface", "Base font size")
        assertFinds("notifications", "Periodically check for new messages", "Messaging")
        assertFinds("mute", "Start videos muted")
        assertFinds("thumbnail", "Post media preview size", "Thumbnails")
        assertFinds("blur", "NSFW posts", "Blur spoiler previews")
        assertFinds("sort", "Default comment sort", "Ask to keep a new sort")
        assertFinds("quality", "HD Reddit video quality")
        assertFinds("one-handed", "Navigation on the right")
        assertFinds("history", "Mark posts as read on scroll", "Hide read posts", "History")
    }

    @Test
    fun `everyday German words find their settings in either language`() {
        for (language in listOf(AppLanguage.ENGLISH, AppLanguage.GERMAN)) {
            I18n.use(language)
            assertFinds("dunkel", "Theme", "Dark overlay", "Dark mode")
            assertFinds("schrift", "Typeface", "Base font size")
            assertFinds("benachrichtigung", "Periodically check for new messages", "Messaging")
            assertFinds("stumm", "Start videos muted")
            assertFinds("unscharf", "NSFW posts", "Blur spoiler previews")
            assertFinds("sortier", "Default comment sort")
            assertFinds("qualität", "HD Reddit video quality")
            assertFinds("gelesen", "Mark posts as read on scroll", "Hide read posts")
            assertFinds("sicherung", "Backup now", "Backup")
            assertFinds("verlauf", "History")
        }
    }
}
