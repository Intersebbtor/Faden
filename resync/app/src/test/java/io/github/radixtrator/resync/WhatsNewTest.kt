package io.github.radixtrator.resync

import io.github.radixtrator.resync.data.AppUpdates
import io.github.radixtrator.resync.data.VersionNotes
import io.github.radixtrator.resync.data.WhatsNew
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsNewTest {
    private val sample = """
        # What's new in Resync
        <!-- a comment -->

        ## 0.6.0
        - Pictures in comments
        - Update **notices**

        ## 0.5.0
        - Reddit links open here
    """.trimIndent()

    @Test
    fun `sections parse newest first`() {
        assertEquals(
            listOf(VersionNotes("0.6.0", listOf("Pictures in comments", "Update notices")), VersionNotes("0.5.0", listOf("Reddit links open here"))),
            WhatsNew.parse(sample),
        )
    }

    @Test
    fun `versions compare by number, ignoring suffixes`() {
        assertTrue(WhatsNew.compare("0.10.0", "0.9.9") > 0)
        assertTrue(WhatsNew.compare("v0.5.0", "0.4.2") > 0)
        assertEquals(0, WhatsNew.compare("0.5.0-debug", "0.5.0"))
        assertTrue(WhatsNew.compare("0.5", "0.5.1") < 0)
    }

    @Test
    fun `an update shows every version since the last one seen`() {
        val all = WhatsNew.parse(sample)
        assertEquals(listOf("0.6.0", "0.5.0"), WhatsNew.since(all, "0.4.2", "0.6.0").map { it.version })
        assertEquals(listOf("0.6.0"), WhatsNew.since(all, "0.5.0", "0.6.0").map { it.version })
        // No record: just this version's.
        assertEquals(listOf("0.5.0"), WhatsNew.since(all, null, "0.5.0").map { it.version })
        assertTrue(WhatsNew.since(all, "0.6.0", "0.6.0").isEmpty())
    }

    @Test
    fun `release body bullets are read back`() {
        val body = "### What's new\n\n- One\n- Two\n\n**Install or update:** add it in Obtainium.\n\n## What's Changed\n* a commit by @someone"
        assertEquals(listOf("One", "Two"), WhatsNew.releaseItems(body))
        assertTrue(WhatsNew.releaseItems("**Install or update:** only").isEmpty())

        val json = Json.parseToJsonElement(
            """{"tag_name":"v0.6.0","html_url":"https://github.com/x/releases/tag/v0.6.0","draft":false,"prerelease":false,"body":${Json.encodeToString(String.serializer(), body)}}""",
        ).jsonObject
        val release = AppUpdates.parseRelease(json)!!
        assertEquals("0.6.0", release.version)
        assertEquals(listOf("One", "Two"), release.items)
        assertNull(AppUpdates.parseRelease(Json.parseToJsonElement("""{"tag_name":"v1.0.0-beta","prerelease":true}""").jsonObject))
    }

    @Test
    fun `the bundled list parses and has notes for its newest version`() {
        val notes = WhatsNew.parse(File("src/main/assets/whats-new.md").readText())
        assertTrue(notes.isNotEmpty())
        assertTrue(notes.all { it.items.isNotEmpty() })
        // Newest first, so the update notice and "Resync was updated" lead with the latest.
        assertEquals(notes.sortedWith { a, b -> WhatsNew.compare(b.version, a.version) }, notes)
    }
}
