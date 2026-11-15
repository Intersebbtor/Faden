@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/** Communities get a scalloped cookie, people a plain circle. */
enum class AvatarKind { Community, User }

/**
 * An icon or picture clipped to its kind's shape, with the first letter of [letterOf] behind it while there is no
 * picture (listings don't carry community icons, and comment listings don't carry avatars). Leave [letterOf] empty
 * for no letter. A [replaceLetter] picture covers the whole circle with no letter or background behind it, for
 * snoovatars, whose backgrounds are transparent.
 */
@Composable
fun Avatar(
    url: String?,
    letterOf: String,
    size: Dp,
    modifier: Modifier = Modifier,
    kind: AvatarKind = AvatarKind.Community,
    letterStyle: TextStyle = MaterialTheme.typography.labelLarge,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    replaceLetter: Boolean = false,
) {
    val shape = (if (kind == AvatarKind.Community) MaterialShapes.Cookie9Sided else MaterialShapes.Circle).toShape()
    if (replaceLetter && url != null) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(shape))
        return
    }
    Box(modifier.size(size).clip(shape).background(containerColor), contentAlignment = Alignment.Center) {
        if (letterOf.isNotEmpty()) Text(letterOf.take(1).uppercase(), style = letterStyle, color = contentColor)
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}
