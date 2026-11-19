package io.github.radixtrator.resync.data

import io.github.radixtrator.resync.i18n.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val WIKI_TOC = Regex("""<div class="toc">.*?</div>""", RegexOption.DOT_MATCHES_ALL)

/** "user/feed", "u/user/m/feed" and the like to Reddit's custom-feed search path, /user/<name>/m/<feed>/search. */
fun multiSearchPath(multi: String): String {
    val parts = multi.trim().trim('/').removePrefix("user/").removePrefix("u/").split('/').filter { it.isNotEmpty() && it != "m" }
    return "/user/${parts.getOrElse(0) { "" }}/m/${parts.getOrElse(1) { "" }}/search"
}

/**
 * The URL name Reddit wants for a new custom feed called [displayName]: letters, digits and underscores,
 * starting with a letter or digit, 3 to 50 long. The display name keeps whatever was typed.
 */
fun multiName(displayName: String): String {
    val name = displayName.trim().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(50).trimEnd('_')
    return if (name.length >= 3) name else (name + "_feed").trimStart('_')
}

/** The `model` JSON of /api/multi writes; null fields are left out, so a PUT only changes what's given. */
fun multiModel(displayName: String? = null, subreddits: List<String>? = null, visibility: String? = null): String = buildJsonObject {
    displayName?.let { put("display_name", it) }
    subreddits?.let { subs -> putJsonArray("subreddits") { subs.forEach { name -> add(buildJsonObject { put("name", name) }) } } }
    visibility?.let { put("visibility", it) }
}.toString()

/** How many of a "more" row's comments one tap loads, and how many of their pages are fetched at once. */
private const val MORE_PER_LOAD = 20
private const val MORE_PARALLEL = 5

/** The endpoints the app uses, on top of [RedditClient]. */
class RedditApi(private val client: RedditClient) {

    // --- Reading ---

    /** [subreddit] null = front page, or with [domain] that site's posts. Also accepts "popular", "all" and "a+b" multis. */
    suspend fun posts(subreddit: String?, sort: Sort, after: String?, limit: Int = 25, domain: String? = null): Listing<Post> {
        val base = when {
            domain != null -> "/domain/$domain"
            subreddit == null -> ""
            else -> "/r/$subreddit"
        }
        val json = client.get(
            "$base/${sort.path}",
            mapOf("after" to after, "limit" to limit.toString(), "t" to sort.time?.param),
        )
        return parsed { Parser.postListing(json) }.also { PostCache.putAll(it.items) }
    }

    /** The newest comments in [subreddit] (null = the front page's communities), for Sync's "Comments" sort. */
    suspend fun feedComments(subreddit: String?, after: String?): Listing<ProfileItem> {
        val base = if (subreddit == null) "" else "/r/$subreddit"
        val json = client.get("$base/comments", mapOf("after" to after, "limit" to "25"))
        return parsed { Parser.profileListing(json) }
    }

    /**
     * A post as it is now (its comment count, for watched posts). Reddit answers /by_id and /api/info with 403
     * for this kind of client, so this asks for the post's page with a single comment.
     */
    suspend fun postNow(id: String): Post =
        Parser.comments(client.get("/comments/$id", mapOf("limit" to "1", "depth" to "1"))).post

    /**
     * [focusCommentId] narrows the page to that comment's subtree, with [context] parents above it. [limit] is how
     * many comments the first page holds (Sync's "Initial comment count"; Reddit stops at 500).
     */
    suspend fun comments(
        postId: String,
        sort: CommentSort,
        focusCommentId: String? = null,
        context: Int = 0,
        limit: Int = 200,
    ): PostWithComments {
        val json = client.get(
            "/comments/$postId",
            mapOf(
                "sort" to sort.param,
                "limit" to limit.toString(),
                "comment" to focusCommentId,
                "context" to context.takeIf { focusCommentId != null && it > 0 }?.toString(),
            ),
        )
        return parsed { Parser.comments(json) }
    }

