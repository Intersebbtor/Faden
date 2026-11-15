package io.github.radixtrator.resync.ui.common

import io.github.radixtrator.resync.i18n.I18n
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.i18n.tn
import java.util.Locale
import kotlin.math.abs

object Format {
    /** Sync names feeds plainly: "Frontpage" (null or ""), "Popular", "All", or the subreddit as is. */
    fun feedName(subreddit: String?): String = when (subreddit?.lowercase()) {
        null, "" -> t("Frontpage")
        "popular" -> t("Popular")
        "all" -> t("All")
        else -> subreddit
    }

    fun timeAgo(epochSeconds: Long, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        val s = (nowSeconds - epochSeconds).coerceAtLeast(0)
        return when {
            s < 60 -> t("just now")
            s < 3_600 -> t("{0}m", s / 60)
            s < 86_400 -> t("{0}h", s / 3_600)
            s < 86_400 * 30 -> t("{0}d", s / 86_400)
            s < 86_400 * 365 -> t("{0}mo", s / (86_400 * 30))
            else -> t("{0}y", s / (86_400 * 365))
        }
    }

    fun count(n: Int): String = when {
        abs(n) < 1_000 -> n.toString()
        abs(n) < 100_000 -> oneDecimal(n / 1_000.0, "k")
        abs(n) < 1_000_000 -> "${n / 1_000}k"
        else -> oneDecimal(n / 1_000_000.0, "m")
    }

    /** "1.2k", or "1,2k" in German (decimal comma); a ".0" is dropped ("5k"). */
    private fun oneDecimal(value: Double, unit: String): String {
        val text = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
        val decimal = if (I18n.locale.language == Locale.GERMAN.language) text.replace('.', ',') else text
        return decimal + unit
    }

    /** [count] when [short], else the whole number with grouping ("10,600"): Sync's "Display short scores". */
    fun count(n: Int, short: Boolean): String = if (short) count(n) else String.format(I18n.locale, "%,d", n)

    /** "1 point" / "10,600 points"; [short]: Post options › "Display short scores" (10.6k, else 10,600). */
    fun points(n: Int, short: Boolean): String = tn(n, "{0} point", "{0} points", count(n, short))

    /** "1 comment" / "10,600 comments", counted like [points]. */
    fun comments(n: Int, short: Boolean): String = tn(n, "{0} comment", "{0} comments", count(n, short))

    /** Community sizes, which Sync writes in capitals ("33.6M") unlike points ("4.3k"). */
    fun members(n: Int): String = count(n).uppercase(Locale.ROOT)
}

/** Reddit's tri-state vote ("likes") as -1/0/1. */
fun voteValue(likes: Boolean?): Int = when (likes) {
    true -> 1
    false -> -1
    null -> 0
}

fun likesFor(direction: Int): Boolean? = when {
    direction > 0 -> true
    direction < 0 -> false
    else -> null
}
