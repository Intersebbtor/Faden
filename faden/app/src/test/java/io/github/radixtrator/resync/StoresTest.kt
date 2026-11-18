package io.github.radixtrator.resync

import android.content.SharedPreferences
import io.github.radixtrator.resync.data.Account
import io.github.radixtrator.resync.data.AccountStore
import io.github.radixtrator.resync.data.ApiConfig
import io.github.radixtrator.resync.data.ApiConfigStore
import io.github.radixtrator.resync.data.AuthPrefs
import io.github.radixtrator.resync.data.Backup
import io.github.radixtrator.resync.data.AccountsBackup
import io.github.radixtrator.resync.data.BackupPart
import io.github.radixtrator.resync.data.BackupPasswordException
import io.github.radixtrator.resync.data.PostColumns
import io.github.radixtrator.resync.data.RestoreResult
import io.github.radixtrator.resync.data.ViewOptions
import io.github.radixtrator.resync.data.columnsFor
import io.github.radixtrator.resync.data.isCoverScreen
import io.github.radixtrator.resync.data.BackupFormatException
import io.github.radixtrator.resync.data.Draft
import io.github.radixtrator.resync.data.DeferredWrites
import io.github.radixtrator.resync.data.HistoryPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import io.github.radixtrator.resync.data.DraftStore
import io.github.radixtrator.resync.data.ExploreSection
import io.github.radixtrator.resync.data.ExploreStore
import io.github.radixtrator.resync.data.FilterStore
import io.github.radixtrator.resync.data.FilterType
import io.github.radixtrator.resync.data.InboxItem
import io.github.radixtrator.resync.data.NotifiedMessages
import io.github.radixtrator.resync.data.Parser
import io.github.radixtrator.resync.data.PostMemoryStore
import io.github.radixtrator.resync.data.Recent
import io.github.radixtrator.resync.data.RecentPost
import io.github.radixtrator.resync.data.RecentPostsStore
import io.github.radixtrator.resync.data.RedditClient
import io.github.radixtrator.resync.data.SettingsStore
import io.github.radixtrator.resync.data.UserTags
import io.github.radixtrator.resync.data.SavedLabels
import io.github.radixtrator.resync.data.Reminder
import io.github.radixtrator.resync.data.Reminders
import io.github.radixtrator.resync.data.ThreadVisits
import io.github.radixtrator.resync.data.VisitedStore
import io.github.radixtrator.resync.ui.settings.SettingsIndex
import io.github.radixtrator.resync.data.WatchStore
import io.github.radixtrator.resync.data.WatchedCommunity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject

/** Just enough SharedPreferences for the stores: strings, plus the booleans and longs ApiConfigStore and AccountStore keep. */
private class FakePrefs : SharedPreferences {
    val values = HashMap<String, Any?>()
    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = apply { values[key] = value }
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() = Unit
    }
}

class StoresTest {
    @Test
    fun `explore counts visits, keeps recents and survives a reload`() {
        val prefs = FakePrefs()
        val store = ExploreStore(prefs)
        repeat(3) { store.recordVisit("pics") }
        store.recordVisit("Pics") // same feed, any case
        store.recordVisit(null)
        store.recordSearch("secret lair", "magicTCG")
        store.recordVisit("pics") // moves back to the top of recents

        val state = store.state.value
        assertEquals(listOf("pics" to 5, "" to 1), state.mostVisited().map { it.name to it.count })
        assertEquals(
            listOf(Recent(subreddit = "pics"), Recent("magicTCG", "secret lair"), Recent(subreddit = "")),
            state.recents,
        )

        store.toggleFavorite("pics")
        store.setDefault("pics")
        store.setVisible(ExploreSection.MOST_VISITED, false)
        val reloaded = ExploreStore(prefs).state.value
        assertTrue(reloaded.isFavorite("PICS"))
        assertTrue(reloaded.isDefault("pics"))
        assertEquals(setOf(ExploreSection.MOST_VISITED), reloaded.hidden)

        store.setDefault("")
        assertNull(store.state.value.defaultFeed)
        assertTrue(store.state.value.isDefault(""))
        store.toggleFavorite("Pics")
        assertTrue(store.state.value.favorites.isEmpty())
    }

    @Test
    fun `explore shows Feeds once for states saved with it hidden, then remembers hiding it`() {
        val prefs = FakePrefs()
        prefs.values["explore.v1"] = """{"hidden":["FEEDS","MODERATED"],"favorites":["pics"]}"""
        val store = ExploreStore(prefs)
        assertEquals(setOf(ExploreSection.MODERATED), store.state.value.hidden)
        assertEquals(listOf("pics"), store.state.value.favorites)

        store.setVisible(ExploreSection.FEEDS, false)
        assertEquals(setOf(ExploreSection.FEEDS, ExploreSection.MODERATED), ExploreStore(prefs).state.value.hidden)
        assertTrue(ExploreSection.FEEDS !in ExploreStore(FakePrefs()).state.value.hidden)
    }

    @Test
    fun `user tags ignore case, drop blanks and survive a reload`() {
        val prefs = FakePrefs()
        val tags = UserTags(prefs)
        tags.set("Spez", "  admin  ")
        assertEquals("admin", UserTags(prefs).tagOf("spez"))
        tags.set("spez", " ")
        assertNull(UserTags(prefs).tagOf("SPEZ"))
    }

