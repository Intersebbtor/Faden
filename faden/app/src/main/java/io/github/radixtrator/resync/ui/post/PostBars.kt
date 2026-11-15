@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.radixtrator.resync.ui.post

import io.github.radixtrator.resync.ui.common.Symbols
import io.github.radixtrator.resync.i18n.I18n
import io.github.radixtrator.resync.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.ui.theme.FadenAccents
import io.github.radixtrator.resync.ui.common.AppIcons

/** Sync's live-thread bar (`view_sports`): a live marker, Refresh and Stop; [seconds] is the refresh rate. */
@Composable
internal fun SportsBar(seconds: Int, onRefresh: () -> Unit, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Spacer(Modifier.width(12.dp))
        Text(
            t("Sports mode · new comments every {0}s", seconds),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        HorizontalFloatingToolbar(expanded = true) {
            IconButton(onClick = onRefresh) { Icon(Symbols.Refresh, t("Refresh now")) }
            IconButton(onClick = onStop) { Icon(Symbols.Close, t("Stop sports mode")) }
        }
    }
}

/** The comment reader's ⋮ speeds (TextToSpeech speech rates). */
private val ReaderSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

private fun speedLabel(rate: Float): String =
    (if (rate == rate.toInt().toFloat()) rate.toInt().toString() else rate.toString().let { if (I18n.locale.language == "de") it.replace('.', ',') else it }) + "×"

/**
 * Sync's comment text-to-speech player (`view_tts`): what's being read, then Stop, Previous, Play/Pause, Next,
 * and a ⋮ with "Read replies" and the speech speed.
 */
@Composable
internal fun ReaderBar(
    state: CommentReader.State,
    readReplies: Boolean,
    speechRate: Float,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onReadReplies: (Boolean) -> Unit,
    onSpeechRate: (Float) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.current?.label.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t("Reading {0} of {1}", state.index + 1, state.items.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Symbols.MoreVert, t("Reader options")) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(t("Read replies")) },
                        leadingIcon = { Icon(Symbols.Check, null, tint = if (readReplies) MaterialTheme.colorScheme.primary else Color.Transparent) },
                        onClick = {
                            menuOpen = false
                            onReadReplies(!readReplies)
                        },
                    )
                    HorizontalDivider()
                    Text(
                        t("Speed"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    ReaderSpeeds.forEach { rate ->
                        DropdownMenuItem(
                            text = { Text(speedLabel(rate)) },
                            leadingIcon = { Icon(Symbols.Check, null, tint = if (rate == speechRate) MaterialTheme.colorScheme.primary else Color.Transparent) },
                            onClick = {
                                menuOpen = false
                                onSpeechRate(rate)
                            },
                        )
                    }
                }
            }
        }
        // Stop, Previous and Next in the toolbar with Play/Pause as its button.
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            floatingActionButton = {
                FloatingToolbarDefaults.StandardFloatingActionButton(onClick = onTogglePlay, containerColor = FadenAccents.fabContainer, contentColor = FadenAccents.onFab) {
                    if (state.playing) Icon(AppIcons.Pause, t("Pause")) else Icon(Symbols.PlayArrowFilled, t("Play"))
                }
            },
        ) {
            IconButton(onClick = onStop) { Icon(Symbols.Close, t("Stop reading")) }
            IconButton(onClick = onPrevious, enabled = state.index > 0) { Icon(Symbols.ArrowBack, t("Previous")) }
            IconButton(onClick = onNext, enabled = state.index < state.items.lastIndex) {
                Icon(Symbols.ArrowForward, t("Next"))
            }
        }
    }
}
