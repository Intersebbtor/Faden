package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.radixtrator.resync.data.ImageSource
import io.github.radixtrator.resync.data.Post
import io.github.radixtrator.resync.data.PostLayout
import io.github.radixtrator.resync.data.PostMedia
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.ui.navigation.LocalFrontpageOrAllFeed
import io.github.radixtrator.resync.ui.theme.FadenColors
import kotlin.math.min
import kotlin.math.roundToInt

/** The band Slides crop previews to, and that Cards with "Full height" off crop tall previews to. */
internal val CroppedPreviewHeight = 220.dp

/** Whether [CardPreview] has a big image to show (links without one fall back to a link row). */
fun Post.hasCardPreview(): Boolean = media.cardImage() != null

private fun PostMedia.cardImage(): ImageSource? = when (this) {
    is PostMedia.Image -> preview ?: ImageSource(url, 0, 0)
    is PostMedia.Gallery -> preview
    is PostMedia.Video -> preview
    // Link thumbnails come from the preview when Reddit has one; tiny legacy thumbnails stay in rows.
    is PostMedia.Link -> thumbnail?.takeIf { it.width == 0 || it.width >= 320 }
    PostMedia.None -> null
}

/**
 * What a feed of [layout] in [columns] will load for a post: the [CardPreview] or [Thumbnail] image, at the rendition and
 * size those ask for, so [PreloadImages] can have it ready before it scrolls into view. Empty under data saving.
 */
@Composable
fun rememberPostPreloads(layout: PostLayout, columns: Int): (Post) -> List<ImagePreload> {
    val display = LocalFeedDisplay.current
    val frontpageOrAll = LocalFrontpageOrAllFeed.current
    val saving = dataSavingActive()
    val columnWidth = LocalWindowInfo.current.containerSize.width / columns.coerceAtLeast(1)
    val density = LocalDensity.current
    return remember(layout, display, frontpageOrAll, saving, columnWidth, density) {
        val options = display.viewOptionsFor(layout)
        val thumbnailSize = when (layout) {
            PostLayout.LIST -> if (options.thumbnails) 72.dp else null
            PostLayout.COMPACT, PostLayout.SMALLER_CARDS -> if (options.thumbnails) 64.dp else null
            PostLayout.SMALL_CARDS -> 64.dp
            PostLayout.CARD, PostLayout.SLIDES -> null
        }
        val cards = layout == PostLayout.CARD || (layout == PostLayout.SLIDES && options.imagePreviews)
        val thumbnailPx = thumbnailSize?.let { with(density) { it.roundToPx() } }
        val preloads: (Post) -> List<ImagePreload> = { post ->
            val decode = !post.shouldBlur(display, frontpageOrAll)
            if (saving) emptyList() else when {
                cards -> post.media.cardImage()?.let { image ->
                    val ratio = image.aspectRatio.coerceIn(0.5f, 2.2f)
                    listOf(ImagePreload(image.urlFor(columnWidth), columnWidth, (columnWidth / ratio).roundToInt(), decode))
                }
                thumbnailPx != null -> post.thumbnail()?.let { listOf(ImagePreload(it.urlFor(thumbnailPx), thumbnailPx, thumbnailPx, decode)) }
                else -> null
            }.orEmpty()
        }
        preloads
    }
}

/**
 * Full-width media for cards and slides, at the image's own aspect (Sync's "Preview full height"),
 * with a centered badge for galleries and video and, on links, the domain/path band along the bottom.
 */
@Composable
fun CardPreview(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp? = null, maxHeight: Dp? = null) {
    val image = post.media.cardImage() ?: return
    val blur = post.shouldBlur()
    val saving = dataSavingActive()
    val ratio = image.aspectRatio.coerceIn(0.5f, 2.2f)
    // About a column's width: a three-column grid of cards needn't fetch 1080px previews.
    val columnWidth = LocalWindowInfo.current.containerSize.width / LocalFeedColumns.current
    Box(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    height != null -> Modifier.height(height)
                    // Its natural height up to [maxHeight], cropped beyond that.
                    maxHeight != null -> Modifier.layout { measurable, constraints ->
                        val width = constraints.maxWidth
                        val cropped = min((width / ratio).roundToInt(), maxHeight.roundToPx())
                        val placeable = measurable.measure(Constraints.fixed(width, cropped))
                        layout(width, cropped) { placeable.place(0, 0) }
                    }
                    else -> Modifier.aspectRatio(ratio)
                },
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Holding it peeks at the picture, in feeds.
            .peekableClick(post, onClick),
    ) {
        // Data saving leaves the preview unloaded: the empty frame keeps its size, with the post's type in it.
        if (!saving) {
            AsyncImage(
                model = rememberPreviewRequest(image.urlFor(columnWidth), blurWidth = if (blur) CARD_BLUR_WIDTH else null),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        when (post.media) {
            is PostMedia.Gallery -> CenterBadge(AppIcons.Gallery, t("Gallery"), Modifier.align(Alignment.Center))
            is PostMedia.Video -> CenterBadge(AppIcons.PlayCircle, t("Video"), Modifier.align(Alignment.Center))
            else -> if (saving && !blur) {
                Icon(typeIcon(post), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).size(32.dp))
            }
        }
        if (post.media is PostMedia.Link) UrlBand(post.domain, post.url, Modifier.align(Alignment.BottomCenter))
        if (blur) {
            Text(
                if (post.over18) t("NSFW") else t("Spoiler"),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (post.over18) FadenColors.Nsfw else Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CenterBadge(icon: ImageVector, description: String, modifier: Modifier) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.4f),
        contentColor = Color.White,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)),
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, modifier = Modifier.size(24.dp)) }
    }
}

/** "thehill.com/policy/international/…" over the bottom of a link preview. */
@Composable
private fun UrlBand(domain: String, url: String, modifier: Modifier) {
    val path = url.substringAfter("://", url).substringAfter('/', "").let { if (it.isEmpty()) "" else "/$it" }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(domain) }
            append(path)
        },
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** How far blurred previews are shrunk ([rememberPreviewRequest]): a heavy blur on cards, a lighter one on thumbnails. */
const val CARD_BLUR_WIDTH = 24
private const val THUMBNAIL_BLUR_WIDTH = 6

/** A square or rounded thumbnail; posts without an image get their content-type icon. */
@Composable
internal fun Thumbnail(post: Post, size: Dp, shape: Shape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Data saving shows the type icon in place of the image.
    val saving = dataSavingActive()
    val thumb = post.thumbnail()?.takeUnless { saving }
    val widthPx = with(LocalDensity.current) { size.roundToPx() }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // Holding it peeks at the picture, in feeds.
            .peekableClick(post, onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            AsyncImage(
                model = rememberPreviewRequest(thumb.urlFor(widthPx), blurWidth = if (post.shouldBlur()) THUMBNAIL_BLUR_WIDTH else null),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(typeIcon(post), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun typeIcon(post: Post): ImageVector = when (post.media) {
    is PostMedia.Image -> AppIcons.Image
    is PostMedia.Gallery -> AppIcons.Gallery
    is PostMedia.Video -> AppIcons.PlayCircle
    is PostMedia.Link -> AppIcons.Link
    PostMedia.None -> AppIcons.Text
}

/** Sync's feed preview of a text post: its first paragraph as plain text, then "…" on a line of its own if there's more. */
@Composable
internal fun selfTextPreview(post: Post): String? {
    if (!post.isSelf) return null
    // Made when the post was parsed; a post built by hand has none, so its plain text stands in.
    return post.previewText ?: if (post.selfTextHtml == null) firstParagraph(post.selfText) else null
}