    @Test
    fun `saved labels trim, rename, delete and survive a reload`() {
        val prefs = FakePrefs()
        val labels = SavedLabels(prefs)
        labels.set("t3_a", setOf(" Recipes ", "", "read later"))
        labels.set("t1_b", setOf("Recipes"))
        assertEquals(setOf("Recipes", "read later"), SavedLabels(prefs).labelsOf("t3_a"))
        assertEquals(listOf("read later", "Recipes"), labels.all())
        labels.rename("Recipes", "Food")
        assertEquals(setOf("Food"), SavedLabels(prefs).labelsOf("t1_b"))
        labels.delete("Food")
        assertEquals(emptySet(), labels.labelsOf("t1_b"))
        assertEquals(listOf("read later"), SavedLabels(prefs).all())
        labels.set("t3_a", emptySet())
        assertTrue(SavedLabels(prefs).labels.value.isEmpty())
    }

    @Test
    fun `reminders survive a reload until their time passes`() {
        val prefs = FakePrefs()
        val later = System.currentTimeMillis() + 60_000
        Reminders(prefs).set(Reminder("p1", "Title", "pics", later))
        Reminders(prefs).set(Reminder("p2", "Old", "pics", 1_000))
        assertEquals(setOf("p1"), Reminders(prefs).all.value.keys)
        Reminders(prefs).remove("p1")
        assertTrue(Reminders(prefs).all.value.isEmpty())
    }

    @Test
    fun `drafts keep the newest first, drop blanks and survive a reload`() {
        val prefs = FakePrefs()
        var now = 100L
        val drafts = DraftStore(prefs) { now }
        drafts.save("reply:t1_a", "hello", "Reply")
        now = 200
        drafts.save("submit", "post body", "Post")
        assertEquals(listOf("submit", "reply:t1_a"), DraftStore(prefs).drafts.value.map { it.key })

        now = 300
        drafts.save("reply:t1_a", "hello there", "Reply")
        val reloaded = DraftStore(prefs)
        assertEquals(listOf("reply:t1_a", "submit"), reloaded.drafts.value.map { it.key })
        assertEquals(Draft("reply:t1_a", "hello there", "Reply", 300), reloaded.get("reply:t1_a"))

        // Saving the same text again doesn't bump it.
        now = 400
        drafts.save("submit", "post body", "Post")
        assertEquals(200L, drafts.get("submit")?.savedAt)

        drafts.save("submit", "  ")
        assertNull(DraftStore(prefs).get("submit"))
        drafts.clear("reply:t1_a")
        assertTrue(DraftStore(prefs).drafts.value.isEmpty())
    }

    @Test
    fun `filters hide users and domains everywhere, communities outside themselves`() {
        fun post(sub: String, author: String, domain: String) = Parser.post(
            RedditClient.JSON.parseToJsonElement("""{"id":"x","title":"t","subreddit":"$sub","author":"$author","domain":"$domain"}""").jsonObject,
        )
        val store = FilterStore(FakePrefs())
        store.filterSubreddit("Pics")
        store.filterUser("Spammer")
        store.filterDomain("Example.com")
        val filters = store.filters.value
        assertTrue(filters.hides(post("pics", "a", "i.redd.it"), feedCommunity = null))
        assertTrue(!filters.hides(post("pics", "a", "i.redd.it"), feedCommunity = "PICS"))
        assertTrue(filters.hides(post("gifs", "spammer", "i.redd.it"), feedCommunity = "gifs"))
        assertTrue(filters.hides(post("gifs", "a", "example.com"), feedCommunity = "gifs"))
        store.filterUser("spammer", on = false)
        assertTrue(!store.filters.value.hides(post("gifs", "Spammer", "i.redd.it"), feedCommunity = null))
    }

    @Test
    fun `filters hide keywords in titles and exact flairs`() {
        fun post(title: String, flair: String?) = Parser.post(
            RedditClient.JSON.parseToJsonElement(
                """{"id":"x","title":"$title","subreddit":"s","author":"a","domain":"d"${flair?.let { ""","link_flair_text":"$it"""" } ?: ""}}""",
            ).jsonObject,
        )
        val store = FilterStore(FakePrefs())
        store.filterKeyword("  Spoiler Alert ")
        store.filterFlair("Meme")
        val filters = store.filters.value
        assertTrue(filters.hides(post("Big SPOILER ALERT inside", null), feedCommunity = null))
        assertTrue(filters.hides(post("hello", "meme"), feedCommunity = "s"))
        assertTrue(!filters.hides(post("hello", "memes"), feedCommunity = null))
        store.filterKeyword("spoiler alert", on = false)
        assertTrue(!store.filters.value.hides(post("spoiler alert", null), feedCommunity = null))
    }

    @Test
    fun `filter editor entries are normalized, removable and clearable per list`() {
        assertEquals("pics", FilterType.SUBREDDITS.normalize(" /r/Pics/ "))
        assertEquals("spez", FilterType.USERS.normalize("u/Spez"))
        assertEquals("spez", FilterType.USERS.normalize("/user/spez"))
        assertEquals("example.com", FilterType.DOMAINS.normalize("https://www.Example.com/a/b?c=d"))
        assertEquals("i.redd.it", FilterType.DOMAINS.normalize("i.redd.it"))
        assertEquals("spoiler alert", FilterType.KEYWORDS.normalize("  Spoiler Alert "))
        assertEquals("r/meta", FilterType.FLAIRS.normalize("R/Meta"))
        assertEquals("r/pics", FilterType.SUBREDDITS.describe("pics"))
        assertEquals("\"word\"", FilterType.KEYWORDS.describe("word"))

        val prefs = FakePrefs()
        val store = FilterStore(prefs)
        store.set(FilterType.SUBREDDITS, "r/Pics")
        store.set(FilterType.SUBREDDITS, "gifs")
        store.set(FilterType.DOMAINS, "http://www.youtube.com/watch?v=x")
        store.set(FilterType.USERS, "   ")
        assertEquals(setOf("pics", "gifs"), FilterType.SUBREDDITS.of(store.filters.value))
        assertEquals(setOf("youtube.com"), FilterType.DOMAINS.of(FilterStore(prefs).filters.value))
        assertTrue(FilterType.USERS.of(store.filters.value).isEmpty())
        store.remove(FilterType.SUBREDDITS, "pics")
        assertEquals(setOf("gifs"), store.filters.value.subreddits)
        store.clear(FilterType.SUBREDDITS)
        assertTrue(store.filters.value.subreddits.isEmpty())
        assertEquals(setOf("youtube.com"), FilterStore(prefs).filters.value.domains)
    }

