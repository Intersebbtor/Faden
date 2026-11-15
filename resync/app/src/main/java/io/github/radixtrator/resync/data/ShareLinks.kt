package io.github.radixtrator.resync.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reddit's Share links ("reddit.com/r/pics/s/AbC123") hide the post or comment they stand for; Reddit answers
 * them with a redirect to its permalink. The redirect is read rather than followed, so nothing is downloaded.
 */
object ShareLinks {
    private val client by lazy {
        Http.base.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** The permalink [url] redirects to, or null if Reddit doesn't say. A browser's user agent gets a 403. */
    suspend fun resolve(url: String, userAgent: String): String? = withContext(Dispatchers.IO) {
        var current = url
        // A share link can redirect to another reddit.com address before the permalink.
        repeat(3) {
            val request = Request.Builder().url(current).header("User-Agent", userAgent).get().build()
            val next = runCatching {
                client.newCall(request).execute().use { response ->
                    if (response.isRedirect) response.header("Location")?.let { response.request.url.resolve(it)?.toString() } else null
                }
            }.getOrNull() ?: return@withContext current.takeIf { it != url }
            if (!next.contains("/s/")) return@withContext next
            current = next
        }
        current.takeIf { it != url }
    }
}
