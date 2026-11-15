package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The big page title Sync puts above every top-level list ("Frontpage", "pics", "Inbox"), with an
 * optional [leading] image (a community icon), an [overline] (subscriber count) and a scrolling row of
 * [chips]. It's the list's first item, so it scrolls away while the toolbar returns on scroll-up.
 */
@Composable
fun LargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    overline: (@Composable () -> Unit)? = null,
    chips: @Composable RowScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val thread = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 48.dp, bottom = 12.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .drawWithCache {
                // Faden's thread: one orange stroke sweeping across the lower right with a single loop.
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.35f, h * 1.08f)
                    cubicTo(w * 0.55f, h * 0.70f, w * 0.72f, h * 1.05f, w * 0.80f, h * 0.78f)
                    cubicTo(w * 0.88f, h * 0.50f, w * 0.66f, h * 0.52f, w * 0.72f, h * 0.74f)
                    cubicTo(w * 0.78f, h * 0.96f, w * 0.95f, h * 0.70f, w * 1.06f, h * 0.45f)
                }
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind { drawPath(path, thread, style = stroke) }
            }
            .padding(20.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    overline?.invoke()
                    // Faden's page titles: the theme's headlineLarge (Figtree) at extra bold.
                    Text(title, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // The chips scroll edge to edge of the hero, so they bleed past its padding instead of clipping inside it.
        Row(
            Modifier
                .padding(top = 16.dp)
                .layout { m, c ->
                    val extra = 20.dp.roundToPx()
                    val p = m.measure(c.copy(minWidth = c.maxWidth + 2 * extra, maxWidth = c.maxWidth + 2 * extra))
                    layout(c.maxWidth, p.height) { p.place(-extra, 0) }
                }
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = chips,
        )
    }
}

/** The label of the header chips: the theme's labelLarge, a point larger so it stays readable at the app's dense base font size. */
val HeaderChipLabel: TextStyle
    @Composable get() = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)

/** A filled, borderless chip in the theme's small (pill) shape, 36dp tall. [dropdown] adds the ▾ for pickers. */
@Composable
fun HeaderChip(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    dropdown: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = HeaderChipLabel) },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(AssistChipDefaults.IconSize)) } },
        trailingIcon = if (dropdown) {
            { Icon(Symbols.ArrowDropDown, null, Modifier.size(AssistChipDefaults.IconSize)) }
        } else null,
        shape = MaterialTheme.shapes.small,
        border = null,
        modifier = Modifier.height(36.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurface,
            trailingIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** A header chip whose ▾ opens a menu of [options], like Sync's section and sort chips. */
@Composable
fun <T> ChipMenu(label: String, icon: ImageVector, options: List<T>, labelOf: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderChip(label, icon, { open = true }, dropdown = true)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(labelOf(option)) }, onClick = {
                    open = false
                    onPick(option)
                })
            }
        }
    }
}