    @Test
    fun `watched posts remember their comment count`() {
        val prefs = FakePrefs()
        val post = Parser.post(RedditClient.JSON.parseToJsonElement("""{"id":"p1","title":"t","subreddit":"pics","num_comments":5}""").jsonObject)
        WatchStore(prefs).watchPost(post)
        val store = WatchStore(prefs)
        assertEquals(5, store.posts.value["p1"]?.comments)
        store.markComments("p1", 9)
        store.markComments("p1", 7)
        assertEquals(9, WatchStore(prefs).posts.value["p1"]?.comments)
        store.unwatchPost("p1")
        assertTrue(WatchStore(prefs).isEmpty)
    }

    @Test
    fun `each unread message is announced once`() {
        fun item(id: String) = InboxItem(
            id = id, fullname = "t4_$id", isComment = false, author = "a", dest = "me", subject = "s", postTitle = null,
            subreddit = null, body = "b", bodyHtml = "", createdUtc = 0, isNew = true, postId = null, commentId = null,
        )
        val prefs = FakePrefs()
        val notified = NotifiedMessages(prefs)
        assertEquals(listOf("t4_1", "t4_2"), notified.unseen(listOf(item("1"), item("2"))).map { it.fullname })
        notified.remember(listOf("t4_1", "t4_2"))
        assertEquals(listOf("t4_3"), NotifiedMessages(prefs).unseen(listOf(item("1"), item("2"), item("3"))).map { it.fullname })
    }

    @Test
    fun `watched communities only move forward and survive a reload`() {
        val prefs = FakePrefs()
        val store = WatchStore(prefs)
        store.watch("Pics", since = 100)
        assertTrue(store.isWatching("pics"))
        store.markSeen("pics", 150)
        store.markSeen("PICS", 120) // an older post never rewinds the mark
        assertEquals(WatchedCommunity("Pics", 150), WatchStore(prefs).watched.value["pics"])
        store.unwatch("pics")
        assertTrue(WatchStore(prefs).watched.value.isEmpty())
    }

    @Test
    fun `post memory and visited keep the most recent entries`() {
        val prefs = FakePrefs()
        val memory = PostMemoryStore(prefs)
        memory.saveScroll("abc", 12, 340)
        memory.saveCollapsed("abc", setOf("c1"))
        val back = PostMemoryStore(prefs)["abc"]!!
        assertEquals(12, back.index)
        assertEquals(setOf("c1"), back.collapsed)
        assertEquals("abc/c9", PostMemoryStore.key("abc", "c9"))

        val visited = VisitedStore(prefs)
        visited.mark("p1")
        visited.mark("p2")
        assertEquals(setOf("p1", "p2"), VisitedStore(prefs).ids.value)
    }

    @Test
    fun `recent posts put the latest first, move reopened ones up and keep fifty`() {
        val prefs = FakePrefs()
        val store = RecentPostsStore(prefs)
        val post = Parser.post(RedditClient.JSON.parseToJsonElement("""{"id":"p1","title":"Hello","subreddit":"pics"}""").jsonObject)
        store.record(post)
        store.record(RecentPost("p2", "Second", "gifs"))
        store.record(post) // reopened: back to the top, not twice
        assertEquals(listOf("p1", "p2"), RecentPostsStore(prefs).posts.value.map { it.id })
        assertEquals(RecentPost("p1", "Hello", "pics"), RecentPostsStore(prefs).posts.value.first())

        repeat(60) { store.record(RecentPost("n$it", "t$it", "s")) }
        val kept = RecentPostsStore(prefs).posts.value
        assertEquals(RecentPostsStore.MAX, kept.size)
        assertEquals("n59", kept.first().id)
        assertEquals("n10", kept.last().id)

        store.clear()
        assertTrue(RecentPostsStore(prefs).posts.value.isEmpty())
    }
}

class FavoriteOrderTest {
    @org.junit.Test
    fun `moves favorites`() {
        val store = ExploreStore(FakePrefs())
        store.toggleFavorite("a")
        store.toggleFavorite("b")
        store.toggleFavorite("c")
        store.moveFavorite("c", -1)
        org.junit.Assert.assertEquals(listOf("a", "c", "b"), store.state.value.favorites)
        store.moveFavorite("a", -1)
        org.junit.Assert.assertEquals(listOf("a", "c", "b"), store.state.value.favorites)
    }
}

