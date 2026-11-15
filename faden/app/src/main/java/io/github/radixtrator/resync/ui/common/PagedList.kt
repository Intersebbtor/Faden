package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.ProfileItem
import io.github.radixtrator.resync.i18n.t

/**
 * A paged LazyColumn with loading/error/empty states and load-more near the end.
 *
 * Without a [header], the "nothing yet" states render outside the list: LazyColumn keeps the
 * first visible keyed item in view, so a lone keyed footer would stay pinned once items arrive
 * and the list would open scrolled to the end.
 */
@Composable
fun <T> PagedList(
    state: ListingState<T>,
    key: (T) -> String,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(vertical = 6.dp),
    emptyText: String = t("Nothing here yet"),
    header: (LazyListScope.() -> Unit)? = null,
    item: @Composable (T) -> Unit,
) {
    if (header == null && state.items.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(state, emptyText, onRetry)
        }
        return
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.items.isNotEmpty()) onLoadMore() }

    LazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier.fillMaxSize()) {
        header?.invoke(this)
        // Typed by class, and for posts by their media, so a slot is only reused for a row of the same shape.
        items(state.items, key = key, contentType = { rowContentType(it) }) { item(it) }
        item(key = "footer") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                if (state.items.isEmpty()) {
                    EmptyState(state, emptyText, onRetry)
                } else when {
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        TextButton(onClick = onRetry) { Text(t("Retry")) }
                    }
                    state.endReached -> Text(t("You've reached the end"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun EmptyState(state: ListingState<*>, emptyText: String, onRetry: () -> Unit) {
    when {
        state.loading || state.refreshing -> CircularProgressIndicator()
        state.error != null -> Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(t("Couldn't load this"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(state.error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(t("Retry")) }
        }
        else -> Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
    }
}

/**
 * [PagedList] as a staggered grid, for feeds: Sync's card layouts tile into [columns], row layouts use
 * one column. [header] spans every column and ignores the [gutter], so it lines up with the toolbar.
 */
@Composable
fun <T> PagedGrid(
    state: ListingState<T>,
    key: (T) -> String,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    columns: Int,
    gutter: Dp,
    spacing: Dp,
    gridState: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    emptyText: String = t("Nothing here yet"),
    preloadsOf: ((T) -> List<ImagePreload>)? = null,
    header: @Composable () -> Unit,
    item: @Composable (T) -> Unit,
) {
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.items.isNotEmpty()) onLoadMore() }
    // Images a few rows ahead load before they're reached. Grid item 0 is the header, so item i sits at grid index i + 1.
    if (preloadsOf != null) {
        PreloadImages(
            items = state.items,
            lastVisible = { (gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0) - 1 },
            decodeAhead = PRELOAD_DECODE_ROWS * columns,
            fetchAhead = PRELOAD_FETCH_ROWS * columns,
            preloadsOf = preloadsOf,
        )
    }

    CompositionLocalProvider(LocalFeedColumns provides columns) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(gutter),
            verticalItemSpacing = spacing,
            modifier = modifier.fillMaxSize(),
        ) {
            item(key = "header", span = StaggeredGridItemSpan.FullLine) { Box(Modifier.fullBleed(gutter)) { header() } }
            // Typed by class, and for posts by their media, so a slot is only reused for a row of the same shape.
            items(state.items, key = key, contentType = { rowContentType(it) }) { item(it) }
            item(key = "footer", span = StaggeredGridItemSpan.FullLine) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (state.items.isEmpty()) {
                        EmptyState(state, emptyText, onRetry)
                    } else when {
                        state.loading -> CircularProgressIndicator()
                        state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            TextButton(onClick = onRetry) { Text(t("Retry")) }
                        }
                        state.endReached -> Text(t("You've reached the end"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** How many rows past the screen [PagedGrid] decodes into memory, and how many more it only downloads. */
private const val PRELOAD_DECODE_ROWS = 4
private const val PRELOAD_FETCH_ROWS = 12

/** Widens an item by [gutter] on both sides, cancelling the grid's content padding. */
private fun Modifier.fullBleed(gutter: Dp) = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Shows a view model's one-shot [ListingState.message] as a snackbar. */
@Composable
fun MessageEffect(message: String?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onShown()
        }
    }
}

/**
 * What a row's composition looks like, for LazyList slot reuse: posts by their media kind (an image card, a text card
 * and a gallery card lay out differently), everything else by its class.
 */
internal fun rowContentType(row: Any?): Any? = when (row) {
    null -> null
    is Post -> row.media::class
    is ProfileItem.PostItem -> row.post.media::class
    else -> row::class
}
