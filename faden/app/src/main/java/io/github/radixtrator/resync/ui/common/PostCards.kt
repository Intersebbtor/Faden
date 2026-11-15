package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.ViewOptions
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.ModRoute
import io.github.radixtrator.resync.ui.theme.FadenColors

@Composable
internal fun PostCard(shape: Shape, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

// --- Cards ---

/**
 * Sync's Cards. [options]: image above or below the title (`card_image_above`), full-height or cropped previews,
 * selftext previews, full width cards (square corners here; the list drops the gutter), the title, description and
 * selftext sizes, and the footer's Save / Hide / Share / Mod tools buttons.
 */
@Composable
internal fun CardPost(post: Post, showSubreddit: Boolean, actions: PostActions, options: ViewOptions) {
    val previewMaxHeight = if (options.cardFullHeight) null else CroppedPreviewHeight
    // Rounded, or square with "Full width cards" on.
    val cardShape = PostLayout.CARD.swipeShape
    // The preview sits inset in the card with rounded corners, unless the card runs full width (square, flush).
    val flush = cardShape == RectangleShape
    val previewShape = MaterialTheme.shapes.medium
    PostCard(cardShape) {
        Column(Modifier.combinedClickable(onClick = actions.onOpen, onLongClick = actions.onMore)) {
            if (options.imageAboveTitle) {
                CardPreview(
                    post,
                    onClick = actions.onMedia,
                    modifier = if (flush) Modifier else Modifier.padding(8.dp).clip(previewShape),
                    maxHeight = previewMaxHeight,
                )
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                PostTitle(post, CardTitle.scaledBy(options.titleSize.scale))
                Spacer(Modifier.height(4.dp))
                PostMeta(post, showSubreddit, maxLines = 3, style = CardMeta.scaledBy(options.descriptionSize.scale), showAuthor = feedAuthors)
                if (post.shownFlair() != null) {
                    Spacer(Modifier.height(8.dp))
                    FlairChip(post)
                }
                if (options.selftextPreviews) {
                    selfTextPreview(post)?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium.scaledBy(options.selftextSize.scale),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!options.imageAboveTitle) {
                CardPreview(
                    post,
                    onClick = actions.onMedia,
                    modifier = if (flush) Modifier.padding(top = 12.dp) else Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp).clip(previewShape),
                    maxHeight = previewMaxHeight,
                )
            }
            StatsFooter(post, actions, PostButtons(options.cardSaveButton, options.hideButton, options.shareButton, options.modButton))
        }
    }
}

/**
 * Sync's Small cards. [options]: the thumbnail top-right, or top-left (`small_cards_thumbnail_align_left`), the title
 * and description sizes, and the footer's Save / Hide / Share / Mod tools buttons, as on Cards.
 */
@Composable
internal fun SmallCardPost(post: Post, showSubreddit: Boolean, actions: PostActions, options: ViewOptions) {
    PostCard(PostLayout.SMALL_CARDS.swipeShape) {
        Column(Modifier.combinedClickable(onClick = actions.onOpen, onLongClick = actions.onMore)) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                val hasThumbnail = post.thumbnail() != null
                if (hasThumbnail && options.thumbnailLeft) {
                    Thumbnail(post, 64.dp, MaterialTheme.shapes.medium, onClick = actions.onMedia)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    PostTitle(post, CardTitle.scaledBy(options.titleSize.scale), maxLines = 5)
                    Spacer(Modifier.height(4.dp))
                    PostMeta(post, showSubreddit, maxLines = 3, style = RowMeta.scaledBy(options.descriptionSize.scale), showAuthor = feedAuthors)
                    if (post.shownFlair() != null) {
                        Spacer(Modifier.height(8.dp))
                        FlairChip(post)
                    }
                }
                if (hasThumbnail && !options.thumbnailLeft) {
                    Spacer(Modifier.width(12.dp))
                    Thumbnail(post, 64.dp, MaterialTheme.shapes.medium, onClick = actions.onMedia)
                }
            }
            StatsFooter(post, actions, PostButtons(options.cardSaveButton, options.hideButton, options.shareButton, options.modButton))
        }
    }
}

/** The score and comments chips on the left, then any of [buttons] and ⋮ on the right: Faden's card footer. */
@Composable
private fun StatsFooter(post: Post, actions: PostActions, buttons: PostButtons = PostButtons.None) {
    val short = LocalFeedDisplay.current.shortScores
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreChip(post, short)
            CommentsChip(post, short)
        }
        PostButtonIcons(post, actions, buttons, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = actions.onMore) { Icon(Symbols.MoreVert, t("Post options"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Which of Sync's optional post buttons (Customize view › Buttons) a button row shows. */
internal class PostButtons(val save: Boolean, val hide: Boolean, val share: Boolean, val mod: Boolean) {
    companion object {
        val None = PostButtons(save = false, hide = false, share = false, mod = false)
    }
}

/**
 * The switched-on [buttons] in Sync's settings order: Save, Hide (only where the list can hide), Share, and Mod tools
 * (only in communities you moderate). Emitted straight into the caller's row.
 */
@Composable
internal fun PostButtonIcons(post: Post, actions: PostActions, buttons: PostButtons, tint: Color) {
    val nav = LocalNavigator.current
    if (buttons.save) {
        StateToggle(
            post.saved,
            { actions.onToggleSave() },
            if (post.saved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
            if (post.saved) t("Unsave") else t("Save"),
            FadenColors.Saved,
            tint,
        )
    }
    if (buttons.hide) {
        actions.onHide?.let { hide -> IconButton(onClick = hide) { Icon(Symbols.Close, t("Hide"), tint = tint) } }
    }
    if (buttons.share) {
        val share: () -> Unit = actions.onShare ?: { nav.share("https://www.reddit.com${post.permalink}", post.title) }
        IconButton(onClick = share) { Icon(Symbols.Share, t("Share"), tint = tint) }
    }
    if (buttons.mod) {
        if (post.subreddit.lowercase() in LocalFeedDisplay.current.moderated) {
            val modTools: () -> Unit = actions.onModTools ?: { nav.push(ModRoute(post.subreddit)) }
            IconButton(onClick = modTools) { Icon(AppIcons.Shield, t("Mod tools"), tint = tint) }
        }
    }
}
