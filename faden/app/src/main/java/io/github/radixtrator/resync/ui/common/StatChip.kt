package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.ui.theme.FadenColors

/**
 * Faden's stat pill: a small rounded chip with an [icon] and a [value] ("10.6k", "154"), for scores and comment counts
 * in feeds and on the post page. It only shows a number; voting and opening stay on the row's own controls.
 * [accent], when set (an upvoted or downvoted score), tints the icon and text and washes the pill with it.
 * [description] is what TalkBack reads instead of the bare number ("10,600 points").
 */
@Composable
fun StatChip(icon: ImageVector, value: String, description: String, modifier: Modifier = Modifier, accent: Color? = null) {
    val content = accent ?: MaterialTheme.colorScheme.onSurfaceVariant
    val container = accent?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier
            .clip(CircleShape)
            .background(container)
            .padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1)
    }
}

/** A post's score as a [StatChip], tinted when the account has voted on it. */
@Composable
fun ScoreChip(post: Post, short: Boolean, modifier: Modifier = Modifier) {
    val accent = when (post.likes) {
        true -> FadenColors.Upvote
        false -> FadenColors.Downvote
        null -> null
    }
    StatChip(
        icon = if (post.likes == false) AppIcons.ArrowDown else AppIcons.ArrowUp,
        value = Format.count(post.score, short),
        description = Format.points(post.score, short),
        modifier = modifier,
        accent = accent,
    )
}

/** A post's comment count as a [StatChip]. */
@Composable
fun CommentsChip(post: Post, short: Boolean, modifier: Modifier = Modifier) {
    StatChip(AppIcons.Comment, Format.count(post.numComments, short), Format.comments(post.numComments, short), modifier)
}