class BackupTest {
    /** A device with something in every store, a signed-in account and an API setup. */
    private fun filledPrefs(clientId: String = "client_id_one"): FakePrefs {
        val prefs = FakePrefs()
        val post = Parser.post(RedditClient.JSON.parseToJsonElement("""{"id":"p1","title":"Hello","subreddit":"pics","num_comments":5}""").jsonObject)
        SettingsStore(prefs).update { it.copy(hideRead = true) }
        FilterStore(prefs).filterKeyword("spoiler")
        ExploreStore(prefs).toggleFavorite("pics")
        UserTags(prefs).set("spez", "admin")
        UserTags(prefs).setColor("spez", 0xFF0000)
        SavedLabels(prefs).set("t3_p1", setOf("Recipes"))
        WatchStore(prefs).watch("pics", since = 100)
        WatchStore(prefs).watchPost(post)
        VisitedStore(prefs).mark("p1")
        PostMemoryStore(prefs).saveScroll("p1", 3, 40)
        RecentPostsStore(prefs).record(post)
        DraftStore(prefs).save("submit", "draft body", "Post")
        NotifiedMessages(prefs).remember(listOf("t4_1"))
        ApiConfigStore(prefs).save(ApiConfig(clientId, "app://redirect", "agent/1.0"))
        val accounts = AccountStore(prefs)
        accounts.upsert(Account("me", "SECRET_REFRESH_TOKEN"))
        accounts.activeUsername = "me"
        accounts.accessToken = "SECRET_ACCESS_TOKEN"
        accounts.accessExpiresAt = 12345L
        return prefs
    }

    @Test
    fun `every store writes the key the backup copies`() {
        val prefs = filledPrefs()
        Backup.LOCAL_KEYS.forEach { key -> assertTrue(key in prefs.values, "nothing stored under $key") }
        assertTrue(Backup.API_CLIENT_ID in prefs.values)
    }

    @Test
    fun `backups never hold account tokens`() {
        val prefs = filledPrefs()
        val text = Backup.export(prefs, createdMillis = 0)
        assertTrue("SECRET_REFRESH_TOKEN" !in text)
        assertTrue("SECRET_ACCESS_TOKEN" !in text)
        assertTrue("auth." !in text)
        assertTrue(Backup.ALLOWED_KEYS.none { it.startsWith("auth.") })

        // Not on the way in either: a hand-edited file can't plant tokens.
        val planted = Backup.filter(mapOf("auth.accounts" to "[]", "auth.active" to "me", "settings.v1" to "{}", "unknown" to "x"))
        assertEquals(mapOf("settings.v1" to "{}"), planted)
    }

    @Test
    fun `filter keeps only allowlisted string values`() {
        val kept = Backup.filter(mapOf("visited.v1" to "a,b", "api.demo" to true, "auth.access_expires_at" to 5L, "drafts.v1" to 3))
        assertEquals(mapOf("visited.v1" to "a,b"), kept)
    }