    /**
     * Loads up to [MORE_PER_LOAD] of a "more" row's [children]. Reddit answers /api/morechildren with a 403
     * ("blocked by network security") for this kind of client, so each one is read from its own comment page
     * instead, a few at a time. Fails only if none loaded; failed ones stay in [CommentTree.MoreLoaded.remaining].
     */
    suspend fun moreChildren(postId: String, children: List<String>, sort: CommentSort): CommentTree.MoreLoaded = coroutineScope {
        val batch = children.take(MORE_PER_LOAD)
        val gate = Semaphore(MORE_PARALLEL)
        val results = batch.map { id ->
            async {
                gate.withPermit {
                    try {
                        Result.success(CommentTree.subtree(comments(postId, sort, focusCommentId = id).comments, id))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        }.awaitAll()
        if (results.isNotEmpty() && results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        CommentTree.mergeThreads(batch.zip(results.map { it.getOrNull() }), rest = children.drop(batch.size))
    }

    suspend fun me(): Me = client.get("/api/v1/me").let { json -> parsed { Parser.me(json) } }

    /** The account's subscribed communities, A–Z, with icons for Explore. */
    suspend fun subscriptions(): List<Subreddit> = mine("subscriber")

    /** Communities the account moderates (Explore's Moderated section). */
    suspend fun moderated(): List<Subreddit> = mine("moderator")

    /**
     * Community names for More actions › Random and Random nsfw, Reddit-wide rather than from your subscriptions:
     * Reddit's /r/random and /r/randnsfw are gone, so this draws from the first 200 of its popular communities, or for
     * [nsfw] the NSFW ones from those plus a community search for "nsfw". Fetched once per app run, then reused.
     */
    suspend fun randomCommunityPool(nsfw: Boolean): List<String> {
        randomPools[nsfw]?.let { return it }
        val subs = mutableListOf<Subreddit>()
        suspend fun pages(path: String, extra: Map<String, String?>) {
            var after: String? = null
            repeat(2) {
                val page = Parser.subredditListing(client.get(path, extra + mapOf("limit" to "100", "after" to after)))
                subs += page.items
                after = page.after ?: return
            }
        }
        val over18 = if (nsfw) mapOf("include_over_18" to "on") else emptyMap()
        pages("/subreddits/popular", over18)
        if (nsfw) pages("/subreddits/search", over18 + mapOf("q" to "nsfw"))
        val pool = subs.filter { it.over18 == nsfw }.map { it.name }.distinctBy { it.lowercase() }
        if (pool.isNotEmpty()) randomPools[nsfw] = pool
        return pool
    }

    private val randomPools = java.util.concurrent.ConcurrentHashMap<Boolean, List<String>>()

    private suspend fun mine(where: String): List<Subreddit> {
        val subs = mutableListOf<Subreddit>()
        var after: String? = null
        do {
            val json = client.get("/subreddits/mine/$where", mapOf("limit" to "100", "after" to after))
            val page = parsed { Parser.subredditListing(json) }
            subs += page.items
            after = page.after
        } while (after != null && subs.size < 1000)
        return subs.sortedBy { it.name.lowercase() }
    }

    /** The names of a community's wiki pages (including "index"), e.g. "rules/faq". */
    suspend fun wikiPages(subreddit: String): List<String> =
        client.get("/r/$subreddit/wiki/pages").jsonObject.arr("data").orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }

    /**
     * A community's wiki page as HTML; "index" is the wiki's front page. Reddit opens the page with a generated
     * table of contents, which Sync leaves out.
     */
    suspend fun wikiPage(subreddit: String, page: String = "index"): String {
        val html = client.get("/r/$subreddit/wiki/$page").jsonObject.obj("data")?.string("content_html")
            ?: throw RedditException(t("r/{0} has no wiki page \"{1}\"", subreddit, page))
        return WIKI_TOC.replace(html, "")
    }

    /**
     * A community's post flairs. Reddit only lists them to people allowed to set their own (that refusal is
     * why Sync's Flairs chip errors), so otherwise they're gathered from its 100 hottest and newest posts,
     * most used first.
     */
    suspend fun linkFlairs(subreddit: String): List<LinkFlair> {
        try {
            Parser.linkFlairs(client.get("/r/$subreddit/api/link_flair_v2")).takeIf { it.isNotEmpty() }?.let { return it }
        } catch (e: RedditException) {
            if (e.code != 403 && e.code != 404) throw e
        }
        val posts = listOf(Sort.HOT, Sort(SortKind.NEW)).flatMap { posts(subreddit, it, after = null, limit = 100).items }.distinctBy { it.id }
        return flairsOf(posts)
    }

    /** Profile pictures by account fullname (t2_…); Reddit takes up to 100 ids a request. */
    suspend fun avatars(accountIds: Collection<String>): Map<String, String> =
        accountIds.chunked(100).fold(emptyMap()) { found, ids ->
            found + Parser.avatars(client.get("/api/user_data_by_account_ids", mapOf("ids" to ids.joinToString(","))))
        }

    suspend fun rules(subreddit: String): List<SubredditRule> = Parser.rules(client.get("/r/$subreddit/about/rules"))

    suspend fun reportReasons(subreddit: String, comment: Boolean): List<ReportReason> =
        Parser.reportReasons(client.get("/r/$subreddit/about/rules"), comment)

    /** Reports [fullname] for [reason], or for [other] (free text) when there's no listed reason. */
    suspend fun report(fullname: String, reason: ReportReason?, other: String? = null) {
        val params = mutableMapOf("api_type" to "json", "thing_id" to fullname)
        when {
            reason == null -> params += mapOf("reason" to "other", "other_reason" to other.orEmpty())
            reason.siteRule -> params += mapOf("reason" to reason.text, "site_reason" to reason.text)
            else -> params += mapOf("reason" to reason.text, "rule_reason" to reason.text)
        }
        Parser.checkErrors(client.post("/api/report", params))
    }

    suspend fun moderators(subreddit: String): List<String> = Parser.moderators(client.get("/r/$subreddit/about/moderators"))

    suspend fun subredditAbout(name: String): Subreddit {
        val data = client.get("/r/$name/about").jsonObject.obj("data") ?: throw RedditException(t("No info for r/{0}", name))
        return Parser.subreddit(data)
    }

    suspend fun user(name: String): UserProfile = client.get("/user/$name/about").let { json -> parsed { Parser.user(json) } }

    suspend fun trophies(name: String): List<Trophy> = Parser.trophies(client.get("/api/v1/user/$name/trophies"))

    /** Needs the "account" scope, which logins from before Block was added don't have. */
    suspend fun blockUser(name: String) {
        client.post("/api/block_user", mapOf("name" to name))
    }

    suspend fun userContent(name: String, where: UserWhere, sort: UserSort, after: String?): Listing<ProfileItem> {
        val json = client.get(
            "/user/$name/${where.path}",
            mapOf("sort" to sort.param, "t" to sort.time, "after" to after, "limit" to "25"),
        )
        return parsed { Parser.profileListing(json) }
    }

    suspend fun inbox(box: InboxBox, after: String?): Listing<InboxItem> {
        // mark=false: opening a box shouldn't silently mark everything read.
        val json = client.get("/message/${box.path}", mapOf("after" to after, "limit" to "50", "mark" to "false"))
        return parsed { Parser.inbox(json) }
    }

    /** [multi] ("user/feedname") searches inside a custom feed instead of a community. */
    suspend fun searchPosts(query: String, subreddit: String?, sort: SearchSort, after: String?, multi: String? = null): Listing<Post> {
        val path = when {
            multi != null -> multiSearchPath(multi)
            subreddit == null -> "/search"
            else -> "/r/$subreddit/search"
        }
        val json = client.get(
            path,
            mapOf(
                "q" to query,
                "sort" to sort.param,
                "t" to sort.time,
                "type" to "link",
                "restrict_sr" to (subreddit ?: multi)?.let { "on" },
                "after" to after,
                "limit" to "25",
            ),
        )
        return parsed { Parser.postListing(json) }.also { PostCache.putAll(it.items) }
    }

    suspend fun searchSubreddits(query: String, after: String?): Listing<Subreddit> {
        val json = client.get("/subreddits/search", mapOf("q" to query, "after" to after, "limit" to "25"))
        return parsed { Parser.subredditListing(json) }
    }

    /** A mod section's posts and comments (needs the "modposts" scope and moderator rights). */
    suspend fun modListing(subreddit: String, section: ModSection, after: String?): Listing<ProfileItem> {
        val json = client.get("/r/$subreddit/about/${section.path}", mapOf("after" to after, "limit" to "25", "only" to null))
        return parsed { Parser.profileListing(json) }
    }

    suspend fun approve(fullname: String) {
        client.post("/api/approve", mapOf("id" to fullname))
    }

    /** Removes [fullname]; [spam] also trains Reddit's spam filter. */
    suspend fun remove(fullname: String, spam: Boolean) {
        client.post("/api/remove", mapOf("id" to fullname, "spam" to spam.toString()))
    }

    // --- New-style mod mail (needs the "modmail" scope) ---

    /**
     * A page of mod mail in [state]; [communities] narrows it to those subreddits (all you moderate when empty).
     * Reddit pages by conversation id, so [after] is the last id of the previous page.
     */
    suspend fun modMail(state: ModMailState, communities: List<String>, after: String?, limit: Int = 25): Listing<ModMailConversation> {
        val items = Parser.modMailConversations(
            client.get(
                "/api/mod/conversations",
                mapOf(
                    "state" to state.param,
                    "sort" to "recent",
                    "entity" to communities.takeIf { it.isNotEmpty() }?.joinToString(","),
                    "after" to after,
                    "limit" to limit.toString(),
                ),
            ),
        )
        return Listing(items, items.lastOrNull()?.id?.takeIf { items.size >= limit })
    }

    /** One conversation with its messages; [markRead] also marks it read. */
    suspend fun modMailThread(id: String, markRead: Boolean = true): ModMailThread =
        Parser.modMailThread(client.get("/api/mod/conversations/$id", mapOf("markRead" to markRead.toString())))

    /** Replies to conversation [id] as yourself (not as the subreddit); returns the updated conversation. */
    suspend fun replyModMail(id: String, markdown: String, internal: Boolean = false): ModMailThread =
        Parser.modMailThread(
            client.post(
                "/api/mod/conversations/$id",
                mapOf("body" to markdown, "isAuthorHidden" to "false", "isInternal" to internal.toString()),
            ),
        )

    suspend fun setModMailArchived(id: String, archived: Boolean) {
        client.post("/api/mod/conversations/$id/${if (archived) "archive" else "unarchive"}", emptyMap())
    }

    /** The user flairs [subreddit] lets you pick (needs a login); empty if it allows none. */
    suspend fun userFlairs(subreddit: String): List<LinkFlair> = Parser.linkFlairs(client.get("/r/$subreddit/api/user_flair_v2"))

    /** Sets your flair in [subreddit] to [flair]; null clears it. */
    suspend fun setUserFlair(subreddit: String, me: String, flair: LinkFlair?) {
        Parser.checkErrors(
            client.post(
                "/r/$subreddit/api/selectflair",
                mapOf("api_type" to "json", "name" to me) + (flair?.let { mapOf("flair_template_id" to it.id.orEmpty(), "text" to it.text) } ?: emptyMap()),
            ),
        )
    }

    suspend fun multis(): List<Multi> = Parser.multis(client.get("/api/multi/mine"))

    /** Adds [subreddit] to the custom feed at [multiPath] ("user/bob/m/news"). */
    suspend fun addToMulti(multiPath: String, subreddit: String) {
        client.put("/api/multi/${multiPath.trim('/')}/r/$subreddit", mapOf("model" to """{"name":"$subreddit"}"""))
    }

    suspend fun removeFromMulti(multiPath: String, subreddit: String) {
        client.delete("/api/multi/${multiPath.trim('/')}/r/$subreddit")
    }

    /**
     * Creates an empty private custom feed for [user] called [displayName] and returns its path
     * ("user/bob/m/my_feed"). Reddit answers 409 when that URL name is taken.
     */
    suspend fun createMulti(user: String, displayName: String): String {
        val path = "user/$user/m/${multiName(displayName)}"
        client.post("/api/multi/$path", mapOf("model" to multiModel(displayName.trim(), emptyList(), "private")))
        return path
    }

    /** Renames the custom feed at [multiPath]; only its display name changes, its URL stays. */
    suspend fun renameMulti(multiPath: String, displayName: String) {
        client.put("/api/multi/${multiPath.trim('/')}", mapOf("model" to multiModel(displayName = displayName.trim())))
    }

    suspend fun deleteMulti(multiPath: String) {
        client.delete("/api/multi/${multiPath.trim('/')}")
    }

    // --- Writing ---

    /** [direction]: 1 up, 0 clear, -1 down. */
    suspend fun vote(fullname: String, direction: Int) {
        client.post("/api/vote", mapOf("id" to fullname, "dir" to direction.toString()))
    }

    suspend fun setSaved(fullname: String, saved: Boolean) {
        client.post(if (saved) "/api/save" else "/api/unsave", mapOf("id" to fullname))
    }

    suspend fun setHidden(fullname: String, hidden: Boolean) {
        client.post(if (hidden) "/api/hide" else "/api/unhide", mapOf("id" to fullname))
    }

    suspend fun setSubscribed(subreddit: String, subscribed: Boolean) {
        client.post("/api/subscribe", mapOf("action" to if (subscribed) "sub" else "unsub", "sr_name" to subreddit))
    }

    /** Replies to a post (t3_) or comment (t1_), or answers a message (t4_). */
    suspend fun reply(parentFullname: String, markdown: String): Comment? =
        Parser.postedComment(
            client.post("/api/comment", mapOf("api_type" to "json", "thing_id" to parentFullname, "text" to markdown)),
        )

    /** Returns the updated comment when editing a comment; null for self posts. */
    suspend fun edit(fullname: String, markdown: String): Comment? =
        Parser.postedComment(
            client.post("/api/editusertext", mapOf("api_type" to "json", "thing_id" to fullname, "text" to markdown)),
        )

    suspend fun delete(fullname: String) {
        client.post("/api/del", mapOf("id" to fullname))
    }

    /** Returns the new post's ID. [body] is markdown for self posts, the URL for links. */
    suspend fun submit(
        subreddit: String,
        title: String,
        isLink: Boolean,
        body: String,
        nsfw: Boolean,
        spoiler: Boolean,
        sendReplies: Boolean = true,
        flair: LinkFlair? = null,
    ): String = Parser.submittedId(
        client.post(
            "/api/submit",
            mapOf(
                "api_type" to "json",
                "sr" to subreddit,
                "kind" to if (isLink) "link" else "self",
                "title" to title,
                (if (isLink) "url" else "text") to body,
                "nsfw" to nsfw.toString(),
                "spoiler" to spoiler.toString(),
                "sendreplies" to sendReplies.toString(),
                "resubmit" to "true",
            ) + (flair?.id?.let { mapOf("flair_id" to it, "flair_text" to flair.text) } ?: emptyMap()),
        ),
    )

    /**
     * A photo post: uploads [bytes], then submits it. Reddit answers image posts before they exist (the id arrives
     * over a websocket), so this looks for the author's newest post afterwards; null if it hasn't shown up yet.
     */
    suspend fun submitImage(
        subreddit: String,
        title: String,
        author: String,
        filename: String,
        mime: String,
        bytes: ByteArray,
        nsfw: Boolean,
        spoiler: Boolean,
        sendReplies: Boolean = true,
        flair: LinkFlair? = null,
    ): String? {
        val url = client.uploadImage(filename, mime, bytes)
        val root = client.post(
            "/api/submit",
            mapOf(
                "api_type" to "json", "sr" to subreddit, "kind" to "image", "title" to title, "url" to url,
                "nsfw" to nsfw.toString(), "spoiler" to spoiler.toString(), "sendreplies" to sendReplies.toString(),
                "resubmit" to "true",
            ) + (flair?.id?.let { mapOf("flair_id" to it, "flair_text" to flair.text) } ?: emptyMap()),
        )
        runCatching { Parser.submittedId(root) }.getOrNull()?.let { return it }
        Parser.checkErrors(root)
        repeat(4) {
            kotlinx.coroutines.delay(2000)
            val latest = (userContent(author, UserWhere.SUBMITTED, UserSort.NEW, null).items.firstOrNull() as? ProfileItem.PostItem)?.post
            if (latest != null && latest.title == title) return latest.id
        }
        return null
    }

    /**
     * A gallery post: uploads each of [images] (2 to 20), then submits them in order with their captions. Gallery
     * posts answer with the new post's id straight away, unlike single photos.
     */
    suspend fun submitGallery(
        subreddit: String,
        title: String,
        images: List<GalleryUpload>,
        nsfw: Boolean,
        spoiler: Boolean,
        sendReplies: Boolean = true,
        flair: LinkFlair? = null,
    ): String {
        val items = images.map { image ->
            val uploaded = client.uploadMedia(image.filename, image.mime, image.bytes)
            val assetId = uploaded.assetId ?: throw RedditException(t("Reddit didn't offer an upload"))
            buildJsonObject {
                put("media_id", assetId)
                put("caption", image.caption.take(180))
                put("outbound_url", "")
            }
        }
        val body = buildJsonObject {
            put("api_type", "json")
            put("show_error_list", true)
            put("sr", subreddit)
            put("submit_type", "subreddit")
            put("title", title)
            put("nsfw", nsfw)
            put("spoiler", spoiler)
            put("sendreplies", sendReplies)
            flair?.id?.let {
                put("flair_id", it)
                put("flair_text", flair.text)
            }
            put("items", JsonArray(items))
        }
        return Parser.galleryId(client.postJson("/api/submit_gallery_post.json", body))
    }

    suspend fun sendMessage(to: String, subject: String, markdown: String) {
        Parser.checkErrors(
            client.post("/api/compose", mapOf("api_type" to "json", "to" to to, "subject" to subject, "text" to markdown)),
        )
    }

    suspend fun markRead(fullnames: List<String>) {
        if (fullnames.isNotEmpty()) client.post("/api/read_message", mapOf("id" to fullnames.joinToString(",")))
    }

    suspend fun markAllRead() {
        client.post("/api/read_all_messages", emptyMap())
    }

    /** Listings and comment threads walk hundreds of items, so they're turned into models off the main thread. */
    private suspend fun <T> parsed(parse: () -> T): T = withContext(Dispatchers.Default) { parse() }
}

/** One picture of a gallery post, read and ready to upload. */
class GalleryUpload(val filename: String, val mime: String, val bytes: ByteArray, val caption: String = "")
