package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.ViewOptions
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.ui.theme.FadenColors

// --- Slides ---

/**
 * Sync's Slides: a full-width post with the sub on top, big media, and vote/comment pills. [options]: image and
 * selftext previews and the image's full height / full width / place before the title, the subreddit icon, the
 * border around selftext, the thick divider (off: a hairline here, and no gap in the list), the title, description
 * and selftext sizes, and the button row (Save / Hide / Share / Mod tools, voting on the right, or no buttons at all).
 */
@Composable
internal fun SlidePost(post: Post, actions: PostActions, options: ViewOptions) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // "Preview before title": the media opens the slide, flush with its top edge when it also runs full width.
    val previewFirst = options.imagePreviews && options.slideImageTop && post.hasCardPreview()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = actions.onOpen, onLongClick = actions.onMore)
            .padding(top = if (previewFirst && options.slideFullWidth) 0.dp else 16.dp),
    ) {
        if (previewFirst) SlidePreview(post, actions, options, top = 0.dp)
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = if (previewFirst) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (options.subredditIcons) {
                Avatar(null, post.subreddit, 32.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                val description = CardMeta.scaledBy(options.descriptionSize.scale)
                Text(
                    buildAnnotatedString {
                        append("r/")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(post.subreddit) }
                    },
                    style = description,
                    color = muted,
                )
                PostMeta(post, showSubreddit = false, maxLines = 1, style = description, showAuthor = feedAuthors)
            }
        }
        PostTitle(
            post,
            CardTitle.copy(fontSize = 15.sp).scaledBy(options.titleSize.scale),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        )
        if (post.shownFlair() != null) FlairChip(post, Modifier.padding(start = 16.dp, top = 8.dp))
        if (options.imagePreviews && !options.slideImageTop) SlidePreview(post, actions, options, top = 12.dp)
        val selftext = if (options.selftextPreviews) selfTextPreview(post) else null
        selftext?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium.scaledBy(options.selftextSize.scale),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .fillMaxWidth()
                    .then(
                        if (options.selftextBorder) {
                            Modifier
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.small)
                                .padding(12.dp)
                        } else Modifier,
                    ),
            )
        }
        if (!options.hideAllButtons) {
            val buttons = PostButtons(options.saveButton, options.hideButton, options.shareButton, options.modButton)
            val onSurface = MaterialTheme.colorScheme.onSurface
            // "Right aligned voting" mirrors the row: icons and ⋮ on the left, the comment and vote pills on the right.
            val voteRight = options.voteRight
            Row(
                Modifier.fillMaxWidth().padding(start = if (voteRight) 4.dp else 16.dp, end = if (voteRight) 16.dp else 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (voteRight) {
                    PostButtonIcons(post, actions, buttons, tint = onSurface)
                    IconButton(onClick = actions.onMore) { Icon(Symbols.MoreVert, t("Post options")) }
                    Spacer(Modifier.weight(1f))
                    CommentsPill(post.numComments, actions.onOpen)
                    Spacer(Modifier.width(8.dp))
                    VotePill(post.likes, post.score, actions.onVote)
                } else {
                    VotePill(post.likes, post.score, actions.onVote)
                    Spacer(Modifier.width(8.dp))
                    CommentsPill(post.numComments, actions.onOpen)
                    Spacer(Modifier.weight(1f))
                    PostButtonIcons(post, actions, buttons, tint = onSurface)
                    IconButton(onClick = actions.onMore) { Icon(Symbols.MoreVert, t("Post options")) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Without the thick divider the list leaves no gap between slides, so each ends on a hairline.
        if (!options.thickDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * A slide's big preview, [top] below what comes before it: inset with rounded corners, or edge to edge with "Preview
 * full width"; cropped to a band unless "Preview full height" is on.
 */
@Composable
private fun SlidePreview(post: Post, actions: PostActions, options: ViewOptions, top: Dp) {
    CardPreview(
        post,
        onClick = actions.onMedia,
        modifier = if (options.slideFullWidth) {
            Modifier.padding(top = top)
        } else {
            Modifier.padding(start = 16.dp, end = 16.dp, top = top).clip(MaterialTheme.shapes.medium)
        },
        height = if (options.slideFullHeight) null else CroppedPreviewHeight,
    )
}

/** The comment count pill beside Slides' vote pill; opens the comments. */
@Composable
private fun CommentsPill(count: Int, onOpen: () -> Unit) {
    FilledTonalButton(
        onClick = onOpen,
        modifier = Modifier.height(40.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(AppIcons.Comment, t("Comments"), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(Format.count(count, LocalFeedDisplay.current.shortScores), style = MaterialTheme.typography.labelLarge)
    }
}

/** ▲ score ▼ in one rounded pill, tinted orange when upvoted. */
@Composable
private fun VotePill(likes: Boolean?, score: Int, onVote: (Int) -> Unit) {
    val tint = when (likes) {
        true -> FadenColors.Upvote
        false -> FadenColors.Downvote
        null -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (likes == null) MaterialTheme.colorScheme.surfaceContainerHighest else tint.copy(alpha = 0.25f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StateToggle(likes == true, { onVote(if (it) 1 else 0) }, Symbols.KeyboardArrowUp, t("Upvote"), tint, tint, Modifier.size(40.dp))
        Text(Format.count(score, LocalFeedDisplay.current.shortScores), style = MaterialTheme.typography.labelLarge, color = tint)
        StateToggle(likes == false, { onVote(if (it) -1 else 0) }, Symbols.KeyboardArrowDown, t("Downvote"), tint, tint, Modifier.size(40.dp))
    }
}
