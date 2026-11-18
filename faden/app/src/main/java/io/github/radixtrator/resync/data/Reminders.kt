package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.radixtrator.resync.i18n.t
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A post to come back to: a notification at [atMillis] opens it again. */
@Serializable
data class Reminder(val postId: String, val title: String, val subreddit: String, val atMillis: Long)

/** When a reminder can be set for, as Post options › Remind me lists them. */
enum class RemindIn(private val en: String, val millis: Long) {
    HOUR("In 1 hour", 60 * 60 * 1000L),
    THREE_HOURS("In 3 hours", 3 * 60 * 60 * 1000L),
    TOMORROW("Tomorrow", 24 * 60 * 60 * 1000L),
    THREE_DAYS("In 3 days", 3 * 24 * 60 * 60 * 1000L),
    WEEK("In a week", 7 * 24 * 60 * 60 * 1000L);

    val label: String get() = t(en)
}

/**
 * The reminders that are set, by post id, so Post options can say a post has one and cancel it. The notification
 * itself is a WorkManager job ([io.github.radixtrator.resync.notify.ReminderWorker]); this only remembers them.
 * Local to the phone, like those jobs, so it isn't part of a backup.
 */
class Reminders(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), Reminder.serializer())
    private val _all = MutableStateFlow(load())
    val all: StateFlow<Map<String, Reminder>> = _all.asStateFlow()

    fun set(reminder: Reminder) = change { it + (reminder.postId to reminder) }

    fun remove(postId: String) = change { it - postId }

    /** Reminders whose time has passed (their job ran, or was lost to a reinstall) are forgotten. */
    fun pending(nowMillis: Long = System.currentTimeMillis()): Map<String, Reminder> = _all.value.filterValues { it.atMillis > nowMillis }

    private fun change(transform: (Map<String, Reminder>) -> Map<String, Reminder>) {
        _all.update(transform)
        prefs.edit { putString(KEY, json.encodeToString(serializer, _all.value)) }
    }

    private fun load(): Map<String, Reminder> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
            .filterValues { it.atMillis > System.currentTimeMillis() }

    private companion object {
        const val KEY = "reminders.v1"
    }
}
