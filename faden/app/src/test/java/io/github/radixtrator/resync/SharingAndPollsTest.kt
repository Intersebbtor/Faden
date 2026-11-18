package io.github.radixtrator.resync

import io.github.radixtrator.resync.data.ClipHosts
import io.github.radixtrator.resync.data.CrosspostSource
import io.github.radixtrator.resync.data.RedditException
import io.github.radixtrator.resync.data.Streamable
import io.github.radixtrator.resync.ui.navigation.RedditLinks
import io.github.radixtrator.resync.ui.navigation.VideoRoute
import io.github.radixtrator.resync.ui.navigation.YouTube
import kotlin.test.assertFailsWith
import io.github.radixtrator.resync.data.Parser
import io.github.radixtrator.resync.data.PollOption
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.RedditClient
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.InboxRoute
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.SubmitRoute
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharingAndPollsTest {
    private fun post(json: String) = Parser.post(RedditClient.JSON.parseToJsonElement(json).jsonObject)

    @Test
    fun `crossposts show the original's media and text and say where it came from`() {
        val image = post(
            """{"id":"x1","subreddit":"funny","author":"bob","title":"look","is_self":false,
               "url":"/r/pics/comments/o1/look/","domain":"i.redd.it",
               "crosspost_parent_list":[{"id":"o1","subreddit":"pics","author":"alice","is_self":false,
                 "url":"https://i.redd.it/abc.jpg","post_hint":"image"}]}""",
        )
        assertEquals(CrosspostSource("o1", "pics", "alice"), image.crosspostOf)
        assertIs<PostMedia.Image>(image.media)

        val text = post(
            """{"id":"x2","subreddit":"funny","title":"read","is_self":false,"selftext":"",
               "crosspost_parent_list":[{"id":"o2","subreddit":"stories","author":"carol","is_self":true,
                 "selftext":"Once upon a time\n\nthe end","selftext_html":"&lt;p&gt;Once upon a time&lt;/p&gt;"}]}""",
        )
        assertEquals("Once upon a time\n\nthe end", text.selfText)
        assertEquals(PostMedia.None, text.media)

        assertNull(post("""{"id":"p","title":"plain","is_self":true}""").crosspostOf)
    }

    @Test
    fun `polls parse with counts only once Reddit gives them`() {
        val open = post(
            """{"id":"p1","title":"Which?","is_self":true,"poll_data":{"total_vote_count":12,
               "voting_end_timestamp":4102444800000,"user_selection":null,
               "options":[{"id":"1","text":"Cats"},{"id":"2","text":"Dogs"},{"id":"3","text":" "}]}}""",
        ).poll!!
        assertEquals(listOf(PollOption("1", "Cats", null), PollOption("2", "Dogs", null)), open.options)
        assertEquals(12, open.totalVotes)
        assertTrue(open.isOpen(nowMillis = 1_700_000_000_000))
        assertFalse(open.hasResults)

        val closed = post(
            """{"id":"p2","title":"Which?","is_self":true,"poll_data":{"total_vote_count":3,
               "voting_end_timestamp":1000,"user_selection":"2",
               "options":[{"id":"1","text":"Cats","vote_count":1},{"id":"2","text":"Dogs","vote_count":2}]}}""",
        ).poll!!
        assertFalse(closed.isOpen(nowMillis = 2000))
        assertTrue(closed.hasResults)
        assertEquals("2", closed.selected)

        assertNull(post("""{"id":"p3","title":"none","is_self":true,"poll_data":{"options":[]}}""").poll)
    }

    @Test
    fun `shared links become link posts and other text a text post`() {
        assertEquals(
            SubmitRoute(title = "A page", url = "https://example.com/a?b=1"),
            SharedContent.text("https://example.com/a?b=1", "A page"),
        )
        // Title before the URL, as some apps share it.
        assertEquals(
            SubmitRoute(title = "Neat thing", url = "https://example.com/x"),
            SharedContent.text("Neat thing https://example.com/x.", null),
        )
        assertEquals(SubmitRoute(text = "Just some thoughts"), SharedContent.text("  Just some thoughts ", null))
        assertNull(SharedContent.text("   ", "subject"))
        // A long text that happens to contain a link stays a text post.
        val long = "word ".repeat(80) + "https://example.com"
        assertEquals(long.trim(), SharedContent.text(long, null)!!.text)
    }

    @Test
    fun `shared pictures only come from content URIs of other apps`() {
        assertEquals(
            SubmitRoute(title = "Sunset", photos = listOf("content://media/external/images/1")),
            SharedContent.photos(listOf("content://media/external/images/1"), " Sunset "),
        )
        assertNull(SharedContent.photos(listOf("file:///data/data/${BuildConfig.APPLICATION_ID}/shared_prefs/resync.xml"), null))
        assertNull(SharedContent.photos(listOf("content://${BuildConfig.APPLICATION_ID}.provider/x"), null))
        assertNull(SharedContent.photos(emptyList(), null))
        // Several make a gallery: the unsafe ones are dropped, and no more than Reddit's 20 are kept.
        val many = (1..25).map { "content://media/external/images/$it" }
        val gallery = SharedContent.photos(listOf("file:///sdcard/x.jpg") + many, null)!!
        assertEquals(many.take(20), gallery.photos)
    }

    @Test
    fun `streamable and youtube links are recognized`() {
        assertEquals("abc123", Streamable.id("https://streamable.com/abc123"))
        assertEquals("abc123", Streamable.id("https://streamable.com/e/abc123?autoplay=1"))
        assertNull(Streamable.id("https://streamable.com/"))
        assertEquals(VideoRoute("https://streamable.com/abc123"), RedditLinks.route("https://streamable.com/abc123"))
        assertTrue(ClipHosts.isHosted("https://www.redgifs.com/watch/somegif"))
        assertTrue(ClipHosts.isHosted("https://streamable.com/abc123"))
        assertFalse(ClipHosts.isHosted("https://example.com/video.mp4"))

        assertTrue(YouTube.isVideo("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YouTube.isVideo("https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ"))
        assertTrue(YouTube.isVideo("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(YouTube.isVideo("https://m.youtube.com/shorts/dQw4w9WgXcQ"))
        assertFalse(YouTube.isVideo("https://www.youtube.com/@somechannel"))
        assertFalse(YouTube.isVideo("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `streamable api responses give the mp4s`() {
        fun parse(json: String) = Streamable.parseVideo(RedditClient.JSON.parseToJsonElement(json).jsonObject)
        val clip = parse("""{"status":2,"files":{"mp4":{"url":"//cdn.streamable.com/video/mp4/abc.mp4?token=1"},
            "mp4-mobile":{"url":"https://cdn.streamable.com/video/mp4-mobile/abc.mp4"}}}""")!!
        assertEquals("https://cdn.streamable.com/video/mp4/abc.mp4?token=1", clip.hd)
        assertEquals("https://cdn.streamable.com/video/mp4-mobile/abc.mp4", clip.sd)
        assertTrue(clip.hasAudio)
        assertEquals("https://x/a.mp4", parse("""{"status":2,"files":{"mp4":{"url":"https://x/a.mp4"}}}""")!!.sd)
        assertNull(parse("""{"status":2,"files":{}}"""))
        assertFailsWith<java.io.IOException> { parse("""{"status":1,"files":{}}""") }
    }

    @Test
    fun `streamable posts play in app and link posts keep their embed title`() {
        val streamable = post("""{"id":"s1","title":"clip","is_self":false,"url":"https://streamable.com/abc123",
            "preview":{"reddit_video_preview":{"fallback_url":"https://v.redd.it/x/DASH_480.mp4"}}}""")
        val video = assertIs<PostMedia.Video>(streamable.media)
        assertEquals("https://streamable.com/abc123", video.url)
        assertEquals("https://v.redd.it/x/DASH_480.mp4", video.fallback)
        assertFalse(video.loop)

        val youtube = post("""{"id":"y1","title":"song","is_self":false,"url":"https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "secure_media":{"oembed":{"title":"Rick Astley &amp; friends","provider_name":"YouTube"}}}""")
        assertEquals("Rick Astley & friends", assertIs<PostMedia.Link>(youtube.media).title)
    }

    @Test
    fun `gallery submissions return the new post's id`() {
        fun parse(json: String) = Parser.galleryId(RedditClient.JSON.parseToJsonElement(json))
        assertEquals("abc", parse("""{"json":{"errors":[],"data":{"id":"t3_abc","url":"https://www.reddit.com/r/x/comments/abc/t/"}}}"""))
        assertEquals("def", parse("""{"json":{"errors":[],"data":{"url":"https://www.reddit.com/r/x/comments/def/t/"}}}"""))
        assertFailsWith<RedditException> { parse("""{"json":{"errors":[["MEDIA_NOT_READY","not ready","items"]]}}""") }
    }

    @Test
    fun `launcher shortcuts open their pages and nothing else`() {
        assertEquals(SearchRoute(), Shortcuts.route("search", null))
        assertEquals(InboxRoute(home = true), Shortcuts.route("inbox", null))
        assertEquals(SubmitRoute(), Shortcuts.route("submit", null))
        assertEquals(FeedRoute("pics"), Shortcuts.route("feed", "pics"))
        assertNull(Shortcuts.route("feed", "../../evil"))
        assertNull(Shortcuts.route("settings", null))
    }
}
