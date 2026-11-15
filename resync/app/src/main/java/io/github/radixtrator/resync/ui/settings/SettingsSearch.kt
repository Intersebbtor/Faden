package io.github.radixtrator.resync.ui.settings

import androidx.navigation3.runtime.NavKey
import io.github.radixtrator.resync.data.FilterType
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.ui.navigation.CustomizeViewRoute
import io.github.radixtrator.resync.ui.navigation.FilterEditorRoute
import io.github.radixtrator.resync.ui.navigation.SettingsRoute
import io.github.radixtrator.resync.ui.navigation.SetupRoute
import io.github.radixtrator.resync.ui.navigation.ThemeManagementRoute

/**
 * One row the settings search can find: its [title] and [summary] as the row shows them, [place] for where it
 * lives ("Comment options"), and [open], the page it's on. [keywords] are other words people search for it by.
 */
data class SettingsEntry(
    val title: String,
    val summary: String?,
    val place: String,
    val open: (currentLayout: PostLayout) -> NavKey,
    val keywords: String = "",
)

/**
 * Every row on the settings pages, for searching them. The pages compose their rows directly, so this list is
 * kept beside them by hand: a row added to a page belongs here too.
 */
object SettingsIndex {
    private fun row(category: SettingsCategory, title: String, summary: String? = null, keywords: String = "") =
        SettingsEntry(title, summary, category.title, { SettingsRoute(category) }, keywords)

    private fun theme(title: String, summary: String? = null, keywords: String = "") =
        SettingsEntry(title, summary, "Theme management", { ThemeManagementRoute }, keywords)

    private fun view(title: String, summary: String? = null) =
        SettingsEntry(title, summary, "View type › Customize view", { CustomizeViewRoute(it) }, "layout card list post")

