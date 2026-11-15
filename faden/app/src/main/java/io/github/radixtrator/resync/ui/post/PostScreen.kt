@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.radixtrator.resync.ui.post

import io.github.radixtrator.resync.ui.common.Symbols
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.LoadingIndicator
import io.github.radixtrator.resync.ui.common.Avatar
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.i18n.tc
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import io.github.radixtrator.resync.ui.common.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.Comment
import io.github.radixtrator.resync.data.CommentTree
import io.github.radixtrator.resync.data.MediaPreviewSize
import io.github.radixtrator.resync.data.MoreComments
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.plainText
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.common.CardPreview
import io.github.radixtrator.resync.ui.common.FlairChip
import io.github.radixtrator.resync.ui.common.Format
import io.github.radixtrator.resync.ui.common.LinkCards
import io.github.radixtrator.resync.ui.common.LinkRow
import io.github.radixtrator.resync.ui.common.MessageEffect
import io.github.radixtrator.resync.ui.common.PostActionsSheet
import io.github.radixtrator.resync.ui.common.PostMeta
import io.github.radixtrator.resync.ui.common.PostStats
import io.github.radixtrator.resync.ui.common.PostTitle
import io.github.radixtrator.resync.ui.common.ScoreChip
import io.github.radixtrator.resync.ui.common.CommentsChip
import io.github.radixtrator.resync.ui.common.QuickAction
import io.github.radixtrator.resync.ui.common.QuickActionGrid
import io.github.radixtrator.resync.ui.common.RedditBodyText
import io.github.radixtrator.resync.ui.common.StateToggle
import io.github.radixtrator.resync.ui.common.SheetTitle
import io.github.radixtrator.resync.ui.common.SwipeBack
import io.github.radixtrator.resync.ui.common.TopSheet
import io.github.radixtrator.resync.ui.common.hasCardPreview
import io.github.radixtrator.resync.ui.common.rememberRedditText
import io.github.radixtrator.resync.ui.navigation.ComposeMode
import io.github.radixtrator.resync.ui.navigation.ComposeRoute
import io.github.radixtrator.resync.ui.navigation.FeedRoute
import io.github.radixtrator.resync.ui.navigation.LocalAccount
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LocalSettings
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.navigation.SettingsRoute
import io.github.radixtrator.resync.ui.theme.FadenAccents
import io.github.radixtrator.resync.ui.theme.FadenColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Sync's comment page (sync-reference/SYNC_PARITY_LOG.md §3.8): one surface for toolbar, post and
// comments; colored rails for every ancestor level; hold collapses a thread and tap opens an
// inline action bar (Comment options › "Tap to collapse" swaps them).

