@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.radixtrator.resync.ui.post

import io.github.radixtrator.resync.ui.common.Symbols
import io.github.radixtrator.resync.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.CommentSort
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.RecentPost
import io.github.radixtrator.resync.data.Subreddit
import io.github.radixtrator.resync.ui.feed.AboutAction
import io.github.radixtrator.resync.ui.feed.CommunityActionSheet
import io.github.radixtrator.resync.ui.feed.SubredditInfoSheet
import io.github.radixtrator.resync.ui.feed.rememberWatchToggle
import io.github.radixtrator.resync.ui.feed.runAboutAction
import io.github.radixtrator.resync.ui.common.isImageLink
import io.github.radixtrator.resync.ui.navigation.Navigator
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.ImageRoute
import io.github.radixtrator.resync.data.CommentNode
import io.github.radixtrator.resync.data.plainText
import io.github.radixtrator.resync.data.VisibleNode
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.ConfirmDeleteDialog
import io.github.radixtrator.resync.ui.common.OptionRow
import io.github.radixtrator.resync.ui.common.QuickAction
import io.github.radixtrator.resync.ui.common.ReportSheet
import io.github.radixtrator.resync.ui.common.SheetHandle
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.TagUserDialog
import io.github.radixtrator.resync.ui.common.TopSheet
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalContainer
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// --- Sheets ---

/** Sync lists comment sorts in this order. */
private val SyncCommentSorts = listOf(CommentSort.TOP, CommentSort.BEST, CommentSort.NEW, CommentSort.OLD, CommentSort.CONTROVERSIAL, CommentSort.QA)

private fun commentSortIcon(sort: CommentSort): ImageVector = when (sort) {
    CommentSort.TOP -> AppIcons.Bars
    CommentSort.BEST -> Symbols.Star
    CommentSort.NEW -> AppIcons.Burst
    CommentSort.OLD -> AppIcons.Clock
    CommentSort.CONTROVERSIAL -> AppIcons.TrendingDown
    CommentSort.QA -> AppIcons.Comment
}

@Composable
internal fun CommentSortSheet(current: CommentSort, onDismiss: () -> Unit, onPick: (CommentSort) -> Unit) {
    TopSheet(onDismiss = onDismiss) {
        SheetTitle(t("Comment sort"))
        SyncCommentSorts.forEach { sort ->
            OptionRow(sort.label, commentSortIcon(sort), selected = sort == current, onClick = {
                onDismiss()
                onPick(sort)
            })
        }
    }
}

/** Sync's "Comment options": share, open, select or copy text, report, tag, read aloud, plus edit/delete on your own. */
@Composable
internal fun CommentOptionsSheet(comment: Comment, post: Post, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val nav = LocalNavigator.current
    val clipboard = LocalClipboardManager.current
    val isOwn = LocalAccount.current?.equals(comment.author, ignoreCase = true) == true
    val permalink = "https://www.reddit.com${post.permalink}${comment.id}/"
    var copying by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    var labeling by remember { mutableStateOf(false) }
    val readAloud = LocalContainer.current.readAloud
    val speaking by readAloud.speaking.collectAsStateWithLifecycle()

    fun act(block: () -> Unit) {
        onDismiss()
        block()
    }

    // Both replace this sheet; closing either closes the lot.
    if (reporting) {
        ReportSheet(comment.fullname, post.subreddit, comment = true, onDismiss = onDismiss)
        return
    }
    if (tagging) {
        TagUserDialog(comment.author, onDismiss = onDismiss)
        return
    }
    if (labeling) {
        io.github.radixtrator.resync.ui.common.SavedLabelsSheet(comment.fullname, onDismiss)
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            if (copying) {
                SheetTitle(t("Copy"), topPadding = 0.dp)
                OptionRow(t("Copy text"), AppIcons.Text, onClick = { act { clipboard.setText(AnnotatedString(comment.body)) } })
                OptionRow(t("Copy permalink"), AppIcons.Link, onClick = { act { clipboard.setText(AnnotatedString(permalink)) } })
                OptionRow(t("Copy username"), Symbols.Person, onClick = { act { clipboard.setText(AnnotatedString(comment.author)) } })
            } else {
                SheetTitle(t("Comment options"), topPadding = 0.dp)
                OptionRow(t("Share"), Symbols.Share, onClick = { act { nav.share(permalink, post.title) } })
                OptionRow(t("Open in browser"), AppIcons.OpenExternal, onClick = { act { nav.openExternal(permalink) } })
                OptionRow(t("Select text"), AppIcons.Text, onClick = { selecting = true })
                OptionRow(t("Copy..."), AppIcons.Copy, onClick = { copying = true })
                if (!isOwn) OptionRow(t("Report"), AppIcons.Report, onClick = { reporting = true })
                OptionRow(t("Tag user"), AppIcons.TagUser, onClick = { tagging = true })
                if (comment.saved) OptionRow(t("Labels..."), AppIcons.Tag, onClick = { labeling = true })
                OptionRow(if (speaking) t("Stop reading") else t("Read aloud"), AppIcons.VolumeOn, onClick = {
                    act {
                        if (speaking) {
                            readAloud.stop()
                        } else {
                            readAloud.speak(plainText(comment.bodyHtml, comment.body))
                        }
                    }
                })
                if (isOwn) {
                    OptionRow(t("Edit"), Symbols.Edit, onClick = {
                        act { nav.push(ComposeRoute(ComposeMode.EDIT, target = comment.fullname, quote = post.title, initial = comment.body)) }
                    })
                    OptionRow(t("Delete"), Symbols.Delete, onClick = { confirmDelete = true })
                }
            }
        }
    }
    if (selecting) {
        AlertDialog(
            onDismissRequest = { selecting = false },
            title = { Text(t("Select text")) },
            text = { SelectionContainer { Text(comment.body, style = CommentBody) } },
            confirmButton = { TextButton(onClick = { selecting = false; onDismiss() }) { Text(t("Done")) } },
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(t("Delete comment?"), onConfirm = { confirmDelete = false; act(onDelete) }, onDismiss = { confirmDelete = false })
    }
}

