package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The high-churn stores (read posts, comment positions, thread visits, recents, explore) live in their own
 * preferences file, so their frequent writes don't re-serialise the settings and everything else alongside them.
 * Key names are unchanged from when they shared the "resync" file; backups still treat all keys as one map.
 */
object HistoryPrefs {
    const val FILE = "faden_history"

    val KEYS: Set<String> = setOf("visited.v1", "post_memory.v1", "thread_visits.v1", "recent_posts.v1", "explore.v1")

    fun isHistoryKey(key: String): Boolean = key in KEYS

    /**
     * One-time, idempotent move of the [KEYS] from [old] to [history]. A key is copied only if [history] lacks it,
     * and removed from [old] only once [history] holds it (the copy is committed first, so a crash between the two
     * steps just repeats the removal next launch). A no-op on a fresh install or when already migrated.
     */
    fun migrate(old: SharedPreferences, history: SharedPreferences) {
        if (old === history) return
        val oldAll = old.all
        val toCopy = KEYS.filter { key -> oldAll[key] is String && !history.contains(key) }
        if (toCopy.isNotEmpty()) {
            val edit = history.edit()
            toCopy.forEach { edit.putString(it, oldAll[it] as String) }
            check(edit.commit()) { "couldn't migrate history" }
        }
        val toRemove = KEYS.filter { key -> key in oldAll && history.contains(key) }
        if (toRemove.isNotEmpty()) {
            val edit = old.edit()
            toRemove.forEach { edit.remove(it) }
            edit.commit()
        }
    }
}

/**
 * Coalesces the history stores' disk writes. [put] queues the latest value for a key (the value is only built when
 * it's written, so a burst of changes serialises once); at most [delayMs] later everything queued is written in one
 * batched edit per file. [flush] writes at once (the container does so whenever an activity stops), [cancel] drops
 * what's queued (after a restore, so stale in-memory data can't overwrite the restored file). With a null [scope]
 * every [put] writes immediately, as the stores did before.
 *
 * A pending write is lost only if the process dies within [delayMs] of the change without an activity having stopped.
 */
class DeferredWrites(private val scope: CoroutineScope? = null, private val delayMs: Long = 750) {
    private val lock = Any()
    private val pending = LinkedHashMap<Pair<SharedPreferences, String>, () -> String?>()
    private var job: Job? = null

    /** Queues [value] for [key] in [prefs]; a null result removes the key. */
    fun put(prefs: SharedPreferences, key: String, value: () -> String?) {
        if (scope == null) {
            write(listOf(Triple(prefs, key, value)))
            return
        }
        synchronized(lock) {
            pending[prefs to key] = value
            if (job == null) job = scope.launch { delay(delayMs); flush() }
        }
    }

    fun flush() {
        val batch = synchronized(lock) {
            job?.cancel()
            job = null
            pending.map { (k, v) -> Triple(k.first, k.second, v) }.also { pending.clear() }
        }
        write(batch)
    }

    fun cancel() {
        synchronized(lock) {
            job?.cancel()
            job = null
            pending.clear()
        }
    }

    private fun write(batch: List<Triple<SharedPreferences, String, () -> String?>>) {
        batch.groupBy { it.first }.forEach { (prefs, items) ->
            val edit = prefs.edit()
            items.forEach { (_, key, value) -> value()?.let { edit.putString(key, it) } ?: edit.remove(key) }
            edit.apply()
        }
    }
}