@Composable
fun PostScreen(route: PostRoute, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel { PostViewModel(container, route.postId, route.commentId, route.context) }
    val state by vm.state.collectAsStateWithLifecycle()
    val subredditIcon by vm.subredditIcon.collectAsStateWithLifecycle()
    val avatars by container.avatars.urls.collectAsStateWithLifecycle()
    val showAvatars = LocalSettings.current.profilePictures
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState(vm.scroll)
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val visible = remember(state.comments, state.collapsed) { CommentTree.visible(state.comments, state.collapsed) }
    var postSheet by remember { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    var actionsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // The comment whose action bar is open (Sync shows one at a time).
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var optionsFor by remember { mutableStateOf<Comment?>(null) }
    var recentsOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val reader = remember { CommentReader(context) }
    val readerState by reader.state.collectAsStateWithLifecycle()
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val settings = LocalSettings.current
    val account = LocalAccount.current
    // Comments posted since the last visit, other than your own, get a "New" label.
    val newSince = vm.newSince.takeIf { settings.markNewComments }
    val linkColor = MaterialTheme.colorScheme.primary
    SideEffect { vm.setLinkColor(linkColor) }
    // Sync's Tablet › "Tabbed comments": Post | Comments tabs instead of one scroll. The comments list then
    // has no post header row.
    val tabbed = settings.tabletMode && settings.tabbedComments
    // A single thread, or a remembered position down in the comments, opens on the Comments tab.
    var commentsTab by rememberSaveable { mutableStateOf(vm.focusCommentId != null || (vm.savedPosition?.index ?: 0) > 0) }
    val showComments = !tabbed || commentsTab
    val headerRows = if (tabbed) 0 else 1
    val currentHeaderRows by rememberUpdatedState(headerRows)
    // A jump into the comments list (index, offset) asked for while it wasn't on screen (the Post tab).
    var pendingScroll by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }

    MessageEffect(state.message, snackbar, vm::messageShown)
    LaunchedEffect(state.postDeleted) { if (state.postDeleted) nav.pop() }
    LaunchedEffect(route.postId) { container.visited.mark(route.postId) }
    LaunchedEffect(settings.readerSpeechRate) { reader.setSpeechRate(settings.readerSpeechRate) }

    // Sync's "Remember comment position": return to last visit's spot once the comments are in,
    // and note where the user is whenever this page leaves the screen. Positions are stored as in the
    // single-scroll list (post header at row 0), so they survive switching tabbed comments on or off.
    LaunchedEffect(state.comments.isNotEmpty()) {
        if (!vm.positionRestored && state.comments.isNotEmpty()) {
            vm.savedPosition?.let {
                val index = (it.index - 1 + headerRows).coerceAtLeast(0)
                if (showComments) {
                    listState.scrollToItem(index, it.offset)
                } else {
                    pendingScroll = index to it.offset
                }
            }
            vm.positionRestored = true
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val (index, offset) = pendingScroll ?: (listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
            // The top of the tabbed comments list is saved as the top of the page, so it reopens on the Post tab.
            if (index == 0 && offset == 0) vm.saveScroll(0, 0) else vm.saveScroll(index + 1 - currentHeaderRows, offset)
            // Sports mode and the comment reader only run while this page is on screen.
            vm.setSportsMode(false)
            reader.release()
        }
    }

    fun reply(target: String, quote: String) {
        if (vm.requireLogin()) nav.push(ComposeRoute(ComposeMode.REPLY, target = target, quote = quote))
    }

    // LazyColumn rows: post header (not when tabbed), optional single-thread banner, then the comments.
    val firstCommentRow = headerRows + if (vm.focusCommentId != null) 1 else 0
    val topLevelRows = remember(visible, firstCommentRow) {
        visible.indices.filter { visible[it].node.depth == 0 }.map { it + firstCommentRow }
    }
    fun scrollToComment(fullname: String) {
        val index = visible.indexOfFirst { (it.node as? Comment)?.fullname == fullname }
        val target = if (index < 0) 0 else index + firstCommentRow
        if (showComments) {
            scope.launch { listState.animateScrollToItem(target) }
        } else {
            commentsTab = true
            pendingScroll = target to 0
        }
    }

    SwipeBack(enabled = LocalSettings.current.swipeBack, onBack = nav::pop) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Symbols.ArrowBack, t("Back")) } },
                    title = {
                        state.post?.let { post ->
                            Row(
                                Modifier.clip(MaterialTheme.shapes.small).clickable { nav.push(FeedRoute(post.subreddit)) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(subredditIcon, post.subreddit, 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(post.subreddit, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (state.sportsMode) t("{0} · Live", state.sort.label) else state.sort.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) { Icon(Symbols.Search, t("Search comments")) }
                        IconButton(onClick = { sortOpen = true }) { Icon(AppIcons.Sort, t("Sort comments")) }
                        IconButton(onClick = { actionsOpen = true }) { Icon(Symbols.MoreVert, t("Comment actions")) }
                    },
                )
            },
            bottomBar = {
                if (state.sportsMode || readerState.active) {
                    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).navigationBarsPadding()) {
                        if (state.sportsMode) {
                            SportsBar(
                                seconds = settings.liveRefresh.seconds,
                                onRefresh = { vm.load(refresh = true) },
                                onStop = { vm.setSportsMode(false) },
                            )
                        }
                        if (readerState.active) {
                            ReaderBar(
                                state = readerState,
                                readReplies = settings.readerReplies,
                                speechRate = settings.readerSpeechRate,
                                onTogglePlay = reader::togglePlay,
                                onPrevious = reader::previous,
                                onNext = reader::next,
                                onStop = reader::stop,
                                onReadReplies = { on ->
                                    container.settings.update { it.copy(readerReplies = on) }
                                    state.post?.let { post -> scope.launch { reader.replaceItems(buildReaderQueue(post, state.comments, ::plainText, replies = on)) } }
                                },
                                onSpeechRate = { rate -> container.settings.update { it.copy(readerSpeechRate = rate) } },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                // With a bottom bar showing, the Scaffold already lifts the buttons above it.
                val inset = if (state.sportsMode || readerState.active) Modifier else Modifier.navigationBarsPadding()
                val showNavigation = settings.commentNavigation && showComments && topLevelRows.size > 1
                val previous = {
                    val target = topLevelRows.lastOrNull { it < listState.firstVisibleItemIndex } ?: 0
                    scope.launch { listState.animateScrollToItem(target) }
                    Unit
                }
                val next = {
                    topLevelRows.firstOrNull { it > listState.firstVisibleItemIndex }
                        ?.let { scope.launch { listState.animateScrollToItem(it) } }
                    Unit
                }
                val post = state.post
                if (showNavigation && post != null) {
                    // Previous and next top-level comment, with the reply button docked under them.
                    VerticalFloatingToolbar(
                        expanded = true,
                        modifier = inset,
                        floatingActionButton = {
                            FloatingToolbarDefaults.StandardFloatingActionButton(
                                onClick = { reply(post.fullname, post.title) },
                                containerColor = FadenAccents.fabContainer,
                                contentColor = FadenAccents.onFab,
                            ) {
                                Icon(AppIcons.Reply, t("Reply to post"))
                            }
                        },
                    ) {
                        IconButton(onClick = previous) { Icon(Symbols.KeyboardArrowUp, t("Previous comment")) }
                        IconButton(onClick = next) { Icon(Symbols.KeyboardArrowDown, t("Next comment")) }
                    }
                } else if (showNavigation) {
                    VerticalFloatingToolbar(expanded = true, modifier = inset) {
                        IconButton(onClick = previous) { Icon(Symbols.KeyboardArrowUp, t("Previous comment")) }
                        IconButton(onClick = next) { Icon(Symbols.KeyboardArrowDown, t("Next comment")) }
                    }
                } else if (post != null) {
                    FloatingActionButton(
                        onClick = { reply(post.fullname, post.title) },
                        modifier = inset,
                        containerColor = FadenAccents.fabContainer,
                        contentColor = FadenAccents.onFab,
                    ) { Icon(AppIcons.Reply, t("Reply to post")) }
                }
            },
        ) { padding ->
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState,
                        isRefreshing = state.refreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
                onRefresh = { vm.load(refresh = true) },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                // Not a list item: a lone keyed status row would stay pinned in view once content arrives.
                if (state.post == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.loading) {
                            LoadingIndicator()
                        } else if (state.error != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                                TextButton(onClick = { vm.load(refresh = false) }) { Text(t("Retry")) }
                            }
                        }
                    }
                    return@PullToRefreshBox
                }
                val post = state.post!!
                val commentList: @Composable () -> Unit = {
                    LaunchedEffect(pendingScroll) {
                        pendingScroll?.let { (index, offset) ->
                            listState.scrollToItem(index, offset)
                            pendingScroll = null
                        }
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                        if (!tabbed) {
                            item(key = "post") {
                                PostHeader(post = post, onVote = vm::votePost, onToggleSave = vm::toggleSavePost, onMore = { postSheet = true })
                            }
                        }
                        if (vm.focusCommentId != null) {
                            item(key = "focus") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(start = 16.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(t("Viewing a single thread"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { nav.replaceTop(PostRoute(post.id)) }) { Text(t("View all comments")) }
                                }
                            }
                        }
                        itemsIndexed(visible, key = { _, it -> (if (it.node is Comment) "c" else "m") + it.node.id }, contentType = { _, it -> it.node is Comment }) { index, row ->
                            // Each top-level thread is a rounded panel: it starts at a top-level row (with a gap of
                            // the window color above) and ends where the next top-level row or the list's end comes,
                            // so a collapsed top-level comment is a panel of one row, rounded at both ends.
                            val separated = row.node.depth == 0
                            val endsThread = visible.getOrNull(index + 1)?.let { it.node.depth == 0 } ?: true
                            when (val node = row.node) {
                                is Comment -> CommentRow(
                                    comment = node,
                                    avatarUrl = node.authorFullname?.let { avatars[it] }.takeIf { showAvatars },
                                    collapsed = node.id in state.collapsed,
                                    hiddenCount = row.hiddenCount,
                                    isNew = newSince != null && node.createdUtc > newSince && !node.author.equals(account, ignoreCase = true),
                                    separated = separated,
                                    endsThread = endsThread,
                                    selected = selected == node.id,
                                    onToggle = {
                                        selected = null
                                        vm.toggleCollapsed(node)
                                    },
                                    onSelect = { selected = if (selected == node.id) null else node.id },
                                    onVote = { vm.voteComment(node, it) },
                                    onToggleSave = { vm.toggleSaveComment(node) },
                                    onReply = { reply(node.fullname, node.body) },
                                    onParent = { scrollToComment(node.parentId) },
                                    onMore = { optionsFor = node },
                                    onShare = { nav.share("https://www.reddit.com${post.permalink}${node.id}/", post.title) },
                                )
                                is MoreComments -> MoreRow(
                                    more = node,
                                    separated = separated,
                                    endsThread = endsThread,
                                    loading = node.id in state.loadingMore,
                                    onClick = {
                                        if (node.children.isNotEmpty()) {
                                            vm.loadMore(node)
                                        } else {
                                            // "Continue this thread": a new page rooted at the parent comment.
                                            nav.push(PostRoute(post.id, node.parentId.removePrefix("t1_")))
                                        }
                                    },
                                )
                            }
                        }
                        item(key = "status") {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                when {
                                    state.loading -> LoadingIndicator()
                                    state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                                        TextButton(onClick = { vm.load(refresh = false) }) { Text(t("Retry")) }
                                    }
                                    state.comments.isEmpty() -> Text(t("No comments yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (!tabbed) {
                    commentList()
                    return@PullToRefreshBox
                }
                Column(Modifier.fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = if (commentsTab) 1 else 0, containerColor = surface) {
                        Tab(selected = !commentsTab, onClick = { commentsTab = false }, text = { Text(tc("noun", "Post")) })
                        Tab(
                            selected = commentsTab,
                            onClick = { commentsTab = true },
                            text = { Text(t("Comments ({0})", Format.count(post.numComments)), maxLines = 1) },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (commentsTab) {
                            commentList()
                        } else {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
                                PostHeader(post = post, onVote = vm::votePost, onToggleSave = vm::toggleSavePost, onMore = { postSheet = true })
                            }
                        }
                    }
                }
            }
        }
    }

    val post = state.post
    if (postSheet && post != null) {
        PostActionsSheet(post = post, onDismiss = { postSheet = false }, onHide = null, onDelete = { vm.delete(post.fullname) })
    }
    if (sortOpen) CommentSortSheet(state.sort, onDismiss = { sortOpen = false }, onPick = vm::setSort)
    if (actionsOpen && post != null) {
        TopSheet(onDismiss = { actionsOpen = false }) {
            SheetTitle(t("Comment actions"))
            QuickActionGrid(
                listOf(
                    QuickAction(t("Reply"), AppIcons.Reply) { reply(post.fullname, post.title) },
                    QuickAction(t("Refresh"), Symbols.Refresh) { vm.load(refresh = true) },
                    QuickAction(t("About"), Symbols.Info) { aboutOpen = true },
                    QuickAction(t("Collapse all"), AppIcons.CollapseAll, onClick = vm::collapseAll),
                    QuickAction(t("Settings"), Symbols.Settings) { nav.push(SettingsRoute()) },
                    QuickAction(t("Sort"), AppIcons.Sort) { sortOpen = true },
                    QuickAction(t("Search"), Symbols.Search) { searchOpen = true },
                    QuickAction(t("Recents"), AppIcons.Clock) { recentsOpen = true },
                    QuickAction(t("Sports mode"), AppIcons.Flame, active = state.sportsMode) { vm.setSportsMode(!state.sportsMode) },
                    QuickAction(t("Read aloud"), AppIcons.VolumeOn, active = readerState.active) {
                        if (readerState.active) {
                            reader.stop()
                        } else {
                            container.readAloud.stop()
                            scope.launch { reader.start(buildReaderQueue(post, state.comments, ::plainText, replies = settings.readerReplies)) }
                        }
                    },
                ) + remember(post, state.comments) { commentActionExtras(post, state.comments, nav) },
                onPicked = { actionsOpen = false },
            )
        }
    }
    if (aboutOpen && post != null) {
        CommunityAbout(container, post.subreddit, onDismiss = { aboutOpen = false }, onMessage = { message ->
            scope.launch { snackbar.showSnackbar(message) }
        })
    }
    if (recentsOpen) {
        val recents by container.recentPosts.posts.collectAsStateWithLifecycle()
        RecentsSheet(
            posts = recents.filter { it.id != route.postId },
            onDismiss = { recentsOpen = false },
            onPick = {
                recentsOpen = false
                nav.push(PostRoute(it.id))
            },
        )
    }
    if (searchOpen) {
        CommentSearchSheet(
            rows = visible,
            onDismiss = { searchOpen = false },
            onPick = { comment ->
                searchOpen = false
                scrollToComment(comment.fullname)
            },
        )
    }
    optionsFor?.let { comment ->
        if (post != null) {
            CommentOptionsSheet(
                comment = comment,
                post = post,
                onDismiss = { optionsFor = null },
                onDelete = { vm.delete(comment.fullname) },
            )
        }
    }
}

