package io.github.radixtrator.resync

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import io.github.radixtrator.resync.ui.navigation.SubmitRoute

/**
 * Something shared to Faden from another app (Android's share sheet), turned into a new post: a link shared from
 * a browser becomes a link post titled with the page's title, other text a text post, a picture a photo post and
 * several pictures a gallery.
 */
object SharedContent {
    private val URL = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    fun routeFrom(intent: Intent): SubmitRoute? {
        if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.type.orEmpty().startsWith("image/")) {
            val streams = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            return photos(streams.map { it.toString() }, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        }
        if (intent.action != Intent.ACTION_SEND) return null
        val type = intent.type.orEmpty()
        if (type.startsWith("image/")) {
            val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            return photos(listOfNotNull(stream?.toString()), intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TEXT))
        }
        return text(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    /**
     * Only content: URIs, which the sharing app grants Faden for this share. A file: URI (or one naming Faden's own
     * files) from another app could otherwise get Faden to upload its own private files.
     */
    internal fun photos(uris: List<String>, title: String?): SubmitRoute? {
        val picked = uris.filter(::isOthersContent).take(GALLERY_MAX)
        if (picked.isEmpty()) return null
        return SubmitRoute(title = title.orEmpty().trim().take(TITLE_MAX), photos = picked)
    }

    private fun isOthersContent(uri: String): Boolean {
        if (!uri.startsWith("content://", ignoreCase = true)) return false
        return !uri.removePrefix("content://").substringBefore('/').startsWith(BuildConfig.APPLICATION_ID)
    }

    /**
     * A shared web page usually arrives as its URL, often with the page title in the subject or before the URL
     * ("Some article https://…"); that's a link post. Text without a link is a text post's body.
     */
    internal fun text(text: String?, subject: String?): SubmitRoute? {
        val body = text?.trim().orEmpty()
        if (body.isEmpty()) return null
        // The whole match comes out of the text, so punctuation after the link doesn't end up in the title.
        val match = URL.find(body)?.value
        val url = match?.trimEnd('.', ',', ')', ']', '"', '\'')
        val rest = if (match != null) body.replace(match, "").trim() else ""
        return if (url != null && rest.length <= TITLE_MAX) {
            SubmitRoute(title = (subject?.trim()?.takeIf { it.isNotEmpty() } ?: rest).take(TITLE_MAX), url = url)
        } else {
            SubmitRoute(title = subject?.trim().orEmpty().take(TITLE_MAX), text = body)
        }
    }

    /** Reddit's longest title. */
    private const val TITLE_MAX = 300

    /** The most pictures a Reddit gallery takes. */
    const val GALLERY_MAX = 20
}