/** Sync's in-thread search: a sheet that filters the loaded comments; picking one scrolls to it. */
@Composable
internal fun CommentSearchSheet(rows: List<VisibleNode>, onDismiss: () -> Unit, onPick: (Comment) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val q = query.trim()
    val matches = remember(rows, q) {
        if (q.length < 2) emptyList()
        else rows.mapNotNull { it.node as? Comment }.filter { it.body.contains(q, ignoreCase = true) || it.author.contains(q, ignoreCase = true) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { SheetHandle() },
    ) {
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Symbols.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.weight(1f)) {
                val hint = t("Search comments")
                if (query.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = hint },
                )
            }
        }
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(top = 8.dp)) {
            if (q.length >= 2 && matches.isEmpty()) {
                item { Text(t("No matching comments"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            items(matches, key = { it.id }) { comment ->
                Column(Modifier.fillMaxWidth().clickable { onPick(comment) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(comment.author, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(comment.body, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}


private val HREF = Regex("""href="([^"]+)"""")

/**
 * Sync's other Comment actions: Other discussions (the same link posted elsewhere), View images (the post's
 * and the comments' images in the viewer) and Translate (the post's text in Google Translate).
 */
internal fun commentActionExtras(post: Post, comments: List<CommentNode>, nav: Navigator): List<QuickAction> = buildList {
    if (!post.isSelf && post.url.startsWith("http")) {
        add(QuickAction(t("Other discussions"), AppIcons.Link) { nav.push(SearchRoute(query = "url:${post.url}")) })
    }
    val images = buildList {
        when (val m = post.media) {
            is PostMedia.Image -> add(m.url)
            is PostMedia.Gallery -> addAll(m.images.map { it.url })
            else -> {}
        }
        comments.forEach { node ->
            (node as? Comment)?.let { c -> HREF.findAll(c.bodyHtml).map { it.groupValues[1].replace("&amp;", "&") }.filter(::isImageLink).forEach(::add) }
        }
    }.distinct()
    if (images.isNotEmpty()) add(QuickAction(t("View images"), AppIcons.Image) { nav.push(ImageRoute(images, postId = post.id)) })
    add(QuickAction(t("Translate"), AppIcons.Text) {
        val text = java.net.URLEncoder.encode(post.title, "UTF-8")
        nav.openExternal("https://translate.google.com/?sl=auto&op=translate&text=$text")
    })
}
/**
 * Comment actions › About: the post's community's About sheet, as Explore shows it, with Join / Watch / Favorite
 * and the ⋮ items (wiki, flairs, rules, mods and the rest open their sheets in its place).
 */
@Composable
internal fun CommunityAbout(container: AppContainer, name: String, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val explore by container.explore.state.collectAsStateWithLifecycle()
    val watched by container.watch.watched.collectAsStateWithLifecycle()
    val toggleWatch = rememberWatchToggle(container, onMessage)
    var about by remember(name) { mutableStateOf<Subreddit?>(null) }
    var sheet by remember(name) { mutableStateOf<AboutAction?>(null) }
    LaunchedEffect(name) {
        runCatching { container.api.subredditAbout(name) }
            .onSuccess { about = it }
            .onFailure {
                if (it is CancellationException) throw it
                onMessage(t("Couldn't load r/{0}: {1}", name, it.message))
                onDismiss()
            }
    }
    val action = sheet
    if (action != null) {
        CommunityActionSheet(container, name, action, onDismiss = onDismiss, onNavigate = { route -> nav.push(route) })
        return
    }
    SubredditInfoSheet(
        about = about,
        watching = name.lowercase() in watched,
        favorite = explore.isFavorite(name),
        onDismiss = onDismiss,
        onToggleSubscribe = {
            val sub = about ?: return@SubredditInfoSheet
            about = sub.copy(isSubscribed = !sub.isSubscribed)
            scope.launch {
                runCatching { container.api.setSubscribed(sub.name, !sub.isSubscribed) }.onFailure { about = sub }
                container.refreshSubscriptions(force = true)
            }
        },
        onToggleWatch = { toggleWatch(name) },
        onToggleFavorite = { container.explore.toggleFavorite(name) },
        onAction = { picked ->
            when (picked) {
                AboutAction.SHARE, AboutAction.MESSAGE_MODS -> {
                    onDismiss()
                    nav.runAboutAction(picked, name)
                }
                else -> sheet = picked
            }
        },
    )
}
/** Sync's comment-page "Recents": the posts opened lately, newest first; picking one opens it. */
@Composable
internal fun RecentsSheet(posts: List<RecentPost>, onDismiss: () -> Unit, onPick: (RecentPost) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
        SheetTitle(t("Recents"), topPadding = 0.dp)
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 8.dp)) {
            if (posts.isEmpty()) {
                item { Text(t("No recently viewed posts"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            items(posts, key = { it.id }) { recent ->
                Column(Modifier.fillMaxWidth().clickable { onPick(recent) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(recent.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("r/${recent.subreddit}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
