package io.github.radixtrator.resync.data

import io.github.radixtrator.resync.i18n.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import java.io.IOException

/**
 * Streamable links (streamable.com/<code>) are web pages; Streamable's public API (no key needed) names the clip's mp4s,
 * so they play in Faden's own player, with sound, instead of in a browser.
 */
object Streamable {
    private val PAGE = Regex(
        """^https?://(?:www\.)?streamable\.com/(?:[eos]/)?([a-z0-9]+)/?(?:[?#].*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private const val API = "https://api.streamable.com/videos/"

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val cache = HashMap<String, HostedClip>()

    /** The clip's code in a Streamable link; null for other links. */
    fun id(url: String): String? = PAGE.find(url)?.groupValues?.get(1)

    /** The clip's mp4s (cached for the session). Throws when Streamable can't be reached, or the clip is gone or not ready. */
    suspend fun resolve(id: String): HostedClip = withContext(Dispatchers.IO) {
        lock.withLock { cache[id] } ?: fetch(id).also { clip -> lock.withLock { cache[id] = clip } }
    }

    private fun fetch(id: String): HostedClip {
        val request = Request.Builder().url(API + id).build()
        val (code, body) = Http.base.newCall(request).execute().use { it.code to it.body.string() }
        if (code == 404 || code == 410) throw IOException(t("This Streamable clip was removed"))
        if (code !in 200..299) throw IOException("Streamable HTTP $code")
        return parseVideo(json.parseToJsonElement(body) as? JsonObject) ?: throw IOException(t("Streamable sent no video"))
    }

    /**
     * /videos/<code>: {"status": 2, "files": {"mp4": {"url"}, "mp4-mobile": {"url"}}}. Status 2 is ready; earlier ones
     * are still processing. URLs can come without a scheme ("//cdn…").
     */
    internal fun parseVideo(root: JsonObject?): HostedClip? {
        if (root == null) return null
        if (root.int("status").let { it != null && it != 2 }) throw IOException(t("Streamable is still processing this clip"))
        val files = root.obj("files") ?: return null
        fun url(key: String) = files.obj(key)?.string("url")?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("//")) "https:$it" else it }
        val hd = url("mp4")
        val sd = url("mp4-mobile")
        if (hd == null && sd == null) return null
        return HostedClip(sd = sd ?: hd!!, hd = hd ?: sd!!, hasAudio = true, repeats = false)
    }
}

/** A clip on a video site, looked up to its mp4s: [sd] plays first, [hd] behind the HD button (they can be the same file). */
data class HostedClip(val sd: String, val hd: String, val hasAudio: Boolean, val repeats: Boolean, val userAgent: String? = null)

/**
 * Video sites whose links are pages, not video files, and that Faden plays itself after asking the site for the clip's
 * files: RedGifs and Streamable.
 */
object ClipHosts {
    fun isHosted(url: String): Boolean = Redgifs.id(url) != null || Streamable.id(url) != null

    /** The site's name, for "Couldn't reach …". */
    fun name(url: String): String = if (Streamable.id(url) != null) "Streamable" else "RedGifs"

    suspend fun resolve(url: String): HostedClip {
        Redgifs.id(url)?.let { id ->
            val video = Redgifs.resolve(id)
            // Clips with sound play like videos; silent ones like GIFs. Both repeat.
            return HostedClip(video.sd, video.hd, video.hasAudio, repeats = true, userAgent = Redgifs.USER_AGENT)
        }
        Streamable.id(url)?.let { return Streamable.resolve(it) }
        throw IOException(t("No app can open {0}", url))
    }
}
