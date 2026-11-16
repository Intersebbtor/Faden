package io.github.radixtrator.resync.data

import android.content.SharedPreferences

/**
 * The accounts' tokens and the logged-out device ID ([AccountStore]'s "auth." keys) live in their own preferences
 * file, so that file alone can be left out when Android copies an app's data to a new phone (see
 * res/xml/data_extraction_rules.xml): settings move, logins don't. Key names are unchanged from when they shared the
 * "resync" file.
 */
object AuthPrefs {
    const val FILE = "faden_auth"
    private const val PREFIX = "auth."

    fun isAuthKey(key: String): Boolean = key.startsWith(PREFIX)

    /**
     * One-time, idempotent move of every "auth." key from [old] to [auth]. A key is copied only if [auth] lacks it,
     * and the copy is committed before anything is removed from [old], so a crash between the two steps just repeats
     * the removal next launch. Afterwards [old] holds no "auth." keys at all, so no token is left behind in the file
     * that still transfers. A no-op on a fresh install or when already migrated.
     */
    fun migrate(old: SharedPreferences, auth: SharedPreferences) {
        if (old === auth) return
        val oldAuth = old.all.filterKeys(::isAuthKey)
        if (oldAuth.isEmpty()) return
        val toCopy = oldAuth.filterKeys { !auth.contains(it) }
        if (toCopy.isNotEmpty()) {
            val edit = auth.edit()
            toCopy.forEach { (key, value) ->
                when (value) {
                    is String -> edit.putString(key, value)
                    is Long -> edit.putLong(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Int -> edit.putInt(key, value)
                    else -> {} // AccountStore stores nothing else
                }
            }
            check(edit.commit()) { "couldn't migrate accounts" }
        }
        val edit = old.edit()
        oldAuth.keys.forEach { edit.remove(it) }
        edit.commit()
    }
}
