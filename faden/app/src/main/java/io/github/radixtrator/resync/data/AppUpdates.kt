package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One version's entry in whats-new.md: its "## x.y.z" heading and the bullets under it. */
data class VersionNotes(val version: String, val items: List<String>)

/** The newest published release on GitHub. */
data class LatestRelease(val version: String, val url: String, val items: List<String>)

/** Reading whats-new.md and release bodies, and comparing versions. */
object WhatsNew {
    const val REPO = "Radixtrator/Faden"

    fun releaseUrl(version: String) = "https://github.com/$REPO/releases/tag/v$version"

    /** Every "## x.y.z" section of whats-new.md, newest first as written. */
    fun parse(markdown: String): List<VersionNotes> {
        val out = mutableListOf<VersionNotes>()
        var version: String? = null
        val items = mutableListOf<String>()
        fun flush() {
            version?.let { if (items.isNotEmpty()) out += VersionNotes(it, items.toList()) }
            items.clear()
        }
        for (raw in markdown.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("## ") -> {
                    flush()
                    version = line.removePrefix("## ").trim().removePrefix("v")
                }
                version != null && bullet(line) != null -> items += bullet(line)!!
            }
        }
        flush()
        return out
    }

    /**
     * What to show after an update to [current] from [lastSeen]: every section in between. With no [lastSeen]
     * (the version before this one didn't record it), just [current]'s.
     */
    fun since(all: List<VersionNotes>, lastSeen: String?, current: String): List<VersionNotes> = all.filter {
        compare(it.version, current) <= 0 && if (lastSeen == null) compare(it.version, current) == 0 else compare(it.version, lastSeen) > 0
    }

    /** The bullets of a GitHub release body's "What's new" section, as the release workflow writes it. */
    fun releaseItems(body: String): List<String> {
        val lines = body.lineSequence().map { it.trim() }.toList()
        val start = lines.indexOfFirst { it.startsWith("#") && it.contains("What's new", ignoreCase = true) }
        if (start < 0) return emptyList()
        return lines.drop(start + 1)
            .takeWhile { it.isEmpty() || bullet(it) != null }
            .mapNotNull(::bullet)
    }

    /** Compares the numeric x.y.z parts; a "-suffix" (like "-debug") is ignored. */
    fun compare(a: String, b: String): Int {
        val pa = numbers(a)
        val pb = numbers(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /**
     * Whether release [a] is newer than [b], counting pre-releases: like [compare], but at equal numbers a pre-release
     * ("0.14.0-beta.1") is older than its final release ("0.14.0"), and pre-releases order by their suffix
     * ("beta.2" after "beta.1"). "-debug" isn't a pre-release, so a debug build of 0.14.0 counts as 0.14.0.
     */
    fun isNewer(a: String, b: String): Boolean {
        val byNumber = compare(a, b)
        if (byNumber != 0) return byNumber > 0
        val pa = preRelease(a)
        val pb = preRelease(b)
        return when {
            pa == null || pb == null -> pa == null && pb != null
            else -> comparePreRelease(pa, pb) > 0
        }
    }

    /** The "-suffix" of a pre-release, or null for a final release or a "-debug" build. */
    private fun preRelease(version: String): String? =
        version.substringAfter('-', "").takeIf { it.isNotEmpty() && !it.startsWith("debug") }

    /** "beta.10" after "beta.2": dot-separated parts, numbers by value, words alphabetically, a longer one last. */
    private fun comparePreRelease(a: String, b: String): Int {
        val pa = a.split('.')
        val pb = b.split('.')
        for (i in 0 until minOf(pa.size, pb.size)) {
            val na = pa[i].toIntOrNull()
            val nb = pb[i].toIntOrNull()
            val c = when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1
                nb != null -> 1
                else -> pa[i].compareTo(pb[i])
            }
            if (c != 0) return c
        }
        return pa.size.compareTo(pb.size)
    }

    private fun numbers(version: String) =
        version.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    /** A "- item" or "* item" line's text, with markdown emphasis and code marks dropped. */
    private fun bullet(line: String): String? {
        if (!line.startsWith("- ") && !line.startsWith("* ")) return null
        return line.drop(2).replace("**", "").replace("`", "").trim().ifEmpty { null }
    }
}

/**
 * The version whose notes were last shown, and the update check's state: when GitHub was last asked, and the
 * newer version the user said "Later" to.
 */
class AppUpdates(private val prefs: SharedPreferences) {
    var lastSeenVersion: String?
        get() = prefs.getString(KEY_SEEN, null)
        set(value) = prefs.edit { putString(KEY_SEEN, value) }

    var dismissedVersion: String?
        get() = prefs.getString(KEY_DISMISSED, null)
        set(value) = prefs.edit { putString(KEY_DISMISSED, value) }

    private var lastCheck: Long
        get() = prefs.getLong(KEY_CHECKED, 0)
        set(value) = prefs.edit { putLong(KEY_CHECKED, value) }

    private val client by lazy { Http.base.newBuilder().callTimeout(15, TimeUnit.SECONDS).build() }

    /**
     * The latest GitHub release if it's newer than [current] and not put off with "Later". Asks GitHub at most
     * every [CHECK_EVERY_MS]; failures are quiet, as there's nothing for the user to do about them.
     */
    suspend fun newerRelease(current: String): LatestRelease? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastCheck < CHECK_EVERY_MS) return@withContext null
        val release = runCatching { fetchLatest() }.getOrNull() ?: return@withContext null
        lastCheck = now
        // On a beta, the final release of the same version counts as newer.
        release.takeIf { WhatsNew.isNewer(it.version, current) && it.version != dismissedVersion }
    }

    private fun fetchLatest(): LatestRelease? {
        // /releases/latest leaves out drafts and pre-releases.
        val request = Request.Builder()
            .url("https://api.github.com/repos/${WhatsNew.REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = Json.parseToJsonElement(response.body.string()).jsonObject
            return parseRelease(json)
        }
    }

    companion object {
        private const val KEY_SEEN = "updates.last_seen_version"
        private const val KEY_DISMISSED = "updates.dismissed_version"
        private const val KEY_CHECKED = "updates.last_check"
        private const val CHECK_EVERY_MS = 12 * 60 * 60 * 1000L

        /** A GitHub release object as the API returns it. */
        fun parseRelease(json: JsonObject): LatestRelease? {
            fun str(key: String) = json[key]?.jsonPrimitive?.contentOrNull
            if (json["draft"]?.jsonPrimitive?.booleanOrNull == true || json["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
            val tag = str("tag_name") ?: return null
            val version = tag.removePrefix("v")
            return LatestRelease(version, str("html_url") ?: WhatsNew.releaseUrl(version), WhatsNew.releaseItems(str("body").orEmpty()))
        }
    }
}