// --- Post header ---

@Composable
private fun PostHeader(post: Post, onVote: (Int) -> Unit, onToggleSave: () -> Unit, onMore: () -> Unit) {
    val nav = LocalNavigator.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        if (post.hasCardPreview()) {
            // Sync's default "Cropped height": a banner over the comments that still opens the media.
            val cropped = LocalSettings.current.postMediaPreview == MediaPreviewSize.CROPPED
            CardPreview(post, onClick = { nav.openMedia(post) }, maxHeight = if (cropped) 180.dp else null)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            PostTitle(post, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), dimWhenRead = false)
            Spacer(Modifier.height(4.dp))
            PostMeta(post, showSubreddit = false, maxLines = 3, suffix = post.upvoteRatio?.let { t("{0}% upvoted", (it * 100).roundToInt()) })
            if (post.flair != null) {
                Spacer(Modifier.height(8.dp))
                FlairChip(post)
            }
            if (post.media is PostMedia.Link && !post.hasCardPreview()) {
                Spacer(Modifier.height(10.dp))
                LinkRow(post, onClick = { nav.openMedia(post) })
            }
            post.selfTextHtml?.takeIf { it.isNotBlank() }?.let { html ->
                val body = rememberRedditText(html, footnotes = LocalSettings.current.linkCards)
                Spacer(Modifier.height(10.dp))
                RedditBodyText(body.text, CommentBody)
                LinkCards(body.links, Modifier.padding(top = 12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreChip(post, short = true)
                CommentsChip(post, short = true)
            }
            StateToggle(post.likes == true, { onVote(if (it) 1 else 0) }, AppIcons.ArrowUp, t("Upvote"), FadenColors.Upvote, muted)
            StateToggle(post.likes == false, { onVote(if (it) -1 else 0) }, AppIcons.ArrowDown, t("Downvote"), FadenColors.Downvote, muted)
            StateToggle(
                post.saved,
                { onToggleSave() },
                if (post.saved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                if (post.saved) t("Unsave") else t("Save"),
                FadenColors.Saved,
                muted,
            )
            IconButton(onClick = onMore) { Icon(Symbols.MoreVert, t("Post options"), tint = muted) }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.background))
    }
}
