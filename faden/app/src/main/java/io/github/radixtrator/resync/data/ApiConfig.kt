package io.github.radixtrator.resync.data

import android.content.SharedPreferences
import androidx.core.content.edit

/** The three values Reddit needs to identify an OAuth client. Entered by the user on first launch. */
data class ApiConfig(
    val clientId: String,
    val redirectUri: String,
    val userAgent: String,
) {
    companion object {
        // RedReader's registered values; its client ID comes from Reddit's "you authorized an app" email.
        const val DEFAULT_REDIRECT_URI = "redreader://rr_oauth_redir"
        const val DEFAULT_USER_AGENT = "org.quantumbadger.redreader/1.25.1"

        private val CLIENT_ID = Regex("[A-Za-z0-9_-]{10,40}")

        /** Accepts the bare ID or the whole "App ID: xxxx" line from Reddit's email. */
        fun parseClientId(raw: String): String? =
            raw.trim().split(Regex("\\s+")).lastOrNull()?.takeIf { CLIENT_ID.matches(it) }

        private val REDIRECT_URI = Regex("""[A-Za-z][A-Za-z0-9+.-]*:[^\s\p{Cntrl}]+""")

        /** A bare client ID, as Setup saves it (no "App ID:" prefix, nothing around it). */
        fun isValidClientId(value: String): Boolean = CLIENT_ID.matches(value)

        /** A URI with a scheme and no spaces or control characters. */
        fun isValidRedirectUri(value: String): Boolean = value.length <= MAX_LENGTH && REDIRECT_URI.matches(value)

        /** Printable ASCII only: OkHttp refuses anything else in a header, which would fail every request. */
        fun isValidUserAgent(value: String): Boolean =
            value.isNotBlank() && value.length <= MAX_LENGTH && value.all { it in ' '..'~' }

        private const val MAX_LENGTH = 256
    }
}

class ApiConfigStore(private val prefs: SharedPreferences) {

    fun load(): ApiConfig? {
        val id = prefs.getString(KEY_CLIENT_ID, null) ?: return null
        return ApiConfig(
            clientId = id,
            redirectUri = prefs.getString(KEY_REDIRECT_URI, null) ?: ApiConfig.DEFAULT_REDIRECT_URI,
            userAgent = prefs.getString(KEY_USER_AGENT, null) ?: ApiConfig.DEFAULT_USER_AGENT,
        )
    }

    fun save(config: ApiConfig) = prefs.edit {
        putString(KEY_CLIENT_ID, config.clientId)
        putString(KEY_REDIRECT_URI, config.redirectUri)
        putString(KEY_USER_AGENT, config.userAgent)
    }

    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) = prefs.edit { putBoolean(KEY_DEMO, value) }

    private companion object {
        const val KEY_CLIENT_ID = "api.client_id"
        const val KEY_REDIRECT_URI = "api.redirect_uri"
        const val KEY_USER_AGENT = "api.user_agent"
        const val KEY_DEMO = "api.demo"
    }
}
