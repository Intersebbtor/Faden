package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.ViewOptions
import io.github.radixtrator.resync.i18n.t

// --- List, compact and smaller cards ---

/**
 * Sync's List ([flushThumbnail]: square thumbnail against the left edge) and Compact (inset rounded
 * thumbnail) rows; Smaller cards wraps the compact row in a card. ⌄ expands the media or text inline.
 * [options]: thumbnails (left or right), the type indicator, a one-line title and the title and description sizes, as
 * Sync's List/Compact/Smaller cards screens offer.
 */
@Composable
internal fun RowPost(post: Post, showSubreddit: Boolean, actions: PostActions, options: ViewOptions, flushThumbnail: Boolean, inCard: Boolean) {
    var expanded by rememberSaveable(post.fullname) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val short = LocalFeedDisplay.current.shortScores
    Column(
        Modifier
            // Opaque so a swipe's action panel shows only where the row has slid away.
            .background(if (inCard) Color.Transparent else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = actions.onOpen, onLongClick = actions.onMore),
    ) {
        val right = options.thumbnailRight
        val thumbnail = @Composable {
            if (flushThumbnail) {
                val inset = if (right) Modifier.padding(end = 12.dp, top = 10.dp) else Modifier.padding(start = 12.dp, top = 10.dp)
                Thumbnail(post, 72.dp, MaterialTheme.shapes.medium, onClick = actions.onMedia, modifier = inset)
            } else {
                val inset = if (right) Modifier.padding(end = 8.dp, top = 8.dp) else Modifier.padding(start = 8.dp, top = 8.dp)
                Thumbnail(post, 64.dp, MaterialTheme.shapes.medium, onClick = actions.onMedia, modifier = inset)
            }
        }
        val text = @Composable {
            Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 10.dp, end = 4.dp)) {
                PostTitle(post, RowTitle.scaledBy(options.titleSize.scale), maxLines = if (options.singleLineTitle) 1 else 4)
                val description = RowMeta.scaledBy(options.descriptionSize.scale)
                PostMeta(post, showSubreddit, maxLines = 1, style = description, showAuthor = feedAuthors)
                if (flushThumbnail) {
                    // List: the stats as small chips; Compact and Smaller cards keep the dense text line.
                    Row(Modifier.padding(top = 4.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScoreChip(post, short)
                        CommentsChip(post, short)
                    }
                } else {
                    Text(
                        "${Format.points(post.score, short)} • ${Format.comments(post.numComments, short)}",
                        style = description,
                        color = muted,
                        maxLines = 1,
                    )
                }
                post.shownFlair()?.let {
                    Text(
                        it,
                        style = if (flushThumbnail) RowMeta else RowTitle,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.flairLink(post.subreddit, it),
                    )
                }
            }
        }
        val expand = @Composable {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(40.dp)) {
                    Icon(if (expanded) Symbols.KeyboardArrowUp else Symbols.KeyboardArrowDown, if (expanded) t("Collapse") else t("Expand"), tint = muted)
                }
            }
        val type = @Composable { Icon(typeIcon(post), null, tint = muted, modifier = Modifier.padding(8.dp).size(20.dp)) }
        val side = @Composable {
            if (flushThumbnail) {
                Row(Modifier.fillMaxHeight().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    expand()
                    if (options.typeIndicators) type()
                }
            } else {
                Column(Modifier.fillMaxHeight().padding(end = 4.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                    // Without the type icon, ⌄ still sits at the bottom.
                    if (options.typeIndicators) type() else Spacer(Modifier.weight(1f))
                    expand()
                }
            }
        }
        val none = @Composable {}
        RowPostLayout(
            leading = if (options.thumbnails && !right) thumbnail else none,
            text = text,
            side = side,
            // "Align thumbnail right": past ⌄ and the type icon, against the row's end.
            trailing = if (options.thumbnails && right) thumbnail else none,
        )
        if (expanded) {
            if (post.thumbnail() != null && !post.isSelf) {
                CardPreview(post, onClick = actions.onMedia, modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).clip(MaterialTheme.shapes.small))
            } else if (post.selfText.isNotBlank()) {
                RedditBodyText(
                    post.selfTextHtml?.let { rememberRedditHtml(it) } ?: AnnotatedString(post.selfText),
                    MaterialTheme.typography.bodyMedium,
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
        if (!inCard) {
            // Starts at the text, past a left thumbnail: List's is 12 + 72 wide, Compact's 8 + 64, plus the text's 12.
            val start = if (options.thumbnails && !right) (if (flushThumbnail) 96.dp else 84.dp) else 12.dp
            HorizontalDivider(Modifier.padding(start = start), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * [RowPost]'s row: [text] takes the width the others leave, and [side] (⌄ and the type icon) stretches to the
 * row's height so ⌄ sits at the bottom. That's what IntrinsicSize.Min did, but it measured the title and meta text
 * twice per row; here only [side]'s small intrinsics are asked for.
 */
@Composable
private fun RowPostLayout(
    leading: @Composable () -> Unit,
    text: @Composable () -> Unit,
    side: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(contents = listOf(leading, text, side, trailing), modifier = Modifier.fillMaxWidth()) { (lead, main, aside, trail), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val leadP = lead.firstOrNull()?.measure(loose)
        val trailP = trail.firstOrNull()?.measure(loose)
        val sideM = aside.first()
        val sideWidth = sideM.maxIntrinsicWidth(Constraints.Infinity)
        val textWidth = (width - (leadP?.width ?: 0) - (trailP?.width ?: 0) - sideWidth).coerceAtLeast(0)
        val textP = main.first().measure(Constraints.fixedWidth(textWidth))
        val height = maxOf(textP.height, leadP?.height ?: 0, trailP?.height ?: 0, sideM.minIntrinsicHeight(sideWidth))
        val sideP = sideM.measure(Constraints.fixed(sideWidth, height))
        layout(width, height) {
            var x = 0
            leadP?.let { it.place(x, 0); x += it.width }
            textP.place(x, 0); x += textP.width
            sideP.place(x, 0); x += sideP.width
            trailP?.place(x, 0)
        }
    }
}
