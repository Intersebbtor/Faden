@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.radixtrator.resync.ui.post

import io.github.radixtrator.resync.ui.common.Symbols
import io.github.radixtrator.resync.ui.common.AvatarKind
import io.github.radixtrator.resync.ui.common.Avatar
import io.github.radixtrator.resync.i18n.I18n
import io.github.radixtrator.resync.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.MoreComments
import io.github.radixtrator.resync.ui.navigation.ImageRoute
import io.github.radixtrator.resync.data.SwipeAction
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.CommentGifs
import io.github.radixtrator.resync.ui.common.GifBody
import io.github.radixtrator.resync.ui.common.LinkCards
import io.github.radixtrator.resync.ui.common.LocalFeedDisplay
import io.github.radixtrator.resync.ui.common.LocalMinuteClock
import io.github.radixtrator.resync.ui.common.cachedGifBody
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.StateToggle
import io.github.radixtrator.resync.ui.common.SwipeActions
import io.github.radixtrator.resync.ui.common.distinguishedMarks
import io.github.radixtrator.resync.ui.common.rememberRedditText
import io.github.radixtrator.resync.ui.common.userTagStyle
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalUserTags
import io.github.radixtrator.resync.ui.navigation.UserRoute
import io.github.radixtrator.resync.ui.theme.FadenColors
import kotlin.math.absoluteValue

// --- Comments ---

/** Indent per depth; rails sit at each multiple of it. Deeper replies stop indenting at [MAX_DEPTH]. */
private val INDENT = 16.dp
private const val MAX_DEPTH = 10

/**
 * Selftext and comment bodies (measured from sync-reference 23 and 55). The font's own line spacing, like
 * Sync, so lines holding a larger link grow.
 */
internal val CommentBody: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = TextUnit.Unspecified)

private fun railColor(level: Int) = FadenColors.Depth[(level - 1) % FadenColors.Depth.size]

/** Draws a rail for every ancestor level 1..[depth], so threads read as continuous colored lines. */
private fun Modifier.rails(depth: Int) = drawBehind {
    val width = 2.dp.toPx()
    for (level in 1..depth) {
        val x = (INDENT * level).toPx()
        drawRect(railColor(level), topLeft = Offset(x, 0f), size = Size(width, size.height))
    }
}

private fun contentStart(depth: Int): Dp = INDENT * (depth + 1)

@Composable
internal fun CommentRow(
    comment: Comment,
    avatarUrl: String?,
    collapsed: Boolean,
    hiddenCount: Int,
    isNew: Boolean,
    separated: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    onVote: (Int) -> Unit,
    onToggleSave: () -> Unit,
    onReply: () -> Unit,
    onParent: () -> Unit,
    onMore: () -> Unit,
    onShare: () -> Unit,
) {
    val depth = comment.depth.coerceAtMost(MAX_DEPTH)
    val nav = LocalNavigator.current
    val display = LocalFeedDisplay.current
    Column {
        if (separated) Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.background))
        SwipeActions(
            stages = display.commentSwipeStages,
            enabled = display.commentSwipe,
            onAction = { action ->
                when (action) {
                    SwipeAction.UPVOTE -> onVote(if (comment.likes == true) 0 else 1)
                    SwipeAction.DOWNVOTE -> onVote(if (comment.likes == false) 0 else -1)
                    SwipeAction.SAVE -> onToggleSave()
                    SwipeAction.PROFILE -> if (comment.author != "[deleted]") nav.push(UserRoute(comment.author))
                    SwipeAction.SHARE -> onShare()
                    SwipeAction.REPLY -> onReply()
                    SwipeAction.HIDE, SwipeAction.MARK_READ -> Unit
                }
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Opaque, so the swipe panel only shows where the row has moved away.
                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow)
                    // Comment options › "Tap to collapse" swaps the two: by default hold collapses, tap opens the bar.
                    .combinedClickable(
                        onClick = if (display.tapToCollapse) onToggle else onSelect,
                        onLongClick = if (display.tapToCollapse) onSelect else onToggle,
                    )
                    .rails(depth)
                    .padding(start = contentStart(depth), end = 16.dp, top = 12.dp, bottom = 12.dp),
            ) {
                CommentHeader(comment, avatarUrl, collapsed, hiddenCount, isNew)
                // Collapsing a reply chain keeps the comment itself readable (Sync's default); a lone
                // comment folds to its header.
                if (!collapsed || hiddenCount > 0) {
                    val split = remember(comment.bodyHtml, comment.gifs, display.commentGifs) {
                        if (display.commentGifs) cachedGifBody(comment.bodyHtml, comment.gifs) else GifBody(comment.bodyHtml, emptyList())
                    }
                    val body = rememberRedditText(split.html, footnotes = display.linkCards)
                    if (body.text.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        RedditBodyText(body.text, CommentBody)
                    }
                    CommentGifs(
                        split.gifs,
                        onOpen = { i -> nav.push(ImageRoute(split.gifs.map { it.url }, start = i)) },
                        Modifier.padding(top = 8.dp),
                    )
                    LinkCards(body.links, Modifier.padding(top = 10.dp))
                }
            }
        }
        if (selected) {
            CommentActionBar(
                comment = comment,
                depth = depth,
                onVote = onVote,
                onToggleSave = onToggleSave,
                onReply = onReply,
                onParent = onParent,
                onMore = onMore,
            )
        }
    }
}

