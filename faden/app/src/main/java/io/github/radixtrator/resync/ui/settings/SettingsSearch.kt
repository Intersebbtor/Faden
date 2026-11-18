package io.github.radixtrator.resync.ui.settings

import androidx.navigation3.runtime.NavKey
import io.github.radixtrator.resync.data.FilterType
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.i18n.t
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
        SettingsEntry(title, summary, t("Theme management"), { ThemeManagementRoute }, keywords)

    // Every view setting is on Customize view, which is the View type category's page.
    private fun view(title: String, summary: String? = null, keywords: String = "") =
        SettingsEntry(title, summary, t("View type"), { CustomizeViewRoute(it) }, "layout card list post ansicht karte liste pfosten customize anpassen $keywords")

    /** Built on each read, so titles and summaries follow the current language. */
    val entries: List<SettingsEntry>
        get() =
            // The categories themselves, so "theme" or "backup" finds the page too.
            SettingsCategory.entries.map { category -> SettingsEntry(category.title, category.summary, t("Settings"), { layout -> category.route(layout) }) } + listOf(
                row(SettingsCategory.GENERAL, t("Language"), keywords = "language deutsch german english sprache"),
                row(SettingsCategory.GENERAL, t("Drawer navigation"), t("A side drawer, opened from your avatar, replaces the bottom bar"), "menu sidebar menü seitenleiste"),
                row(SettingsCategory.GENERAL, t("Confirm exit"), t("Ask before back closes the app"), "quit close beenden schließen"),
                row(SettingsCategory.GENERAL, t("Slide to go back"), t("Drag a page to the right to close it"), "gesture swipe back geste wischen fingerfegen zurück"),
                row(SettingsCategory.GENERAL, t("Typeface"), keywords = "font schrift schriftart"),
                row(SettingsCategory.GENERAL, t("Base font size"), keywords = "text size bigger smaller textgröße größer kleiner"),

                row(SettingsCategory.TABLET, t("Tablet mode"), t("On wide screens, a navigation rail replaces the bottom bar"), "foldable faltbar klapphandy"),
                row(SettingsCategory.TABLET, t("Navigation on the right"), t("Move the bottom bar to a rail on the right edge, for one-handed use"), "foldable rail faltbar leiste"),
                row(SettingsCategory.TABLET, t("Dual pane (landscape)"), t("With Tablet mode on: comments next to posts in landscape"), "split two geteilt zwei"),
                row(SettingsCategory.TABLET, t("Dual pane (portrait)"), t("With Tablet mode on: the same in portrait, on wide screens"), "split two geteilt zwei"),
                row(SettingsCategory.TABLET, t("Tabbed comments"), t("With Tablet mode on: a post and its comments as tabs")),

                row(SettingsCategory.THEME, t("Material You colors"), t("Use your wallpaper's colors"), "dynamic color wallpaper dynamisch farbe hintergrundbild"),
                row(SettingsCategory.THEME, t("Theme color"), keywords = "accent color akzent farbe"),
                row(SettingsCategory.THEME, t("Extra colorful"), t("Tint backgrounds and cards with the accent too")),
                theme(t("Saved themes"), t("Preview, save, share and load themes")),
                theme(t("Color intensity"), keywords = "accent akzent"),
                theme(t("Save current theme")),
                theme(t("View palette"), t("The colors the theme in use is built from")),
                theme(t("Share theme"), t("Send the theme you're using as a code to paste")),
                theme(t("Load theme"), t("Paste a shared theme code"), "import importieren"),

                row(SettingsCategory.DARK_MODE, t("Theme"), t("Light, dark, black or follow the system"), "night amoled nacht schwarz"),
                row(SettingsCategory.DARK_MODE, t("Dark overlay"), t("Dim the whole app, for reading at night"), "dim brightness abdunkeln helligkeit"),
                row(SettingsCategory.DARK_MODE, t("Overlay darkness"), t("With Dark overlay on"), "dim brightness abdunkeln helligkeit"),

                view(t("Default post layout"), keywords = "card list compact slides karte liste kompakt folien"),
                view(t("Post media preview size"), keywords = "image cropped bild zugeschnitten"),

                row(SettingsCategory.COMMENTS, t("Show user flair")),
                row(SettingsCategory.COMMENTS, t("Show profile pictures"), t("Authors' avatars in comment headers"), "avatar profilbild"),
                row(SettingsCategory.COMMENTS, t("Mark new comments"), t("Label comments posted since you last opened the thread"), "unread label ungelesen markierung"),
                row(SettingsCategory.COMMENTS, t("Hide AutoMod comments"), t("And the replies to them"), "bot automoderator truggenosse"),
                row(SettingsCategory.COMMENTS, t("Live comments refresh rate"), keywords = "sports mode sportmodus live"),

                row(SettingsCategory.POSTS, t("NSFW posts"), keywords = "blur adult verwischen nsfa erwachsene"),
                row(SettingsCategory.POSTS, t("Blur NSFW only on Frontpage, Popular & All"), t("With NSFW posts set to blur")),
                row(SettingsCategory.POSTS, t("Blur spoiler previews")),
                row(SettingsCategory.POSTS, t("Show post flair"), t("Flair chips on posts in feeds")),
                row(SettingsCategory.POSTS, t("Show post author"), t("In feeds")),
                row(SettingsCategory.POSTS, t("Ask to keep a new sort"), t("When you change a feed's sort, ask whether to always open it that way"), "always default hot new top immer standard heiß neu spitze sortierung"),
                row(SettingsCategory.POSTS, t("Display short scores"), t("10.6k instead of 10,600"), "points karma punkte"),
                row(SettingsCategory.POSTS, t("Swipe posts"), t("Drag a post left; drag further for the next action"), "gesture geste wischen fingerfeger"),
                row(SettingsCategory.POSTS, t("Post swipe actions"), t("First, second and third swipe action"), "gesture geste wischen fingerfeger"),

                row(SettingsCategory.COMMENT_OPTIONS, t("Default comment sort"), keywords = "best top new beste spitze neu sortierung"),
                row(SettingsCategory.COMMENT_OPTIONS, t("Collapse all threads by default"), t("The first time you open a post"), "fold einklappen falten"),
                row(SettingsCategory.COMMENT_OPTIONS, t("Initial comment count"), keywords = "load laden anzahl"),
                row(SettingsCategory.COMMENT_OPTIONS, t("Tap to collapse"), t("Tap a comment to collapse it and hold it for options, instead of the other way round"), "hold long press fold halten lang drücken einklappen"),
                row(SettingsCategory.COMMENT_OPTIONS, t("Comment navigation buttons"), t("Jump between top-level comments"), "next previous nächster vorheriger"),
                row(SettingsCategory.COMMENT_OPTIONS, t("Volume key navigation"), t("Volume buttons jump between top-level comments"), "volume lautstärke taste button knopf next previous nächster vorheriger"),
                row(SettingsCategory.COMMENT_OPTIONS, t("Remember comment position"), t("Reopen posts where you left off, threads folded as you left them"), "scroll scrollen position"),
                row(SettingsCategory.COMMENT_OPTIONS, t("Swipe comments"), t("Drag a comment left; drag further for the next action"), "gesture geste wischen fingerfeger"),
                row(SettingsCategory.COMMENT_OPTIONS, t("Comment swipe actions"), t("First, second and third swipe action"), "gesture geste wischen fingerfeger"),

                row(SettingsCategory.MESSAGING, t("Periodically check for new messages"), keywords = "inbox notifications innenschachtel benachrichtigungen"),
                row(SettingsCategory.MESSAGING, t("Check now"), t("Look for new messages, posts and comments now"), "inbox notifications innenschachtel benachrichtigungen"),
                row(SettingsCategory.MESSAGING, t("Watched communities"), t("A notification for each new post"), "notifications subreddit benachrichtigungen unterlases"),
                row(SettingsCategory.MESSAGING, t("Watched posts"), t("New comments are checked every hour"), "notifications benachrichtigungen"),

                row(SettingsCategory.MEDIA, t("Close images by"), t("Single tap, or swiping up or down"), "viewer betrachter bildansicht"),
                row(SettingsCategory.MEDIA, t("Close videos by"), t("Swiping up or down, or a single tap"), "player video tap tippen schließen"),
                row(SettingsCategory.MEDIA, t("Swipe to close"), t("Fling videos and images to close them"), "viewer betrachter bildansicht"),
                row(SettingsCategory.MEDIA, t("Hold to preview"), t("Hold a picture in a feed to see it full screen until you let go"), "peek vorschau"),
                row(SettingsCategory.MEDIA, t("Unblur NSFW previews"), t("With Hold to preview on: holding a blurred NSFW picture shows it unblurred"), "peek vorschau"),
                row(SettingsCategory.MEDIA, t("Start videos muted"), keywords = "sound audio ton stumm"),
                row(SettingsCategory.MEDIA, t("Display seek bar"), keywords = "video"),
                row(SettingsCategory.MEDIA, t("Display speed controls"), keywords = "video playback wiedergabe geschwindigkeit"),

                row(SettingsCategory.LINKS, t("Open links in the app"), t("Custom Tabs instead of your browser app"), "browser chrome brauser"),
                row(SettingsCategory.LINKS, t("Link cards"), t("Number links and list them under posts and comments")),
                row(SettingsCategory.LINKS, t("Pictures in comments"), t("Show GIFs and images under the comments that post or link them, instead of their links"), "gif image photo inline preview bild foto vorschau"),
                row(SettingsCategory.LINKS, t("Open Reddit links in Faden"), t("Links to reddit.com and redd.it from other apps open here"), "default supported web standard unterstützt lases"),

                row(SettingsCategory.HISTORY, t("Mark posts as read on scroll"), t("Posts you scroll past in a feed count as read")),
                row(SettingsCategory.HISTORY, t("Hide read posts"), t("Leave out posts you have already opened when a feed loads")),

                row(SettingsCategory.DATA, t("Data saving mode"), keywords = "mobile metered mobil getaktet datensparen"),
                row(SettingsCategory.DATA, t("HD Reddit video quality"), keywords = "wifi wlan"),
                row(SettingsCategory.DATA, t("Autoplay"), t("Videos and GIFs play muted in Cards and Slides, one at a time, unless data saving is on"), "wifi wlan feed futter play abspielen gif video"),

                row(SettingsCategory.BACKUP, t("Backup now"), t("Save a backup file wherever you choose"), "export exportieren rückhoch sicherung"),
                row(SettingsCategory.BACKUP, t("Restore from backup"), t("Replace this device's data with a backup file"), "import importieren rückhoch wiederherstellen"),

                SettingsEntry(t("Reddit API"), t("Client ID, redirect URI, user agent"), SettingsCategory.OTHER.title, { SetupRoute }, "login key anmelden schlüssel"),
                row(SettingsCategory.OTHER, t("Version"), keywords = "about über info"),
                row(SettingsCategory.OTHER, t("What's new"), t("What changed in each version"), "changelog release notes update änderungen neuigkeiten aufdatum"),
                row(SettingsCategory.OTHER, t("Check for updates"), t("Say when a new version is out on GitHub"), "release version aufdatum aktualisierung"),
                row(SettingsCategory.OTHER, t("Support Faden"), t("Donate on Ko-fi. Faden stays free either way"), "donate donation tip kofi ko-fi coffee spende spenden kaffee unterstützen"),
            ) +
                FilterType.entries.map { type -> SettingsEntry(type.title, type.summary, SettingsCategory.FILTERS.title, { FilterEditorRoute(type) }, "hide block ausblenden blockieren") } +
                ViewOption.entries.distinctBy { it.title }.map { view(it.title, it.summary) } +
                ViewFontSize.entries.map { view(it.title) } +
                listOf(view(t("Columns on main screen")), view(t("Columns on cover screen")))

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
