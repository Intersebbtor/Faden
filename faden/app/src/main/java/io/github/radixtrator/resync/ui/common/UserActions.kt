package io.github.radixtrator.resync.ui.common

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.radixtrator.resync.data.ReportReason
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.ui.navigation.LocalContainer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sync's "Report reasons": the community's rules for [comment]s or posts, Reddit's own rules, then Other.
 * Each asks before sending, since a report goes to the moderators (or Reddit) for real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(fullname: String, subreddit: String, comment: Boolean, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val reasons by produceState<Result<List<ReportReason>>?>(null, subreddit, comment) {
        value = runCatching { container.api.reportReasons(subreddit, comment) }.onFailure { if (it is CancellationException) throw it }
    }
    var confirm by remember { mutableStateOf<ReportReason?>(null) }
    var other by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            SheetTitle(t("Report reasons"), topPadding = 0.dp)
            when (val result = reasons) {
                null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Without the rules (a private community, say), Other still works.
                    result.onFailure {
                        Text(
                            t("Couldn't load r/{0}'s rules: {1}", subreddit, it.message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    result.getOrDefault(emptyList()).forEach { reason -> OptionRow(reason.text, null, onClick = { confirm = reason }) }
                    OptionRow(t("Other"), null, onClick = { other = true })
                }
            }
        }
    }
    confirm?.let { reason ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (comment) t("Report this comment?") else t("Report this post?")) },
            text = { Text(reason.text) },
            confirmButton = {
                TextButton(onClick = {
                    container.report(fullname, reason)
                    onDismiss()
                }) { Text(t("Report")) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("Cancel")) } },
        )
    }
    if (other) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { other = false },
            title = { Text(if (comment) t("Report this comment?") else t("Report this post?")) },
            text = { OutlinedTextField(text, { text = it.take(100) }, label = { Text(t("Reason")) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    container.report(fullname, reason = null, other = text.trim())
                    onDismiss()
                }) { Text(t("Report")) }
            },
            dismissButton = { TextButton(onClick = { other = false }) { Text(t("Cancel")) } },
        )
    }
}

/** The tag tints on offer, after the theme default: red, orange, yellow, green, blue, purple (ARGB). */
private val TAG_COLORS = listOf(0xFFE57373.toInt(), 0xFFFFB74D.toInt(), 0xFFFFF176.toInt(), 0xFF81C784.toInt(), 0xFF64B5F6.toInt(), 0xFFBA68C8.toInt())

/** Sync's "Tag user": a label of your own for [user], shown beside their name. Empty is refused, as in Sync. */
@Composable
fun TagUserDialog(user: String, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val current = remember(user) { container.tags.tagOf(user) }
    var text by remember(user) { mutableStateOf(current.orEmpty()) }
    var empty by remember { mutableStateOf(false) }
    var color by remember(user) { mutableStateOf(container.tags.colorOf(user)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Tag u/{0}", user)) },
        text = {
          Column {
            OutlinedTextField(
                text,
                {
                    text = it.take(40)
                    empty = false
                },
                label = { Text(t("Tag")) },
                singleLine = true,
                isError = empty,
                supportingText = if (empty) ({ Text(t("Tag name must not be empty.")) }) else null,
            )
            Row(Modifier.selectableGroup().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Int?>(null) .plus(TAG_COLORS).forEach { swatch ->
                    val selected = swatch == color
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(swatch?.let { Color(it) } ?: MaterialTheme.colorScheme.tertiaryContainer)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .semantics { contentDescription = swatch?.let { c -> t("Tag color {0}", "#%06X".format(c and 0xFFFFFF)) } ?: t("Default tag color") }
                            .selectable(selected = selected, role = Role.RadioButton) { color = swatch },
                    )
                }
            }
          }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) {
                    empty = true
                } else {
                    container.tags.set(user, text)
                    container.tags.setColor(user, color)
                    Toast.makeText(context, t("Tag added: {0}", text.trim()), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text(t("Save")) }
        },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = {
                    container.tags.set(user, null)
                    Toast.makeText(context, t("Tag removed"), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text(t("Remove tag")) }
            } else {
                TextButton(onClick = onDismiss) { Text(t("Cancel")) }
            }
        },
    )
}
