package io.github.radixtrator.resync

import io.github.radixtrator.resync.i18n.AppLanguage
import io.github.radixtrator.resync.i18n.I18n
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.i18n.tn
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class I18nTest {
    @After
    fun reset() = I18n.use(AppLanguage.ENGLISH)

    @Test
    fun englishIsTheSource() {
        I18n.use(AppLanguage.ENGLISH)
        assertEquals("Language", t("Language"))
        assertEquals("Post", io.github.radixtrator.resync.i18n.tc("noun", "Post"))
        assertEquals("u/bob and 3", t("u/{0} and {1}", "bob", 3))
        assertEquals("1 comment", tn(1, "{0} comment", "{0} comments", 1))
        assertEquals("2 comments", tn(2, "{0} comment", "{0} comments", 2))
    }

    @Test
    fun germanLooksUpAndFallsBack() {
        I18n.use(AppLanguage.GERMAN)
        assertEquals("Sprache", t("Language"))
        assertEquals("Pfosten", io.github.radixtrator.resync.i18n.tc("noun", "Post"))
        assertEquals("Not translated anywhere", t("Not translated anywhere"))
        assertEquals("x {5}", t("x {5}", "only one"))
    }

    @Test
    fun systemPicksATranslatedLanguageOrEnglish() {
        I18n.use(AppLanguage.SYSTEM, Locale.GERMANY)
        assertEquals(AppLanguage.GERMAN, I18n.language)
        I18n.use(AppLanguage.SYSTEM, Locale.FRANCE)
        assertEquals(AppLanguage.ENGLISH, I18n.language)
    }
}
