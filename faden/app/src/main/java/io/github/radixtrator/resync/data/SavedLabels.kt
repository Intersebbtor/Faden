package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Your own labels on saved posts and comments ("Recipes", "Read later"), which Reddit's single saved list doesn't have.
 * Kept on the phone, by fullname (t3_…, t1_…); the profile's Saved tab filters by them.
 */
class SavedLabels(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), SetSerializer(String.serializer()))
    private val _labels = MutableStateFlow(load())
    val labels: StateFlow<Map<String, Set<String>>> = _labels.asStateFlow()

    fun labelsOf(fullname: String): Set<String> = _labels.value[fullname].orEmpty()

    /** Every label in use, alphabetically (ignoring case). */
    fun all(): List<String> = all(_labels.value)

    /** Gives [fullname] exactly [labels] (trimmed, blank ones dropped); none removes its entry. */
    fun set(fullname: String, labels: Set<String>) {
        val clean = labels.mapNotNull { it.trim().take(MAX_LENGTH).takeIf(String::isNotEmpty) }.toSet()
        change { if (clean.isEmpty()) it - fullname else it + (fullname to clean) }
    }

    /** Takes [label] off everything that has it. */
    fun delete(label: String) = change { all ->
        all.mapValues { (_, set) -> set - label }.filterValues { it.isNotEmpty() }
    }

    /** Renames [from] to [to] everywhere (merging with [to] where both were set). */
    fun rename(from: String, to: String) {
        val name = to.trim().take(MAX_LENGTH)
        if (name.isEmpty() || name == from) return
        change { all -> all.mapValues { (_, set) -> if (from in set) set - from + name else set } }
    }

    private fun change(transform: (Map<String, Set<String>>) -> Map<String, Set<String>>) {
        _labels.update(transform)
        prefs.edit { putString(KEY, json.encodeToString(serializer, _labels.value)) }
    }

    private fun load(): Map<String, Set<String>> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    companion object {
        const val KEY = "savedlabels.v1"
        private const val MAX_LENGTH = 40

        fun all(labels: Map<String, Set<String>>): List<String> =
            labels.values.flatten().distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
}
