package io.github.radixtrator.resync.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.BuildConfig
import io.github.radixtrator.resync.data.LatestRelease
import io.github.radixtrator.resync.data.VersionNotes
import io.github.radixtrator.resync.data.WhatsNew
import io.github.radixtrator.resync.ui.navigation.LocalNavigator

/**
 * The two notices at launch. After an update, "Resync was updated" lists what changed since the version you
 * had (from whats-new.md). Otherwise, now and then, GitHub is asked whether a newer release is out; if so its
 * list (the same bullets, copied into the release by the release workflow) is shown with a link to the release.
 */
@Composable
fun UpdateNotices(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val current = BuildConfig.VERSION_NAME
    var updated by remember { mutableStateOf<List<VersionNotes>>(emptyList()) }
    var newer by remember { mutableStateOf<LatestRelease?>(null) }

    LaunchedEffect(Unit) {
        val updates = container.appUpdates
        val lastSeen = updates.lastSeenVersion
        // Before setup there's no app to be updated yet: just note the version, so the next update knows it.
        if (!container.isConfigured) {
            updates.lastSeenVersion = current
            return@LaunchedEffect
        }
        if (lastSeen == null || WhatsNew.compare(lastSeen, current) != 0) {
            // With no record, an app that's been updated in place came from a version older than these notes
            // (it gets this version's); one installed fresh has nothing new to tell.
            val fresh = lastSeen == null && !container.hadEarlierVersion
            updated = if (fresh) emptyList() else WhatsNew.since(container.whatsNew, lastSeen, current)
            if (updated.isEmpty()) updates.lastSeenVersion = current
        }
        if (updated.isEmpty() && settings.checkForUpdates) newer = updates.newerRelease(current)
    }

    if (updated.isNotEmpty()) {
        WhatsNewDialog("Resync was updated", updated, onDismiss = {
            container.appUpdates.lastSeenVersion = current
            updated = emptyList()
        })
    }
    newer?.let { release ->
        val nav = LocalNavigator.current
        UpdateDialog(
            release = release,
            onView = {
                nav.openExternal(release.url)
                newer = null
            },
            onLater = {
                container.appUpdates.dismissedVersion = release.version
                newer = null
            },
        )
    }
}

/** [notes]' versions, newest first, each with its bullets; "View release" opens the newest one's GitHub release. */
@Composable
fun WhatsNewDialog(title: String, notes: List<VersionNotes>, onDismiss: () -> Unit) {
    val nav = LocalNavigator.current
    val newest = notes.firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (notes.isEmpty()) Text("Nothing listed for this version.")
                notes.forEachIndexed { i, version ->
                    // One version needs no heading; several get one each.
                    if (notes.size > 1) {
                        Text(
                            "Version ${version.version}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = if (i == 0) 0.dp else 12.dp, bottom = 4.dp),
                        )
                    }
                    Bullets(version.items)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = newest?.let {
            {
                TextButton(onClick = {
                    nav.openExternal(WhatsNew.releaseUrl(it.version))
                    onDismiss()
                }) { Text("View release") }
            }
        },
    )
}

@Composable
private fun UpdateDialog(release: LatestRelease, onView: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Resync ${release.version} is out") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (release.items.isEmpty()) {
                    Text("A new version is available on GitHub.")
                } else {
                    Text("What's new:", Modifier.padding(bottom = 4.dp))
                    Bullets(release.items)
                }
            }
        },
        confirmButton = { TextButton(onClick = onView) { Text("View release") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
    )
}

@Composable
private fun Bullets(items: List<String>) {
    items.forEach { item ->
        Row(Modifier.padding(vertical = 2.dp)) {
            Text("•", Modifier.padding(end = 8.dp))
            Text(item)
        }
    }
}
