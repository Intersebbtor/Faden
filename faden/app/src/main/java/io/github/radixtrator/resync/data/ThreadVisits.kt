package io.github.radixtrator.resync.data

import android.content.SharedPreferences

/**
 * When each thread's comments last loaded (epoch seconds), so comments posted since can be marked new.
 * Local only, kept apart from "Remember comment position" so turning that off leaves this working; keeps the
 * [MAX] most recent threads.
 */
class ThreadVisits(private val prefs: SharedPreferences, private val writes: DeferredWrites = DeferredWrites()) {
    private val seen: LinkedHashMap<String, Long> = load()

    operator fun get(postId: String): Long? = synchronized(seen) { seen[postId] }

    fun record(postId: String, epochSeconds: Long) {
        synchronized(seen) {
            seen.remove(postId)
            seen[postId] = epochSeconds // re-inserted, so it's the most recent
            while (seen.size > MAX) seen.remove(seen.keys.first())
        }
        writes.put(prefs, KEY) { synchronized(seen) { seen.entries.joinToString(",") { "${it.key}:${it.value}" } } }
    }

    private fun load(): LinkedHashMap<String, Long> {
        val out = LinkedHashMap<String, Long>()
        prefs.getString(KEY, null)?.split(',')?.forEach { pair ->
            val id = pair.substringBefore(':', "")
            val time = pair.substringAfter(':', "").toLongOrNull()
            if (id.isNotEmpty() && time != null) out[id] = time
        }
        return out
    }

    private companion object {
        const val KEY = "thread_visits.v1"
        const val MAX = 1000
    }
}
