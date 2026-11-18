package io.github.radixtrator.resync.ui.common

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.data.Redgifs
import io.github.radixtrator.resync.ui.media.VideoCache
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The key of the feed item whose preview plays right now under Data options › "Autoplay", or null for none. Provided by
 * [PagedGrid] for feeds that ask for it; elsewhere (a post page, search) nothing autoplays.
 */
val LocalAutoplayKey = compositionLocalOf<String?> { null }

/**
 * What a feed preview of this post would autoplay: Reddit's own stream, or Reddit's mp4 of a GIF. A RedGifs page needs a
 * lookup first, so it plays Reddit's copy of the clip when there is one, and nothing otherwise. A GIF Reddit hasn't
 * transcoded stays still: playing it would mean loading the whole GIF, possibly from a site that then sees who's reading.
 */
fun Post.autoplayStream(): String? = when (val m = media) {
    is PostMedia.Video -> if (Redgifs.id(m.url) != null) m.fallback else m.url
    is PostMedia.Image -> m.mp4
    else -> null
}

/**
 * The item of [items] whose preview should play: of the visible ones [playable] accepts, the one nearest the middle of
 * the viewport that's mostly on screen (at least [MIN_VISIBLE] of it, or of the viewport when it's taller). Item i sits
 * at grid index i + 1, after the header. Read through a derived state, so scrolling only recomposes when it changes.
 */
@Composable
fun <T> rememberAutoplayKey(
    gridState: LazyStaggeredGridState,
    items: List<T>,
    key: (T) -> String,
    playable: (T) -> Boolean,
): State<String?> {
    val currentItems by rememberUpdatedState(items)
    val currentKey by rememberUpdatedState(key)
    val currentPlayable by rememberUpdatedState(playable)
    return remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val start = info.viewportStartOffset
            val end = info.viewportEndOffset
            val viewport = end - start
            if (viewport <= 0) return@derivedStateOf null
            val middle = (start + end) / 2f
            info.visibleItemsInfo
                .filter { visible ->
                    val item = currentItems.getOrNull(visible.index - 1) ?: return@filter false
                    if (currentKey(item) != visible.key || !currentPlayable(item)) return@filter false
                    val top = visible.offset.y
                    val bottom = top + visible.size.height
                    val shown = min(bottom, end) - max(top, start)
                    shown >= MIN_VISIBLE * min(visible.size.height, viewport)
                }
                .minByOrNull { visible -> abs(visible.offset.y + visible.size.height / 2f - middle) }
                ?.key as? String
        }
    }
}

private const val MIN_VISIBLE = 0.6f

/**
 * Where a clip has got to, so it carries on instead of starting over as it moves between its autoplaying feed preview
 * and the full-screen player, either way. The two overlap while a page slides in or out, so a player that's still
 * playing is asked first ([attach]); one that's gone leaves its position behind ([detach]). Keyed by stream, main
 * thread only. The full-screen player takes part only while "Autoplay" is active, so without it nothing changes there.
 */
internal object PlaybackHandoff {
    private class Live(val owner: Any, val position: () -> Long)

    private val live = HashMap<String, Live>()
    private val saved = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > SAVED_POSITIONS
    }

    /** Where [stream] should start: a playing player's position, else the last one left behind, else the start. */
    fun position(stream: String): Long = live[stream]?.position?.invoke() ?: saved[stream] ?: 0L

    /** [player] now plays [stream]; it's the one [position] asks until another attaches or it [detach]es. */
    fun attach(stream: String, player: Player) = attach(stream, player) { player.currentPosition }

    /** [player] is about to be released: its position stays behind, unless a newer player has taken [stream] over. */
    fun detach(stream: String, player: Player) = detach(stream, owner = player)

    /** [attach] for anything that has a [position]: the players, and tests. */
    fun attach(stream: String, owner: Any, position: () -> Long) {
        live[stream] = Live(owner, position)
    }

    fun detach(stream: String, owner: Any) {
        val current = live[stream]?.takeIf { it.owner === owner } ?: return
        live.remove(stream)
        saved[stream] = current.position()
    }

    private const val SAVED_POSITIONS = 30
}

/**
 * A muted, looping clip filling its box, cropped to cover it as the preview image is ([androidx.compose.ui.layout.ContentScale.Crop]),
 * or with [fit] shown whole and centered, as Swipe mode's image is ([androidx.compose.ui.layout.ContentScale.Fit]).
 * It stays invisible until its first frame is drawn ([onFirstFrame]), so the still preview underneath shows meanwhile,
 * and pauses while the app is in the background. SD only, through the player's on-disk cache; it doesn't take audio
 * focus, so music keeps playing.
 */
@OptIn(UnstableApi::class)
@Composable
fun AutoplayVideo(stream: String, onFirstFrame: () -> Unit, modifier: Modifier = Modifier, fit: Boolean = false) {
    val context = LocalContext.current
    var aspect by remember(stream) { mutableFloatStateOf(0f) }
    var shown by remember(stream) { mutableStateOf(false) }
    val player = remember(stream) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(VideoCache.dataSourceFactory(context, null)))
            .build().apply {
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(stream)
                        .apply { if (stream.contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
                        .build(),
                )
                trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxVideoSizeSd().build()
                // Carries on where the clip was, in the full-screen player or the last time it played here.
                PlaybackHandoff.position(stream).takeIf { it > 0 }?.let(::seekTo)
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                playWhenReady = true
                prepare()
            }
    }
    val firstFrame by rememberUpdatedState(onFirstFrame)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                shown = true
                firstFrame()
            }
        }
        player.addListener(listener)
        PlaybackHandoff.attach(stream, player)
        onDispose {
            PlaybackHandoff.detach(stream, player)
            player.removeListener(listener)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { player.play() }
    Box(modifier.clipToBounds().graphicsLayer { alpha = if (shown) 1f else 0f }) {
        AndroidView(
            factory = { ctx -> TextureView(ctx) },
            update = { view ->
                if (view.tag !== player) {
                    view.tag = player
                    player.setVideoTextureView(view)
                }
            },
            modifier = Modifier.fillMaxSize().scaleTo(aspect, cover = !fit),
        )
    }
}

/** Sizes the video at its own [aspect], centered: to [cover] the box (which clips the overflow), else to fit inside it. */
private fun Modifier.scaleTo(aspect: Float, cover: Boolean) = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    if (aspect <= 0f || width == 0 || height == 0 || !constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val (videoWidth, videoHeight) = if ((width.toFloat() / height > aspect) == cover) {
        width to (width / aspect).roundToInt()
    } else {
        (height * aspect).roundToInt() to height
    }
    val placeable = measurable.measure(Constraints.fixed(videoWidth, videoHeight))
    layout(width, height) { placeable.place((width - videoWidth) / 2, (height - videoHeight) / 2) }
}
