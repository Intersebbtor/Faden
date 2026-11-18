@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.radixtrator.resync.data.SavedLabels
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.ui.navigation.LocalContainer

/**
 * Labels for one saved post or comment ([fullname]): every label in use as a checkbox, plus a field for a new one.
 * Changes apply as they're made.
 */
@Composable
fun SavedLabelsSheet(fullname: String, onDismiss: () -> Unit) {
    val store = LocalContainer.current.savedLabels
    val all by store.labels.collectAsStateWithLifecycle()
    val mine = all[fullname].orEmpty()
    // Labels picked here stay listed even if unticked again, so a slip can be undone in place.
    var shown by remember { mutableStateOf(SavedLabels.all(all)) }
    var draft by rememberSaveable { mutableStateOf("") }

    fun add() {
        val name = draft.trim()
        if (name.isEmpty()) return
        store.set(fullname, mine + name)
        if (shown.none { it.equals(name, ignoreCase = true) }) shown = (shown + name).sortedWith(String.CASE_INSENSITIVE_ORDER)
        draft = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { SheetHandle() }) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(bottom = 8.dp).verticalScroll(rememberScrollState())) {
            SheetTitle(t("Labels"), topPadding = 0.dp)
            if (shown.isEmpty()) {
                Text(
                    t("Labels sort your saved posts and comments, like folders. They stay on this phone."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            shown.forEach { label ->
                val on = label in mine
                OptionRow(
                    label,
                    null,
                    onClick = { store.set(fullname, if (on) mine - label else mine + label) },
                    toggled = on,
                    toggleRole = Role.Checkbox,
                    trailing = { Checkbox(checked = on, onCheckedChange = null) },
                )
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(40) },
                    label = { Text(t("New label")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = ::add, enabled = draft.isNotBlank()) { Icon(Symbols.Add, t("Add label")) }
            }
        }
    }
}
