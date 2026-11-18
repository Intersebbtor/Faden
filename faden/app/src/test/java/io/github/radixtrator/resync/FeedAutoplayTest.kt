package io.github.radixtrator.resync

import io.github.radixtrator.resync.data.Autoplay
import io.github.radixtrator.resync.data.DataSaving
import io.github.radixtrator.resync.data.Parser
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.RedditClient
import io.github.radixtrator.resync.data.Settings
import io.github.radixtrator.resync.data.ViewAutoplay
import io.github.radixtrator.resync.data.ViewOptions
import io.github.radixtrator.resync.ui.common.FeedDisplay
import io.github.radixtrator.resync.ui.common.PlaybackHandoff
import io.github.radixtrator.resync.ui.common.autoplayStream
import io.github.radixtrator.resync.ui.settings.SettingsIndex
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedAutoplayTest {
    private fun post(json: String) = Parser.post(RedditClient.JSON.parseToJsonElement(json).jsonObject)

    @Test
    fun `autoplay follows the setting, the connection and data saving`() {
        fun active(autoplay: Autoplay, metered: Boolean, saving: DataSaving = DataSaving.NEVER) =
            FeedDisplay(autoplay = autoplay, metered = metered, dataSaving = saving).autoplayActive(PostLayout.CARD)

        assertTrue(active(Autoplay.ALWAYS, metered = true))
        assertTrue(active(Autoplay.WIFI, metered = false))
        assertTrue(!active(Autoplay.WIFI, metered = true))
        assertTrue(!active(Autoplay.NEVER, metered = false))
        // Data saving wins, also "on mobile data" while metered.
        assertTrue(!active(Autoplay.ALWAYS, metered = false, saving = DataSaving.ALWAYS))
        assertTrue(!active(Autoplay.ALWAYS, metered = true, saving = DataSaving.MOBILE))
        assertTrue(active(Autoplay.ALWAYS, metered = false, saving = DataSaving.MOBILE))
        // Off unless picked, so feeds behave as before.
        assertEquals(Autoplay.NEVER, Settings().autoplay)
        assertTrue(!FeedDisplay.of(Settings(), metered = false, moderated = emptySet()).anyAutoplayActive)
    }

    @Test
    fun `each view can override autoplay, and data saving still wins`() {
        // Only in Swipe mode: Data options says never, Swipe mode says always.
        val swipeOnly = Settings(autoplay = Autoplay.NEVER, swipeAutoplay = ViewAutoplay.ALWAYS)
        val display = FeedDisplay.of(swipeOnly, metered = true, moderated = emptySet())
        assertTrue(display.swipeAutoplayActive)
        assertTrue(!display.autoplayActive(PostLayout.CARD))
        assertTrue(!display.autoplayActive(PostLayout.SLIDES))
        assertTrue(display.anyAutoplayActive)

        // Everywhere but Cards; Slides on WiFi only.
        val noCards = Settings(
            autoplay = Autoplay.ALWAYS,
            viewOptions = mapOf(
                PostLayout.CARD to ViewOptions(autoplay = ViewAutoplay.NEVER),
                PostLayout.SLIDES to ViewOptions(autoplay = ViewAutoplay.WIFI),
            ),
        )
        val metered = FeedDisplay.of(noCards, metered = true, moderated = emptySet())
        assertTrue(!metered.autoplayActive(PostLayout.CARD))
        assertTrue(!metered.autoplayActive(PostLayout.SLIDES))
        assertTrue(metered.swipeAutoplayActive)
        assertTrue(FeedDisplay.of(noCards, metered = false, moderated = emptySet()).autoplayActive(PostLayout.SLIDES))

        assertTrue(!FeedDisplay.of(swipeOnly.copy(dataSaving = DataSaving.ALWAYS), metered = false, moderated = emptySet()).anyAutoplayActive)
        // Views follow Data options unless overridden.
        assertEquals(ViewAutoplay.DEFAULT, Settings().swipeAutoplay)
        assertEquals(ViewAutoplay.DEFAULT, ViewOptions().autoplay)
    }

    @Test
    fun `previews autoplay Reddit's streams and GIF mp4s, and RedGifs only through Reddit's copy`() {
        val hls = "https://v.redd.it/abc/HLSPlaylist.m3u8"
        val video = post(
            """{"id":"v1","title":"t","url":"https://v.redd.it/abc","is_video":true,
               "secure_media":{"reddit_video":{"hls_url":"$hls","is_gif":false}}}""",
        )
        assertEquals(hls, video.autoplayStream())

        val gifv = post("""{"id":"v2","title":"t","url":"https://i.imgur.com/xyz.gifv"}""")
        assertEquals("https://i.imgur.com/xyz.mp4", gifv.autoplayStream())

        val copy = "https://v.redd.it/rg/HLSPlaylist.m3u8"
        val redgifs = post(
            """{"id":"v3","title":"t","url":"https://www.redgifs.com/watch/someclip",
               "preview":{"reddit_video_preview":{"hls_url":"$copy","is_gif":true}}}""",
        )
        assertEquals(copy, redgifs.autoplayStream())
        assertNull(post("""{"id":"v4","title":"t","url":"https://www.redgifs.com/watch/other"}""").autoplayStream())

        // A GIF on i.redd.it plays Reddit's mp4 of it; one Reddit didn't transcode stays still.
        val mp4 = "https://preview.redd.it/anim.gif?format=mp4&s=abc"
        val gif = post(
            """{"id":"g1","title":"t","url":"https://i.redd.it/anim.gif","post_hint":"image",
               "preview":{"images":[{"source":{"url":"https://preview.redd.it/anim.gif?s=x","width":400,"height":300},
               "resolutions":[],"variants":{"mp4":{"source":{"url":"$mp4","width":400,"height":300}}}}]}}""",
        )
        assertEquals(mp4, gif.autoplayStream())
        assertNull(post("""{"id":"g2","title":"t","url":"https://example.org/anim.gif","post_hint":"image"}""").autoplayStream())

        assertNull(post("""{"id":"i1","title":"t","url":"https://i.redd.it/pic.jpg","post_hint":"image"}""").autoplayStream())
        assertNull(post("""{"id":"s1","title":"t","is_self":true,"selftext":"hi"}""").autoplayStream())
    }

    @Test
    fun `a clip carries on between the feed preview and the full-screen player`() {
        val stream = "https://v.redd.it/handoff/HLSPlaylist.m3u8"
        assertEquals(0L, PlaybackHandoff.position(stream))

        // The preview plays; the full-screen player opening over it starts where the preview is.
        val preview = Any()
        var previewAt = 12_000L
        PlaybackHandoff.attach(stream, preview) { previewAt }
        assertEquals(12_000L, PlaybackHandoff.position(stream))

        // Both play while the page slides in; the newer one owns the stream, so the preview leaving doesn't count.
        val full = Any()
        var fullAt = 12_000L
        PlaybackHandoff.attach(stream, full) { fullAt }
        previewAt = 12_400L
        PlaybackHandoff.detach(stream, preview)
        fullAt = 30_000L
        assertEquals(30_000L, PlaybackHandoff.position(stream))

        // Closed, its position stays behind for the preview to pick up.
        PlaybackHandoff.detach(stream, full)
        fullAt = 99_000L
        assertEquals(30_000L, PlaybackHandoff.position(stream))
    }

    @Test
    fun `autoplay is in settings search`() {
        assertTrue("Autoplay" in SettingsIndex.search("autoplay").map { it.title })
    }
}
