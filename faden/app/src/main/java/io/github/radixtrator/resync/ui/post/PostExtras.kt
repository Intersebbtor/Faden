package io.github.radixtrator.resync.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.data.CrosspostSource
import io.github.radixtrator.resync.data.Poll
import io.github.radixtrator.resync.data.PollOption
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.i18n.tn
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.LocalMinuteClock
import io.github.radixtrator.resync.ui.common.Symbols
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.PostRoute
import kotlin.math.roundToInt

/** "Crossposted from r/pics by u/someone": opens the original post and its comments. */
@Composable
internal fun CrosspostRow(source: CrosspostSource, modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = t("Open the original post")) { nav.push(PostRoute(source.id)) }
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                t("Crossposted from r/{0}", source.subreddit),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                t("by u/{0} · see the original comments", source.author),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Symbols.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A poll's options, with a bar for each option's share once Reddit gives the counts (after you vote or once it
 * closes). Reddit's API has no way to vote, so an open poll you haven't voted in links to reddit.com.
 */
@Composable
internal fun PollCard(post: Post, poll: Poll, modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    val nowMillis = LocalMinuteClock.current.value * 60_000
    val open = poll.isOpen(nowMillis)
    val counted = poll.options.sumOf { it.votes ?: 0 }
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Bars, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(t("Poll"), style = MaterialTheme.typography.titleSmall)
        }
        poll.options.forEach { option ->
            PollOptionRow(option, counted, selected = option.id.isNotEmpty() && option.id == poll.selected)
        }
        val total = tn(poll.totalVotes, "{0} vote", "{0} votes", Format.count(poll.totalVotes, short = false))
        val ends = poll.endsAtMillis
        val status = when {
            !open -> t("Voting closed")
            ends != null -> t("Voting ends in {0}", Format.timeAgo(nowMillis / 1000, nowSeconds = ends / 1000))
            else -> null
        }
        Text(
            listOfNotNull(total, status).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (open && poll.selected == null) {
            TextButton(onClick = { nav.openExternal("https://www.reddit.com" + post.permalink) }) { Text(t("Vote on reddit.com")) }
        }
    }
}

@Composable
private fun PollOptionRow(option: PollOption, counted: Int, selected: Boolean) {
    val share = option.votes?.let { if (counted > 0) it.toFloat() / counted else 0f }
    val shape = MaterialTheme.shapes.small
    val percent = share?.let { "${(it * 100).roundToInt()}%" }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(shape)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(option.text, percent, if (selected) t("Your vote") else null).joinToString(", ")
            },
    ) {
        if (share != null) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(share.coerceIn(0f, 1f))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected) Icon(Symbols.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(option.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            percent?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
        }
    }
}
