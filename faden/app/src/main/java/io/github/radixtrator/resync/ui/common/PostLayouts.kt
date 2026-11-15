package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostColumns
import io.github.radixtrator.resync.data.columnsFor
import io.github.radixtrator.resync.data.isCoverScreen
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.ViewOptions
import io.github.radixtrator.resync.i18n.t

// Sync's six feed layouts (sync-reference/SYNC_PARITY_LOG.md §3.3). Each takes the post plus
// [PostActions]; spacing between items belongs to the list, see [gutter] and [spacing].

/**
 * What a feed item can do with its post. [onHide] is null where the list can't drop posts (the Hide button then
 * stays out); [onShare] and [onModTools] null mean the usual share sheet and Moderator screen.
 */
class PostActions(
    val onOpen: () -> Unit,
    val onMedia: () -> Unit,
    val onMore: () -> Unit,
    val onVote: (Int) -> Unit,
    val onToggleSave: () -> Unit,
    val onHide: (() -> Unit)? = null,
    val onShare: (() -> Unit)? = null,
    val onModTools: (() -> Unit)? = null,
)

/**
 * Side gutter and column gap: card layouts float on the window, row layouts run edge to edge, and so do Cards with
 * "Full width cards" on ([fullWidthCards]).
 */
val PostLayout.gutter: Dp
    @Composable get() = when (this) {
        PostLayout.LIST, PostLayout.COMPACT, PostLayout.SLIDES -> 0.dp
        PostLayout.CARD -> if (fullWidthCards()) 0.dp else 16.dp
        PostLayout.SMALLER_CARDS, PostLayout.SMALL_CARDS -> 16.dp
    }

/**
 * Gap between consecutive items. Rows draw their own dividers instead, as do Slides with the thick divider off
 * ([ViewOptions.thickDivider]); full-width Cards sit closer, like Slides.
 */
val PostLayout.spacing: Dp
    @Composable get() = when (this) {
        PostLayout.LIST, PostLayout.COMPACT -> 0.dp
        PostLayout.SLIDES -> if (LocalFeedDisplay.current.viewOptionsFor(this).thickDivider) 8.dp else 0.dp
        PostLayout.CARD -> if (fullWidthCards()) 8.dp else 16.dp
        PostLayout.SMALLER_CARDS, PostLayout.SMALL_CARDS -> 16.dp
    }

/** The item's outline, so the swipe panel behind a card has the card's corners. */
val PostLayout.swipeShape: Shape
    @Composable get() = when (this) {
        PostLayout.CARD -> if (fullWidthCards()) RectangleShape else MaterialTheme.shapes.medium
        PostLayout.SMALL_CARDS -> MaterialTheme.shapes.medium
        PostLayout.SMALLER_CARDS -> MaterialTheme.shapes.small
        PostLayout.LIST, PostLayout.COMPACT, PostLayout.SLIDES -> RectangleShape
    }

/**
 * Whether this is Cards with Customize view's "Full width cards" ([ViewOptions.cardFullWidth]) taking effect: Sync
 * offers it for a single column only, so it applies while the feed's [columns] (from the window width, as feeds
 * count them) come to one.
 */
@Composable
private fun PostLayout.fullWidthCards(): Boolean {
    if (this != PostLayout.CARD) return false
    val options = LocalFeedDisplay.current.viewOptionsFor(this)
    return options.cardFullWidth && feedColumns(options) == 1
}

/**
 * How many columns a feed of this layout has in the current window: the Customize view choice for this kind of
 * screen (a foldable's cover or main screen, [ViewOptions.columnsFor]), else as many as the width fits.
 */
@Composable
fun PostLayout.feedColumns(options: ViewOptions): Int {
    val config = LocalConfiguration.current
    return columns(config.screenWidthDp, options.columnsFor(isCoverScreen(config.smallestScreenWidthDp)))
}

/**
 * Cards and small cards tile into columns on wide screens, as on Sync's foldable cover screen. A Customize view
 * [override] other than Auto fixes the count instead, whatever the width.
 */
fun PostLayout.columns(widthDp: Int, override: PostColumns = PostColumns.AUTO): Int = override.count ?: when (this) {
    PostLayout.CARD, PostLayout.SMALL_CARDS -> when {
        widthDp >= 840 -> 3
        widthDp >= 480 -> 2
        else -> 1
    }
    else -> 1
}

/** [post] drawn in [layout], honoring that layout's Customize view options ([ViewOptions]) from [LocalFeedDisplay]. */
@Composable
fun PostLayoutItem(layout: PostLayout, post: Post, showSubreddit: Boolean, actions: PostActions) {
    val options = LocalFeedDisplay.current.viewOptionsFor(layout)
    when (layout) {
        PostLayout.LIST -> RowPost(post, showSubreddit, actions, options, flushThumbnail = true, inCard = false)
        PostLayout.COMPACT -> RowPost(post, showSubreddit, actions, options, flushThumbnail = false, inCard = false)
        PostLayout.SMALLER_CARDS -> PostCard(MaterialTheme.shapes.small) {
            RowPost(post, showSubreddit, actions, options, flushThumbnail = false, inCard = true)
        }
        PostLayout.SMALL_CARDS -> SmallCardPost(post, showSubreddit, actions, options)
        PostLayout.CARD -> CardPost(post, showSubreddit, actions, options)
        PostLayout.SLIDES -> SlidePost(post, actions, options)
    }
}