    @Test
    fun `encode and decode round-trip`() {
        val values = mapOf("visited.v1" to "p1,p2", "settings.v1" to """{"hideRead":true}""", "api.client_id" to "abc_client_1")
        val text = Backup.encode(values, createdMillis = 86_400_000L)
        val root = RedditClient.JSON.parseToJsonElement(text).jsonObject
        assertEquals("\"resync-backup\"", root["format"].toString())
        assertEquals("1", root["version"].toString())
        assertEquals("\"1970-01-02T00:00:00Z\"", root["created"].toString())

        val doc = Backup.decode(text)
        assertEquals(1, doc.version)
        assertEquals("1970-01-02T00:00:00Z", doc.created)
        assertEquals(values, doc.prefs)
        assertTrue(doc.hasApiConfig)
        assertEquals("faden-backup-2026-11-08.json", Backup.fileName(java.time.LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun `decode rejects other files`() {
        fun rejects(text: String) = assertTrue(runCatching { Backup.decode(text) }.exceptionOrNull() is BackupFormatException, text)
        rejects("not json")
        rejects("[]")
        rejects("""{"format":"resync-theme","version":1,"prefs":{}}""")
        rejects("""{"format":"resync-backup","prefs":{}}""")
        rejects("""{"format":"resync-backup","version":2,"prefs":{}}""")
        rejects("""{"format":"resync-backup","version":1}""")
        val doc = Backup.decode("""{"format":"resync-backup","version":1,"prefs":{"visited.v1":"p1","auth.active":"me","filters.v1":5}}""")
        assertEquals(mapOf("visited.v1" to "p1"), doc.prefs)
        assertNull(doc.created)
    }

    @Test
    fun `restore replaces local data, keeps accounts and leaves the API setup unless asked`() {
        val source = filledPrefs(clientId = "client_id_two")
        val doc = Backup.decode(Backup.export(source))

        val target = FakePrefs()
        ApiConfigStore(target).save(ApiConfig("client_id_one", "app://mine", "mine/1.0"))
        AccountStore(target).upsert(Account("other", "TARGET_REFRESH"))
        AccountStore(target).activeUsername = "other"
        UserTags(target).set("someone", "only here") // replaced by the backup's tags
        RecentPostsStore(target).record(RecentPost("x", "t", "s"))

        val result = Backup.restore(target, doc, includeApiConfig = false)
        assertTrue(!result.signedOut)
        Backup.LOCAL_KEYS.forEach { key -> assertEquals(source.values[key], target.values[key], key) }
        assertTrue(SettingsStore(target).settings.value.hideRead)
        assertEquals("admin", UserTags(target).tagOf("spez"))
        assertNull(UserTags(target).tagOf("someone"))
        assertEquals("draft body", DraftStore(target).get("submit")?.text)
        assertEquals(listOf("p1"), RecentPostsStore(target).posts.value.map { it.id })
        assertEquals("client_id_one", ApiConfigStore(target).load()?.clientId)
        assertEquals("other", AccountStore(target).active?.username)
    }

    @Test
    fun `restore resets local data the backup lacks`() {
        val target = filledPrefs()
        Backup.restore(target, Backup.decode("""{"format":"resync-backup","version":1,"prefs":{"visited.v1":"p9"}}"""), includeApiConfig = true)
        assertEquals(setOf("p9"), VisitedStore(target).ids.value)
        assertTrue(DraftStore(target).drafts.value.isEmpty())
        assertTrue(FilterStore(target).filters.value.keywords.isEmpty())
        // No API setup in the file: the device's stays, and so do its accounts.
        assertEquals("client_id_one", ApiConfigStore(target).load()?.clientId)
        assertEquals("me", AccountStore(target).active?.username)
    }

    @Test
    fun `restoring a different client ID signs accounts out, the same one keeps them`() {
        val target = filledPrefs(clientId = "client_id_one")
        val same = Backup.decode(Backup.export(filledPrefs(clientId = "client_id_one")))
        assertTrue(!Backup.restore(target, same, includeApiConfig = true).signedOut)
        assertEquals("me", AccountStore(target).active?.username)

        val other = Backup.decode(Backup.export(filledPrefs(clientId = "client_id_two")))
        assertTrue(Backup.restore(target, other, includeApiConfig = true).signedOut)
        assertEquals(ApiConfig("client_id_two", "app://redirect", "agent/1.0"), ApiConfigStore(target).load())
        assertTrue(AccountStore(target).accounts.isEmpty())
        assertNull(AccountStore(target).activeUsername)
        assertNull(AccountStore(target).accessToken)
    }

    @Test
    fun `a backup's API setup is checked like Setup's`() {
        assertTrue(ApiConfig.isValidRedirectUri(ApiConfig.DEFAULT_REDIRECT_URI))
        assertTrue(ApiConfig.isValidUserAgent(ApiConfig.DEFAULT_USER_AGENT))
        assertTrue(!ApiConfig.isValidRedirectUri("no scheme"))
        assertTrue(!ApiConfig.isValidUserAgent("agent\n/1.0"))
        assertTrue(!ApiConfig.isValidUserAgent("agent ✓"))

        fun doc(id: String, redirect: String, agent: String) = Backup.decode(
            """{"format":"resync-backup","version":1,"prefs":{"api.client_id":"$id","api.redirect_uri":"$redirect","api.user_agent":"$agent"}}""",
        )
        // Not a bare client ID: no API setup is offered or restored, so nobody is signed out.
        val bad = doc("App ID: client_id_two", "app://redirect", "agent/1.0")
        assertTrue(!bad.hasApiConfig)
        val target = filledPrefs(clientId = "client_id_one")
        assertTrue(!Backup.restore(target, bad, includeApiConfig = true).signedOut)
        assertEquals(ApiConfig("client_id_one", "app://redirect", "agent/1.0"), ApiConfigStore(target).load())
        assertEquals("me", AccountStore(target).active?.username)

        // A good ID with a bad redirect URI and user agent: the ID is restored, the others fall back to the defaults.
        val odd = doc("client_id_two", "javascript alert(1)", "agent\\u0001/1.0")
        assertTrue(odd.hasApiConfig)
        Backup.restore(target, odd, includeApiConfig = true)
        assertEquals(ApiConfig("client_id_two", ApiConfig.DEFAULT_REDIRECT_URI, ApiConfig.DEFAULT_USER_AGENT), ApiConfigStore(target).load())
    }

    @Test
    fun `restoring an API setup leaves demo mode`() {
        val target = FakePrefs()
        ApiConfigStore(target).demoMode = true
        AccountStore(target).upsert(Account("demo_user", "demo"))
        val doc = Backup.decode(Backup.export(filledPrefs()))
        assertTrue(Backup.restore(target, doc, includeApiConfig = true).signedOut)
        assertTrue(!ApiConfigStore(target).demoMode)
        assertTrue(AccountStore(target).accounts.isEmpty())
    }

    private val password = "correct horse".toCharArray()

    @Test
    fun `accounts travel encrypted and only with a password`() {
        val prefs = filledPrefs()
        val withAccounts = Backup.export(prefs, createdMillis = 0, accountsPassword = password.copyOf())
        assertTrue("SECRET_REFRESH_TOKEN" !in withAccounts)
        assertTrue("SECRET_ACCESS_TOKEN" !in withAccounts)
        val doc = Backup.decode(withAccounts)
        assertEquals(listOf("me"), doc.accountNames)
        assertEquals(AccountsBackup("client_id_one", listOf(Account("me", "SECRET_REFRESH_TOKEN")), "me"), Backup.decryptAccounts(doc, password.copyOf()))
        assertTrue(runCatching { Backup.decryptAccounts(doc, "wrong password".toCharArray()) }.exceptionOrNull() is BackupPasswordException)

        assertNull(Backup.decode(Backup.export(prefs, createdMillis = 0)).accounts)
        // A hand-written plain accounts section is ignored rather than trusted.
        val planted = Backup.decode("""{"format":"resync-backup","version":1,"prefs":{},"accounts":{"accounts":[{"username":"x","refreshToken":"y"}]}}""")
        assertNull(planted.accounts)
    }

    @Test
    fun `restoring accounts signs them in and brings their API setup`() {
        val doc = Backup.decode(Backup.export(filledPrefs(clientId = "client_id_two"), accountsPassword = password.copyOf()))
        val accounts = Backup.decryptAccounts(doc, password.copyOf())

        // Same client: accounts merge, the backup's active one wins.
        val same = FakePrefs()
        ApiConfigStore(same).save(ApiConfig("client_id_two", "app://mine", "mine/1.0"))
        AccountStore(same).upsert(Account("other", "OTHER_REFRESH"))
        AccountStore(same).activeUsername = "other"
        AccountStore(same).accessToken = "OLD_ACCESS"
        val result = Backup.restore(same, doc, includeApiConfig = false, accounts = accounts)
        assertEquals(RestoreResult(signedOut = false, accountsRestored = 1), result)
        assertEquals(setOf("other", "me"), AccountStore(same).accounts.map { it.username }.toSet())
        assertEquals("me", AccountStore(same).activeUsername)
        assertNull(AccountStore(same).accessToken)

        // Different client: the old accounts go, the API setup comes along even though it wasn't ticked.
        val other = FakePrefs()
        ApiConfigStore(other).save(ApiConfig("client_id_one", "app://mine", "mine/1.0"))
        AccountStore(other).upsert(Account("other", "OTHER_REFRESH"))
        val switched = Backup.restore(other, doc, includeApiConfig = false, accounts = accounts)
        assertEquals(RestoreResult(signedOut = true, accountsRestored = 1), switched)
        assertEquals("client_id_two", ApiConfigStore(other).load()?.clientId)
        assertEquals(listOf(Account("me", "SECRET_REFRESH_TOKEN")), AccountStore(other).accounts)
        assertEquals("me", AccountStore(other).activeUsername)
    }

    @Test
    fun `restore only replaces the chosen parts`() {
        val doc = Backup.decode(Backup.export(filledPrefs()))
        val target = FakePrefs()
        UserTags(target).set("someone", "only here")
        Backup.restore(target, doc, includeApiConfig = false, parts = setOf(BackupPart.SETTINGS))
        assertTrue(SettingsStore(target).settings.value.hideRead)
        assertEquals("only here", UserTags(target).tagOf("someone"))
        assertNull(target.values["visited.v1"])
    }

    @Test
    fun `a snapshot puts everything back, accounts included`() {
        val prefs = filledPrefs()
        val before = HashMap(prefs.values)
        val snapshot = Backup.snapshot(prefs, createdMillis = 0)
        assertEquals("1970-01-01T00:00:00Z", Backup.snapshotCreated(snapshot))

        Backup.restore(prefs, Backup.decode(Backup.export(FakePrefs())), includeApiConfig = false)
        AccountStore(prefs).removeAll()
        prefs.values["junk"] = "x"
        Backup.restoreSnapshot(prefs, snapshot)
        assertEquals(before, prefs.values)
        assertNull(Backup.snapshotCreated(Backup.export(prefs)))
    }

    @Test
    fun `cover and main screens pick their own columns`() {
        val options = ViewOptions(columns = PostColumns.FOUR, coverColumns = PostColumns.TWO)
        assertTrue(isCoverScreen(344))
        assertTrue(!isCoverScreen(673))
        assertEquals(PostColumns.TWO, options.columnsFor(coverScreen = true))
        assertEquals(PostColumns.FOUR, options.columnsFor(coverScreen = false))
        // Settings saved before the cover screen choice keep their single override for the big screen.
        val old = RedditClient.JSON.decodeFromString(ViewOptions.serializer(), """{"columns":"TWO"}""")
        assertEquals(PostColumns.TWO, old.columns)
        assertEquals(PostColumns.AUTO, old.coverColumns)
    }

    @Test
    fun `thread visits keep the latest time and survive a reload`() {
        val prefs = FakePrefs()
        val visits = ThreadVisits(prefs)
        assertNull(visits["abc"])
        visits.record("abc", 100)
        visits.record("def", 200)
        visits.record("abc", 300)
        val back = ThreadVisits(prefs)
        assertEquals(300L, back["abc"])
        assertEquals(200L, back["def"])
    }

    @Test
    fun `settings search finds rows by title, summary and keyword`() {
        val search = { q: String -> SettingsIndex.search(q).map { it.title } }
        assertTrue(search("").isEmpty())
        assertEquals("Hide AutoMod comments", search("automod").first())
        // Every word has to match, in any field.
        assertTrue("Mark new comments" in search("label new"))
        assertTrue("Open Reddit links in Faden" in search("reddit links"))
        assertTrue("Dark overlay" in search("dim"))
        // Title matches first.
        val theme = search("theme")
        assertTrue(theme.indexOf("Theme color") in 0 until theme.indexOf("Material You colors"))
        assertTrue(search("zzzz").isEmpty())
    }

    @Test
    fun `closing videos by a tap starts off, survives a reload and shows up in settings search`() {
        // Settings saved before the option existed keep closing videos by swiping.
        val prefs = FakePrefs().apply { values["settings.v1"] = """{"tapToClose":true}""" }
        assertTrue(!SettingsStore(prefs).settings.value.tapToCloseVideos)
        SettingsStore(prefs).update { it.copy(tapToCloseVideos = true) }
        assertTrue(SettingsStore(prefs).settings.value.tapToCloseVideos)
        assertTrue("Close videos by" in SettingsIndex.search("close videos").map { it.title })
    }

    /** [filledPrefs] split the way the app keeps it: main, history and auth files. */
    private fun splitPrefs(clientId: String = "client_id_one"): Triple<FakePrefs, FakePrefs, FakePrefs> {
        val main = filledPrefs(clientId)
        val history = FakePrefs()
        val auth = FakePrefs()
        HistoryPrefs.migrate(main, history)
        AuthPrefs.migrate(main, auth)
        return Triple(main, history, auth)
    }

    @Test
    fun `auth migration moves every login key, keeps their types and leaves none behind`() {
        val old = filledPrefs()
        AccountStore(old).deviceId // written on first read
        val before = HashMap(old.values)
        val auth = FakePrefs().apply { values["auth.active"] = "newer" }
        AuthPrefs.migrate(old, auth)

        assertTrue(old.values.keys.none(AuthPrefs::isAuthKey), "a login key was left in the file that transfers")
        assertEquals(before.filterKeys { !AuthPrefs.isAuthKey(it) }, old.values)
        assertEquals("newer", auth.values["auth.active"], "an existing value was overwritten")
        assertEquals(12345L, auth.values["auth.access_expires_at"])
        assertEquals(before["auth.device_id"], auth.values["auth.device_id"])
        assertEquals(listOf(Account("me", "SECRET_REFRESH_TOKEN")), AccountStore(auth).accounts)

        val oldAfter = HashMap(old.values)
        val authAfter = HashMap(auth.values)
        AuthPrefs.migrate(old, auth)
        assertEquals(oldAfter, old.values)
        assertEquals(authAfter, auth.values)
        val fresh = FakePrefs()
        AuthPrefs.migrate(FakePrefs(), fresh)
        assertTrue(fresh.values.isEmpty())
    }

    @Test
    fun `backup and restore keep accounts in the auth file`() {
        val (main, history, auth) = splitPrefs()
        assertNull(Backup.accountsIn(main), "accounts aren't in the main file any more")
        assertEquals("me", Backup.accountsIn(main, auth)?.active)
        val password = "pw".toCharArray()
        val doc = Backup.decode(Backup.export(main, accountsPassword = password.copyOf(), history = history, auth = auth))
        val accounts = Backup.decryptAccounts(doc, password.copyOf())
        assertEquals(listOf(Account("me", "SECRET_REFRESH_TOKEN")), accounts.accounts)

        // Onto an install with another client ID: its accounts are signed out and the backup's land in its auth file.
        val (tMain, tHistory, tAuth) = splitPrefs(clientId = "client_id_two")
        AccountStore(tAuth).upsert(Account("other", "OTHER_TOKEN"))
        val result = Backup.restore(tMain, doc, includeApiConfig = false, accounts = accounts, history = tHistory, auth = tAuth)
        assertEquals(1, result.accountsRestored)
        assertEquals(listOf(Account("me", "SECRET_REFRESH_TOKEN")), AccountStore(tAuth).accounts)
        assertEquals("me", tAuth.values["auth.active"])
        assertNull(tAuth.values["auth.access_token"])
        assertTrue(tMain.values.keys.none(AuthPrefs::isAuthKey))
        assertEquals("client_id_one", tMain.values[Backup.API_CLIENT_ID])
    }

    @Test
    fun `a snapshot puts all three files back, and an old one lands logins in the auth file`() {
        val (main, history, auth) = splitPrefs()
        val before = listOf(HashMap(main.values), HashMap(history.values), HashMap(auth.values))
        val snapshot = Backup.snapshot(main, createdMillis = 0, history = history, auth = auth)
        AccountStore(auth).removeAll()
        history.values.clear()
        main.values["junk"] = "x"
        Backup.restoreSnapshot(main, snapshot, history, auth)
        assertEquals(before, listOf(main.values, history.values, auth.values))

        // A snapshot saved by a version that kept everything in one file.
        val old = filledPrefs()
        val oldSnapshot = Backup.snapshot(old, createdMillis = 0)
        val (m, h, a) = Triple(FakePrefs(), FakePrefs(), FakePrefs())
        Backup.restoreSnapshot(m, oldSnapshot, h, a)
        assertTrue(m.values.keys.none(AuthPrefs::isAuthKey))
        assertEquals(old.values.filterKeys(AuthPrefs::isAuthKey), a.values)
        assertEquals(old.values.filterKeys(HistoryPrefs::isHistoryKey), h.values)
    }
}

class HistorySplitTest {
    /** What the previous version left on a device: everything, history included, in the one "resync" file. */
    private fun oldLayout(): FakePrefs {
        val prefs = FakePrefs()
        val post = Parser.post(RedditClient.JSON.parseToJsonElement("""{"id":"p1","title":"Hello","subreddit":"pics","num_comments":5}""").jsonObject)
        SettingsStore(prefs).update { it.copy(hideRead = true) }
        ExploreStore(prefs).recordVisit("pics")
        VisitedStore(prefs).markAll(listOf("p1", "p2"))
        PostMemoryStore(prefs).saveScroll("p1", 3, 40)
        ThreadVisits(prefs).record("p1", 1234)
        RecentPostsStore(prefs).record(post)
        DraftStore(prefs).save("submit", "draft body", "Post")
        return prefs
    }

    @Test
    fun `migration moves exactly the history keys and keeps every value`() {
        val old = oldLayout()
        val before = HashMap(old.values)
        val history = FakePrefs()
        HistoryPrefs.migrate(old, history)

        assertEquals(HistoryPrefs.KEYS, history.values.keys)
        HistoryPrefs.KEYS.forEach { key ->
            assertEquals(before[key], history.values[key])
            assertTrue(key !in old.values, "$key left behind in the old file")
        }
        assertEquals(before.filterKeys { it !in HistoryPrefs.KEYS }, old.values)
        // The stores read it all back from the new file.
        assertEquals(setOf("p1", "p2"), VisitedStore(history).ids.value)
        assertEquals(3, PostMemoryStore(history)["p1"]!!.index)
        assertEquals(1234L, ThreadVisits(history)["p1"])
        assertEquals(listOf("p1"), RecentPostsStore(history).posts.value.map { it.id })
        assertEquals(1, ExploreStore(history).state.value.mostVisited().single().count)
    }

    @Test
    fun `migration is idempotent and a no-op on a fresh install`() {
        val old = oldLayout()
        val history = FakePrefs()
        HistoryPrefs.migrate(old, history)
        val oldAfter = HashMap(old.values)
        val historyAfter = HashMap(history.values)
        HistoryPrefs.migrate(old, history)
        assertEquals(oldAfter, old.values)
        assertEquals(historyAfter, history.values)

        val freshOld = FakePrefs()
        val freshHistory = FakePrefs()
        HistoryPrefs.migrate(freshOld, freshHistory)
        assertTrue(freshOld.values.isEmpty() && freshHistory.values.isEmpty())
    }

    @Test
    fun `migration never overwrites newer history and finishes an interrupted move`() {
        val old = FakePrefs().apply { values["visited.v1"] = "stale"; values["explore.v1"] = "{}" }
        val history = FakePrefs().apply { values["visited.v1"] = "fresh" } // copied, then died before removing from old
        HistoryPrefs.migrate(old, history)
        assertEquals("fresh", history.values["visited.v1"])
        assertEquals("{}", history.values["explore.v1"])
        assertTrue(old.values.isEmpty())
    }

    @Test
    fun `a backup from the single-file version restores into the right files`() {
        val doc = Backup.decode(Backup.export(oldLayout(), createdMillis = 0))
        val main = FakePrefs()
        val history = FakePrefs()
        Backup.restore(main, doc, includeApiConfig = false, history = history)
        assertEquals(setOf("p1", "p2"), VisitedStore(history).ids.value)
        assertEquals(3, PostMemoryStore(history)["p1"]!!.index)
        assertEquals(listOf("p1"), RecentPostsStore(history).posts.value.map { it.id })
        assertTrue(ExploreStore(history).state.value.visits.isNotEmpty())
        assertTrue(HistoryPrefs.KEYS.none { it in main.values })
        assertTrue(main.values.containsKey("settings.v1") && main.values.containsKey("drafts.v1"))
    }

    @Test
    fun `a split-layout backup holds the same data as the single-file one and round-trips`() {
        val oldPrefs = oldLayout()
        val main = FakePrefs().apply { values.putAll(oldPrefs.values) }
        val history = FakePrefs()
        HistoryPrefs.migrate(main, history)

        val single = Backup.decode(Backup.export(oldPrefs, createdMillis = 0))
        val split = Backup.decode(Backup.export(main, createdMillis = 0, history = history))
        assertEquals(single.prefs, split.prefs)
        assertTrue(HistoryPrefs.KEYS.filter { it != "thread_visits.v1" }.all { it in split.prefs })

        val main2 = FakePrefs()
        val history2 = FakePrefs()
        Backup.restore(main2, split, includeApiConfig = false, history = history2)
        assertEquals(Backup.filter(history.values), Backup.filter(history2.values))
        assertEquals(Backup.filter(main.values), Backup.filter(main2.values))
    }

    @Test
    fun `snapshots cover both files and old snapshots restore into the right ones`() {
        val oldPrefs = oldLayout()
        val oldSnapshot = Backup.snapshot(oldPrefs, createdMillis = 0)
        val main = FakePrefs().apply { values["junk"] = "x" }
        val history = FakePrefs().apply { values["visited.v1"] = "zzz" }
        Backup.restoreSnapshot(main, oldSnapshot, history)
        assertEquals(oldPrefs.values.filterKeys { it !in HistoryPrefs.KEYS }, main.values)
        assertEquals(oldPrefs.values.filterKeys { it in HistoryPrefs.KEYS }, history.values)

        assertEquals(oldSnapshot, Backup.snapshot(main, createdMillis = 0, history = history))
    }

    @Test
    fun `deferred writes coalesce, flush, and can be cancelled`() {
        val prefs = FakePrefs()
        val scope = CoroutineScope(Dispatchers.Default)
        val writes = DeferredWrites(scope, delayMs = 100)
        val visited = VisitedStore(prefs, writes)
        visited.mark("a")
        visited.mark("b")
        visited.mark("c")
        assertEquals(setOf("a", "b", "c"), visited.ids.value) // in memory at once
        assertNull(prefs.values["visited.v1"]) // not yet on disk

        val deadline = System.currentTimeMillis() + 5000
        while ("visited.v1" !in prefs.values && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals("a,b,c", prefs.values["visited.v1"])

        visited.mark("d")
        writes.flush()
        assertEquals("a,b,c,d", prefs.values["visited.v1"])

        visited.mark("e")
        writes.cancel()
        Thread.sleep(300)
        assertEquals("a,b,c,d", prefs.values["visited.v1"])
        scope.cancel()
    }

    @Test
    fun `explore edits other than visits write at once`() {
        val prefs = FakePrefs()
        val scope = CoroutineScope(Dispatchers.Default)
        val writes = DeferredWrites(scope, delayMs = 60_000)
        val explore = ExploreStore(prefs, writes)
        explore.recordVisit("pics")
        assertNull(prefs.values["explore.v1"])
        explore.toggleFavorite("pics")
        assertTrue((prefs.values["explore.v1"] as String).contains("favorites"))
        assertTrue(ExploreStore(prefs).state.value.visits.containsKey("pics")) // flushed along with it
        scope.cancel()
    }
}
