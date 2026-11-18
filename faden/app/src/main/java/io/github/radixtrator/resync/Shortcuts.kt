package io.github.radixtrator.resync

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation3.runtime.NavKey
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.InboxRoute
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.SubmitRoute

/**
 * The launcher icon's long-press shortcuts: your first favorite community, Search, and when logged in, Inbox and
 * Create post. They're dynamic rather than res/xml ones so their labels follow Faden's own language setting, and
 * are set again on each launch so a new favorite, account or language shows up.
 */
object Shortcuts {
    private const val ACTION = "io.github.radixtrator.resync.action.SHORTCUT"
    private const val EXTRA_TARGET = "target"
    private const val EXTRA_SUBREDDIT = "subreddit"

    private const val SEARCH = "search"
    private const val INBOX = "inbox"
    private const val SUBMIT = "submit"
    private const val FEED = "feed"

    /** The page a shortcut's intent asks for, or null if [intent] isn't one of these shortcuts. */
    fun routeFrom(intent: Intent): NavKey? {
        if (intent.action != ACTION) return null
        return route(intent.getStringExtra(EXTRA_TARGET), intent.getStringExtra(EXTRA_SUBREDDIT))
    }

    internal fun route(target: String?, subreddit: String?): NavKey? = when (target) {
        SEARCH -> SearchRoute()
        INBOX -> InboxRoute(home = true)
        SUBMIT -> SubmitRoute()
        // Only a plain community name: anything else from another app naming this action is ignored.
        FEED -> subreddit?.takeIf { COMMUNITY.matches(it) }?.let { FeedRoute(it) }
        else -> null
    }

    private val COMMUNITY = Regex("""[A-Za-z0-9_]{2,21}""")

    /** Sets the shortcuts; a binder call each, so this is called off the main thread. */
    fun publish(context: Context, favorites: List<String>, loggedIn: Boolean) {
        val shortcuts = buildList {
            favorites.firstOrNull { COMMUNITY.matches(it) }?.let { sub ->
                add(shortcut(context, "$FEED:${sub.lowercase()}", Format.feedName(sub), "r/$sub", R.drawable.ic_shortcut_feed, FEED) {
                    putExtra(EXTRA_SUBREDDIT, sub)
                })
            }
            add(shortcut(context, SEARCH, t("Search"), t("Search Reddit"), R.drawable.ic_shortcut_search, SEARCH))
            if (loggedIn) {
                add(shortcut(context, INBOX, t("Inbox"), t("Inbox"), R.drawable.ic_shortcut_inbox, INBOX))
                add(shortcut(context, SUBMIT, t("New post"), t("Create post"), R.drawable.ic_shortcut_post, SUBMIT))
            }
        }.take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1))
        // A launcher can refuse (rate limits, a work profile's policy); shortcuts are a convenience, so that's only logged.
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: RuntimeException) {
            Log.w("Shortcuts", "Couldn't set launcher shortcuts", e)
        }
    }

    private fun shortcut(
        context: Context,
        id: String,
        short: String,
        long: String,
        icon: Int,
        target: String,
        extras: Intent.() -> Unit = {},
    ): ShortcutInfoCompat = ShortcutInfoCompat.Builder(context, id)
        .setShortLabel(short)
        .setLongLabel(long)
        .setIcon(IconCompat.createWithResource(context, icon))
        .setIntent(Intent(context, MainActivity::class.java).setAction(ACTION).putExtra(EXTRA_TARGET, target).apply(extras))
        .build()
}
