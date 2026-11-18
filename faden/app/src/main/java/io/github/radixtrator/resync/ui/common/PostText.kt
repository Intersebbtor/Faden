package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.ViewOptions
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.flairSearch
import io.github.radixtrator.resync.ui.navigation.LocalUserTagColors
import io.github.radixtrator.resync.ui.navigation.LocalUserTags
import io.github.radixtrator.resync.ui.navigation.rememberVisited
import io.github.radixtrator.resync.ui.theme.FadenColors

// --- Shared pieces ---

// Feed text, sized from Sync's screenshots: cards read a size up from rows, and lines sit 1.2em apart.
internal val CardTitle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
internal val CardMeta: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 1.2.em)
internal val RowTitle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 1.2.em, fontWeight = FontWeight.SemiBold)
internal val RowMeta: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 1.2.em)

/** Customize view's title size ([ViewOptions.titleSize]): the size times [scale]; em line heights follow on their own. */
internal fun TextStyle.scaledBy(scale: Float): TextStyle =
    if (scale == 1f) this else copy(fontSize = fontSize * scale, lineHeight = if (lineHeight.isSp) lineHeight * scale else lineHeight)

/** The title, dimmed once read (in feeds), with Sync's pin and lock marks inline. */
@Composable
fun PostTitle(
    post: Post,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    dimWhenRead: Boolean = true,
) {
    val read = dimWhenRead && rememberVisited(post.id)
    val accent = MaterialTheme.colorScheme.primary
    // Remembered, with the marks map below, so Text can skip a row whose title hasn't changed.
    val text = remember(post.title, post.stickied, post.locked) { buildAnnotatedString {
        append(post.title)
        if (post.stickied) {
            append(" ")
            appendInlineContent("pin", t("[pinned]"))
        }
        if (post.locked) {
            append(" ")
            appendInlineContent("lock", t("[locked]"))
        }
    } }
    val marks = remember(accent) { mapOf(
        "pin" to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(AppIcons.Pin, t("Pinned"), tint = accent)
        },
        "lock" to InlineTextContent(Placeholder(0.9.em, 0.9.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Symbols.LockFilled, t("Locked"), tint = accent)
        },
    ) }
    Text(
        text,
        style = style,
        color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = marks,
        modifier = modifier,
    )
}

/**
 * "worldnews • author 🛡 [flair] • 4h* (last edited 1h) • thehill", with the sub in the accent color and
 * Sync's shield after moderators and admins.
 */
@Composable
fun PostMeta(
    post: Post,
    showSubreddit: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    style: TextStyle = CardMeta,
    suffix: String? = null,
    showAuthor: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    val flairBg = MaterialTheme.colorScheme.surfaceVariant
    val tag = LocalUserTags.current[post.author.lowercase()]
    val tagStyle = userTagStyle(post.author)
    val me = LocalAccount.current
    // The age ("4h") is part of the text, so it is rebuilt when the shared clock moves on a minute.
    val minute = LocalMinuteClock.current.value
    val text = remember(post, showSubreddit, showAuthor, suffix, me, tag, tagStyle, accent, flairBg, minute) { buildAnnotatedString {
        if (showSubreddit) {
            withStyle(SpanStyle(color = accent)) { append(post.subreddit) }
        }
        if (showAuthor) {
            if (showSubreddit) append(" • ")
            append(post.author)
            // Sync marks your own posts with a person after your name.
            if (post.author.equals(me, ignoreCase = true)) {
                append(" ")
                appendInlineContent("you", t("[you]"))
            }
            tag?.let {
                append(" ")
                withStyle(tagStyle) { append(" $it ") }
            }
            post.distinguished?.takeIf { it in DISTINGUISHED }?.let {
                append(" ")
                appendInlineContent(it, "[$it]")
            }
            post.authorFlair?.let {
                append(" ")
                withStyle(SpanStyle(background = flairBg)) { append(" $it ") }
            }
        }
        if (showSubreddit || showAuthor) append(" • ")
        append(Format.timeAgo(post.createdUtc))
        post.editedUtc?.let { append(t("* (last edited {0})", Format.timeAgo(it))) }
        shortDomain(post)?.let { append(" • $it") }
        post.crosspostOf?.let { append(" • " + t("crossposted from r/{0}", it.subreddit)) }
        if (post.over18) withStyle(SpanStyle(color = FadenColors.Nsfw, fontWeight = FontWeight.Bold)) { append(" • " + t("NSFW")) }
        if (post.spoiler) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" • " + t("Spoiler")) }
        suffix?.let { append(" • $it") }
    } }
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = distinguishedMarks(),
        modifier = modifier,
    )
}