    val entries: List<SettingsEntry> by lazy {
        // The categories themselves, so "theme" or "backup" finds the page too.
        SettingsCategory.entries.map { SettingsEntry(it.title, it.summary, "Settings", { _ -> SettingsRoute(it) }) } + listOf(
            row(SettingsCategory.GENERAL, "Drawer navigation", "A side drawer, opened from your avatar, replaces the bottom bar", "menu sidebar"),
            row(SettingsCategory.GENERAL, "Confirm exit", "Ask before back closes the app", "quit close"),
            row(SettingsCategory.GENERAL, "Slide to go back", "Drag a page to the right to close it", "gesture swipe back"),
            row(SettingsCategory.GENERAL, "Typeface", keywords = "font"),
            row(SettingsCategory.GENERAL, "Base font size", keywords = "text size bigger smaller"),

            row(SettingsCategory.TABLET, "Tablet mode", "On wide screens, a navigation rail replaces the bottom bar", "foldable"),
            row(SettingsCategory.TABLET, "Navigation on the right", "Move the bottom bar to a rail on the right edge, for one-handed use", "foldable rail"),
            row(SettingsCategory.TABLET, "Dual pane (landscape)", "With Tablet mode on: comments next to posts in landscape", "split two"),
            row(SettingsCategory.TABLET, "Dual pane (portrait)", "With Tablet mode on: the same in portrait, on wide screens", "split two"),
            row(SettingsCategory.TABLET, "Tabbed comments", "With Tablet mode on: a post and its comments as tabs"),

            row(SettingsCategory.THEME, "Material You colors", "Use your wallpaper's colors", "dynamic color wallpaper"),
            row(SettingsCategory.THEME, "Theme color", keywords = "accent color"),
            row(SettingsCategory.THEME, "Extra colorful", "Tint backgrounds and cards with the accent too"),
            theme("Saved themes", "Preview, save, share and load themes"),
            theme("Color intensity", keywords = "accent"),
            theme("Save current theme"),
            theme("View palette", "The colors the theme in use is built from"),
            theme("Share theme", "Send the theme you're using as a code to paste"),
            theme("Load theme", "Paste a shared theme code", "import"),

            row(SettingsCategory.DARK_MODE, "Theme", "Light, dark, black or follow the system", "night amoled"),
            row(SettingsCategory.DARK_MODE, "Dark overlay", "Dim the whole app, for reading at night", "dim brightness"),
            row(SettingsCategory.DARK_MODE, "Overlay darkness", "With Dark overlay on", "dim brightness"),

            row(SettingsCategory.VIEW_TYPE, "Default post layout", keywords = "card list compact slides"),
            SettingsEntry("Customize view", "Each layout's previews, sizes and buttons", SettingsCategory.VIEW_TYPE.title, { CustomizeViewRoute(it) }),
            row(SettingsCategory.VIEW_TYPE, "Post media preview size", keywords = "image cropped"),

            row(SettingsCategory.COMMENTS, "Show user flair"),
            row(SettingsCategory.COMMENTS, "Show profile pictures", "Authors' avatars in comment headers", "avatar"),
            row(SettingsCategory.COMMENTS, "Mark new comments", "Label comments posted since you last opened the thread", "unread label"),
            row(SettingsCategory.COMMENTS, "Hide AutoMod comments", "And the replies to them", "bot automoderator"),
            row(SettingsCategory.COMMENTS, "Live comments refresh rate", keywords = "sports mode"),

            row(SettingsCategory.POSTS, "NSFW posts", keywords = "blur adult"),
            row(SettingsCategory.POSTS, "Blur NSFW only on Frontpage, Popular & All", "With NSFW posts set to blur"),
            row(SettingsCategory.POSTS, "Blur spoiler previews"),
            row(SettingsCategory.POSTS, "Show post flair", "Flair chips on posts in feeds"),
            row(SettingsCategory.POSTS, "Show post author", "In feeds"),
            row(SettingsCategory.POSTS, "Ask to keep a new sort", "When you change a feed's sort, ask whether to always open it that way", "always default hot new top"),
            row(SettingsCategory.POSTS, "Display short scores", "10.6k instead of 10,600", "points karma"),
            row(SettingsCategory.POSTS, "Swipe posts", "Drag a post left; drag further for the next action", "gesture"),
            row(SettingsCategory.POSTS, "Post swipe actions", "First, second and third swipe action", "gesture"),

            row(SettingsCategory.COMMENT_OPTIONS, "Default comment sort", keywords = "best top new"),
            row(SettingsCategory.COMMENT_OPTIONS, "Collapse all threads by default", "The first time you open a post", "fold"),
            row(SettingsCategory.COMMENT_OPTIONS, "Initial comment count", keywords = "load"),
            row(SettingsCategory.COMMENT_OPTIONS, "Tap to collapse", "Tap a comment to collapse it and hold it for options, instead of the other way round", "hold long press fold"),
            row(SettingsCategory.COMMENT_OPTIONS, "Comment navigation buttons", "Jump between top-level comments", "next previous"),
            row(SettingsCategory.COMMENT_OPTIONS, "Remember comment position", "Reopen posts where you left off, threads folded as you left them", "scroll"),
            row(SettingsCategory.COMMENT_OPTIONS, "Swipe comments", "Drag a comment left; drag further for the next action", "gesture"),
            row(SettingsCategory.COMMENT_OPTIONS, "Comment swipe actions", "First, second and third swipe action", "gesture"),

            row(SettingsCategory.MESSAGING, "Periodically check for new messages", keywords = "inbox notifications"),
            row(SettingsCategory.MESSAGING, "Check now", "Look for new messages, posts and comments now", "inbox notifications"),
            row(SettingsCategory.MESSAGING, "Watched communities", "A notification for each new post", "notifications subreddit"),
            row(SettingsCategory.MESSAGING, "Watched posts", "New comments are checked every hour", "notifications"),

            row(SettingsCategory.MEDIA, "Close images by", "Single tap, or swiping up or down", "viewer"),
            row(SettingsCategory.MEDIA, "Swipe to close", "Fling videos and images to close them", "viewer"),
            row(SettingsCategory.MEDIA, "Hold to preview", "Hold a picture in a feed to see it full screen until you let go", "peek"),
            row(SettingsCategory.MEDIA, "Unblur NSFW previews", "With Hold to preview on: holding a blurred NSFW picture shows it unblurred", "peek"),
            row(SettingsCategory.MEDIA, "Start videos muted", keywords = "sound audio"),
            row(SettingsCategory.MEDIA, "Display seek bar", keywords = "video"),
            row(SettingsCategory.MEDIA, "Display speed controls", keywords = "video playback"),

            row(SettingsCategory.LINKS, "Open links in the app", "Custom Tabs instead of your browser app", "browser chrome"),
            row(SettingsCategory.LINKS, "Link cards", "Number links and list them under posts and comments"),
            row(SettingsCategory.LINKS, "Pictures in comments", "Show GIFs and images under the comments that post or link them, instead of their links", "gif image photo inline preview"),
            row(SettingsCategory.LINKS, "Open Reddit links in Resync", "Links to reddit.com and redd.it from other apps open here", "default supported web"),

            row(SettingsCategory.HISTORY, "Mark posts as read on scroll", "Posts you scroll past in a feed count as read"),
            row(SettingsCategory.HISTORY, "Hide read posts", "Leave out posts you have already opened when a feed loads"),

            row(SettingsCategory.DATA, "Data saving mode", keywords = "mobile metered"),
            row(SettingsCategory.DATA, "HD Reddit video quality", keywords = "wifi"),

            row(SettingsCategory.BACKUP, "Backup now", "Save a backup file wherever you choose", "export"),
            row(SettingsCategory.BACKUP, "Restore from backup", "Replace this device's data with a backup file", "import"),

            SettingsEntry("Reddit API", "Client ID, redirect URI, user agent", SettingsCategory.OTHER.title, { SetupRoute }, "login key"),
            row(SettingsCategory.OTHER, "Version", keywords = "about"),
            row(SettingsCategory.OTHER, "What's new", "What changed in each version", "changelog release notes update"),
            row(SettingsCategory.OTHER, "Check for updates", "Say when a new version is out on GitHub", "release version"),
        ) +
            FilterType.entries.map { type -> SettingsEntry(type.title, type.summary, SettingsCategory.FILTERS.title, { FilterEditorRoute(type) }, "hide block") } +
            ViewOption.entries.distinctBy { it.title }.map { view(it.title, it.summary) } +
            ViewFontSize.entries.map { view(it.title) } +
            listOf(view("Columns on main screen"), view("Columns on cover screen"))
    }

    /**
     * The entries matching every word of [query], anywhere in their title, summary, place or keywords; ones whose
     * title has the words come first, then ones whose title starts with the query.
     */
    fun search(query: String, from: List<SettingsEntry> = entries): List<SettingsEntry> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val q = query.trim().lowercase()
        return from
            .filter { e ->
                val haystack = listOfNotNull(e.title, e.summary, e.place, e.keywords).joinToString(" ").lowercase()
                words.all { it in haystack }
            }
            .sortedWith(
                compareBy<SettingsEntry> { e -> if (words.all { it in e.title.lowercase() }) 0 else 1 }
                    .thenBy { e -> if (e.title.lowercase().startsWith(q)) 0 else 1 },
            )
    }
}