/**
 * "avatar username [marks] [flair] 859 points • 4h", OP's name in a rounded tinted pill, "New" when posted since
 * the last visit, "+N" when folded.
 */
@Composable
private fun CommentHeader(comment: Comment, avatarUrl: String?, collapsed: Boolean, hiddenCount: Int, isNew: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val nameColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val scoreColor = when (comment.likes) {
        true -> FadenColors.Upvote
        false -> FadenColors.Downvote
        null -> muted
    }
    val opPill = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    val flairBackground = MaterialTheme.colorScheme.surfaceVariant
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tag = LocalUserTags.current[comment.author.lowercase()]
    val tagStyle = userTagStyle(comment.author)
    val language = I18n.language
    // The age ("4h") is part of the text, so it is rebuilt when the shared clock moves on a minute.
    val minute = LocalMinuteClock.current.value
    val text = remember(comment, nameColor, muted, scoreColor, flairBackground, tag, tagStyle, language, minute) { buildAnnotatedString {
        withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.SemiBold)) { append(comment.author) }
        tag?.let {
            append(" ")
            withStyle(tagStyle) { append(" $it ") }
        }
        // Sync's marks: OP (a person speaking), the moderator shield, the pin. A thin space keeps the OP
        // pill off the first mark.
        if (comment.isSubmitter) {
            append("\u2009 ")
            appendInlineContent("op", "[OP]")
        }
        comment.distinguished?.takeIf { it == "moderator" || it == "admin" }?.let {
            append(" ")
            appendInlineContent(it, "[$it]")
        }
        if (comment.stickied) {
            append(" ")
            appendInlineContent("pin", t("[pinned]"))
        }
        comment.flair?.let {
            append(" ")
            withStyle(SpanStyle(background = flairBackground, color = muted)) { append(" $it ") }
        }
        append(" ")
        withStyle(SpanStyle(color = scoreColor)) {
            append(if (comment.scoreHidden) t("[score hidden]") else Format.points(comment.score, short = true))
        }
        withStyle(SpanStyle(color = muted)) {
            append(" • ${Format.timeAgo(comment.createdUtc)}")
            if (comment.edited) append("*")
        }
    } }
    // A span background can't round its corners, so the OP pill is drawn under the name.
    val pill = remember(comment.isSubmitter, comment.author, opPill) {
        Modifier.drawBehind {
            val result = layout ?: return@drawBehind
            if (!comment.isSubmitter || comment.author.isEmpty()) return@drawBehind
            val first = result.getBoundingBox(0)
            val last = result.getBoundingBox(comment.author.length - 1)
            val pad = 4.dp.toPx()
            drawRoundRect(
                opPill,
                topLeft = Offset(first.left - pad, first.top),
                size = Size(last.right - first.left + 2 * pad, first.height),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(comment.author, avatarUrl, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            inlineContent = commentMarks(),
            onTextLayout = { layout = it },
            modifier = Modifier.weight(1f).then(pill),
        )
        // Beside the header line rather than in it, where long flair could cut it off.
        if (isNew) {
            Text(
                t("New"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (collapsed && hiddenCount > 0) {
            Text(
                "+$hiddenCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Inline marks for comment headers: OP, the moderator or admin shield, the pin. */
@Composable
private fun commentMarks(): Map<String, InlineTextContent> {
    val accent = MaterialTheme.colorScheme.primary
    val base = distinguishedMarks()
    val language = I18n.language
    return remember(base, accent, language) {
        base + mapOf(
            "op" to InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) { Icon(AppIcons.Speaker, "OP", tint = accent) },
            "pin" to InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) { Icon(AppIcons.Pin, t("Pinned"), tint = accent) },
        )
    }
}

/** Sync's long-press bar: upvote, downvote, save, profile, reply, go to parent, more. */
@Composable
private fun CommentActionBar(
    comment: Comment,
    depth: Int,
    onVote: (Int) -> Unit,
    onToggleSave: () -> Unit,
    onReply: () -> Unit,
    onParent: () -> Unit,
    onMore: () -> Unit,
) {
    val nav = LocalNavigator.current
    val normal = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .rails(depth),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarToggle(AppIcons.ArrowUp, t("Upvote"), comment.likes == true, FadenColors.Upvote, normal) { onVote(if (it) 1 else 0) }
        BarToggle(AppIcons.ArrowDown, t("Downvote"), comment.likes == false, FadenColors.Downvote, normal) { onVote(if (it) -1 else 0) }
        BarToggle(
            if (comment.saved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
            if (comment.saved) t("Unsave") else t("Save"),
            comment.saved,
            FadenColors.Saved,
            normal,
        ) { onToggleSave() }
        BarButton(Symbols.Person, t("Profile"), normal) { if (comment.author != "[deleted]") nav.push(UserRoute(comment.author)) }
        BarButton(AppIcons.Reply, t("Reply"), normal, onReply)
        BarButton(Symbols.KeyboardArrowUp, t("Go to parent"), normal, onParent)
        BarButton(Symbols.MoreVert, t("Comment options"), normal, onMore)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.weight(1f).fillMaxSize(), colors = IconButtonDefaults.iconButtonColors(contentColor = tint)) {
        Icon(icon, description)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarToggle(
    icon: ImageVector,
    description: String,
    checked: Boolean,
    checkedColor: Color,
    color: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    StateToggle(checked, onCheckedChange, icon, description, checkedColor, color, Modifier.weight(1f).fillMaxSize())
}

@Composable
internal fun MoreRow(more: MoreComments, separated: Boolean, loading: Boolean, onClick: () -> Unit) {
    val depth = more.depth.coerceAtMost(MAX_DEPTH)
    Column {
        if (separated) Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.background))
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick)
                .rails(depth)
                .padding(start = contentStart(depth), end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (more.children.isEmpty()) t("Continue this thread") else t("View more ({0})", more.count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (loading) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/** A colored initial: comment listings don't carry avatars, and fetching one per author is too costly. */
@Composable
private fun UserAvatar(name: String, url: String?, size: Dp) {
    val color = FadenColors.Depth[name.hashCode().absoluteValue % FadenColors.Depth.size]
    Avatar(
        url, name, size,
        kind = AvatarKind.User,
        letterStyle = MaterialTheme.typography.labelSmall,
        containerColor = color.copy(alpha = 0.35f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        replaceLetter = true,
    )
}