private val DISTINGUISHED = setOf("moderator", "admin")

/** Your tag for a user (Sync's "Tag user"): a tinted label after their name. */
@Composable
fun userTagStyle(user: String? = null): SpanStyle {
    val custom = user?.let { LocalUserTagColors.current[it.lowercase()] }
    return if (custom == null) {
        SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, color = MaterialTheme.colorScheme.onTertiaryContainer)
    } else {
        // Dark text on light tints, light text on dark ones.
        SpanStyle(background = Color(custom), color = if (Color(custom).luminance() > 0.5f) Color.Black else Color.White)
    }
}

/** Inline content for [DISTINGUISHED] ids: Sync's shield, in the accent color (red for admins). */
@Composable
fun distinguishedMarks(): Map<String, InlineTextContent> {
    val accent = MaterialTheme.colorScheme.primary
    val you = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(accent, you) { mapOf(
        "moderator" to InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(AppIcons.Shield, t("Moderator"), tint = accent)
        },
        "admin" to InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(AppIcons.Shield, t("Admin"), tint = FadenColors.Nsfw)
        },
        "you" to InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Symbols.Person, t("You"), tint = you)
        },
    ) }
}

/** Sync drops ".com" ("thehill", "reddit") and keeps other hosts ("i.redd.it"); self posts show none. */
internal fun shortDomain(post: Post): String? = when {
    post.isSelf || post.domain.isEmpty() || post.domain.startsWith("self.") -> null
    else -> post.domain.removeSuffix(".com")
}

/** Post options › "Show post author", for feed items' [PostMeta]; off leaves the name and its marks out. */
internal val feedAuthors: Boolean
    @Composable get() = LocalFeedDisplay.current.showPostAuthor

/** The post's flair, unless Post options › Flair hides flair in feeds. */
@Composable
internal fun Post.shownFlair(): String? = flair?.takeIf { LocalFeedDisplay.current.showPostFlair }

/** A rounded flair chip, tinted with the subreddit's flair color when it has one. Tapping it lists the community's posts with that flair. */
@Composable
fun FlairChip(post: Post, modifier: Modifier = Modifier) {
    val flair = post.flair ?: return
    FlairChip(flair, post.flairColor, modifier.flairLink(post.subreddit, flair))
}

/** Makes a post's flair open [subreddit]'s posts with [flair]. */
@Composable
internal fun Modifier.flairLink(subreddit: String, flair: String): Modifier {
    val nav = LocalNavigator.current
    return clip(MaterialTheme.shapes.extraSmall)
        .clickable(onClickLabel = t("Show posts with this flair")) { nav.push(flairSearch(subreddit, flair)) }
}

/** A flair chip for [text], tinted with [color] (ARGB) when the community set one. */
@Composable
fun FlairChip(text: String, color: Int?, modifier: Modifier = Modifier) {
    // Sync mutes flair colors toward the card so bright subreddit colors don't shout.
    val bg = color?.let { Color(it).copy(alpha = 0.45f) } ?: MaterialTheme.colorScheme.surfaceVariant
    Text(
        text,
        style = CardMeta,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun PostStats(post: Post, modifier: Modifier = Modifier, style: TextStyle = CardMeta) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val pointsColor = when (post.likes) {
        true -> FadenColors.Upvote
        false -> FadenColors.Downvote
        null -> muted
    }
    val short = LocalFeedDisplay.current.shortScores
    Column(modifier) {
        Text(Format.points(post.score, short), style = style, color = pointsColor)
        Text(Format.comments(post.numComments, short), style = style, color = muted)
    }
}
