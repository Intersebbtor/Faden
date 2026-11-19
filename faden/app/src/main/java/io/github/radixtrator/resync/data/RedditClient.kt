package io.github.radixtrator.resync.data

import android.content.res.AssetManager
import android.util.Log
import io.github.radixtrator.resync.BuildConfig
import io.github.radixtrator.resync.i18n.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class RedditException(message: String, val code: Int = 0) : IOException(message)

/**
 * OAuth + HTTP for Reddit's API.
 *
 * Logged in: authorization_code -> refresh_token -> short-lived bearer tokens.
 * Logged out: app-only tokens from the "installed_client" grant. Both go to oauth.reddit.com.
 */
class RedditClient(
    private val configStore: ApiConfigStore,
    private val tokens: AccountStore,
    private val assets: AssetManager,
) {
    // Lazy (and synchronized by default): AppContainer is built on the main thread in Application.onCreate.
    private val http by lazy {
        Http.base.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply { if (BuildConfig.DEBUG) addInterceptor(::logExchange) }
            .build()
    }
    private val tokenLock = Mutex()

    private val config: ApiConfig
        get() = configStore.load() ?: throw RedditException(t("Reddit API isn't set up yet"))

    val redirectUri: String get() = config.redirectUri

    fun authorizeUrl(state: String): String =
        "https://www.reddit.com/api/v1/authorize.compact".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", config.clientId)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("state", state)
            .addQueryParameter("redirect_uri", config.redirectUri)
            .addQueryParameter("duration", "permanent")
            .addQueryParameter("scope", SCOPES)
            .build()
            .toString()

    /** Exchanges the code from the login redirect for tokens, saves the account and makes it active. */
    suspend fun completeLogin(code: String): String {
        val response = tokenRequest(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to config.redirectUri,
        )
        val refresh = response.string("refresh_token")
            ?: throw RedditException(t("Reddit didn't return a refresh token"))
        val access = storeAccessToken(response)
        // Ask who this is with the new token directly; the active account isn't switched yet.
        val me = withContext(Dispatchers.IO) {
            val request = Request.Builder().url("$API_BASE/api/v1/me")
                .header("User-Agent", config.userAgent)
                .header("Authorization", "bearer $access")
                .build()
            http.newCall(request).execute().use { it.body.string() }
        }
        val name = runCatching { JSON.parseToJsonElement(me).jsonObject.string("name") }.getOrNull()
        if (name == null) {
            tokens.clearSession()
            throw RedditException(t("Couldn't read the account name"))
        }
        tokens.upsert(Account(name, refresh))
        tokens.activeUsername = name
        return name
    }

    suspend fun get(path: String, params: Map<String, String?> = emptyMap()): JsonElement {
        if (configStore.demoMode) return demoResponse(path, isNextPage = params["after"] != null, focus = params["comment"])
        val url = apiUrl(path).newBuilder().apply {
            params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
        return execute(Request.Builder().url(url).get())
    }

    /**
     * [path] on the API with raw_json=1, on every method: without it Reddit HTML-escapes <, > and & in every string it
     * returns, so the comment /api/comment and /api/editusertext send back would show its body_html as literal tags.
     */
    private fun apiUrl(path: String) = "$API_BASE$path".toHttpUrl().newBuilder().addQueryParameter("raw_json", "1").build()

    suspend fun post(path: String, form: Map<String, String>): JsonElement {
        if (configStore.demoMode) return DemoWrites.respond(path, form)
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(apiUrl(path)).post(body))
    }

    /** A JSON body instead of a form: /api/submit_gallery_post.json takes one. */
    suspend fun postJson(path: String, body: JsonObject): JsonElement {
        if (configStore.demoMode) return DemoWrites.respond(path, emptyMap())
        return execute(Request.Builder().url(apiUrl(path)).post(body.toString().toRequestBody("application/json".toMediaType())))
    }

    /**
     * Uploads an image to Reddit's media storage (lease from /api/media/asset.json, then a multipart POST to the
     * returned S3 form) and returns the hosted URL a "kind=image" submission takes.
     */
    suspend fun uploadImage(filename: String, mime: String, bytes: ByteArray): String = uploadMedia(filename, mime, bytes).url

    /** [uploadImage], also giving the asset id that a gallery post's items name. */
    suspend fun uploadMedia(filename: String, mime: String, bytes: ByteArray): UploadedMedia {
        val lease = post("/api/media/asset.json", mapOf("filepath" to filename, "mimetype" to mime)).jsonObject
        val args = lease.obj("args") ?: throw RedditException(t("Reddit didn't offer an upload"))
        val action = args.string("action") ?: throw RedditException(t("Reddit didn't offer an upload"))
        val uploadUrl = if (action.startsWith("//")) "https:$action" else action
        val fields = args.arr("fields").orEmpty().mapNotNull { it as? JsonObject }
        val form = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM).apply {
            fields.forEach { f -> addFormDataPart(f.string("name").orEmpty(), f.string("value").orEmpty()) }
            // S3 wants the file last.
            addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType()))
        }.build()
        val key = fields.firstOrNull { it.string("name") == "key" }?.string("value") ?: throw RedditException(t("Reddit's upload form had no key"))
        val code = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(uploadUrl).post(form).build()).execute().use { it.code }
        }
        if (code !in 200..299) throw RedditException(t("Image upload failed ({0})", code), code)
        return UploadedMedia(uploadUrl.trimEnd('/') + "/" + key, lease.obj("asset")?.string("asset_id"))
    }

    suspend fun put(path: String, form: Map<String, String>): JsonElement {
        if (configStore.demoMode) return JsonObject(emptyMap())
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(apiUrl(path)).put(body))
    }

    suspend fun delete(path: String): JsonElement {
        if (configStore.demoMode) return JsonObject(emptyMap())
        return execute(Request.Builder().url(apiUrl(path)).delete())
    }

    private suspend fun execute(builder: Request.Builder): JsonElement {
        var token = accessToken(forceRefresh = false)
        repeat(2) { attempt ->
            val request = builder
                .header("User-Agent", config.userAgent)
                .header("Authorization", "bearer $token")
                .build()
            // Parsed off the main thread as well: a big comment thread is megabytes of JSON.
            val (code, json) = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    // 202 and 204 carry no JSON: /api/read_all_messages answers 202 with an HTML "Accepted" page.
                    val ok = response.code in 200..299 && response.code != 202 && response.code != 204
                    response.code to if (ok) decodeBody(response) else null
                }
            }
            if (code == 401 && attempt == 0) {
                token = accessToken(forceRefresh = true)
                return@repeat
            }
            if (code !in 200..299) throw RedditException(httpError(code), code)
            return json ?: JsonObject(emptyMap())
        }
        throw RedditException(httpError(401), 401)
    }

    /** Decodes straight from the stream (no multi-MB String); null for an empty or blank body. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun decodeBody(response: Response): JsonElement? {
        val source = response.body.source()
        while (!source.exhausted() && source.peek().readByte().toInt().toChar().isWhitespace()) source.skip(1)
        if (source.exhausted()) return null
        return JSON.decodeFromStream(JsonElement.serializer(), source.inputStream())
    }

    private suspend fun accessToken(forceRefresh: Boolean): String = tokenLock.withLock {
        val cached = tokens.accessToken
        if (!forceRefresh && cached != null && System.currentTimeMillis() < tokens.accessExpiresAt - 60_000) {
            return cached
        }
        val refresh = tokens.active?.refreshToken
        val response = if (refresh != null) {
            tokenRequest("grant_type" to "refresh_token", "refresh_token" to refresh)
        } else {
            tokenRequest("grant_type" to INSTALLED_CLIENT_GRANT, "device_id" to tokens.deviceId)
        }
        storeAccessToken(response)
    }

    private fun storeAccessToken(response: JsonObject): String {
        val token = response.string("access_token") ?: throw RedditException(t("Reddit didn't return an access token"))
        val expiresIn = response.long("expires_in") ?: 3600
        tokens.accessToken = token
        tokens.accessExpiresAt = System.currentTimeMillis() + expiresIn * 1000
        return token
    }

    private suspend fun tokenRequest(vararg fields: Pair<String, String>): JsonObject {
        val cfg = config
        val request = Request.Builder()
            .url("https://www.reddit.com/api/v1/access_token")
            .header("User-Agent", cfg.userAgent)
            // Installed apps have no secret: "client_id:".
            .header("Authorization", Credentials.basic(cfg.clientId, ""))
            .post(FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build())
            .build()
        val (code, body) = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { it.code to it.body.string() }
        }
        val json = runCatching { JSON.parseToJsonElement(body).jsonObject }.getOrNull()
        json?.string("error")?.let { throw RedditException(tokenError(it), code) }
        if (code !in 200..299 || json == null) throw RedditException(tokenError("http $code"), code)
        return json
    }

    private suspend fun demoResponse(path: String, isNextPage: Boolean, focus: String?): JsonElement = withContext(Dispatchers.IO) {
        if (isNextPage) return@withContext JSON.parseToJsonElement("""{"kind":"Listing","data":{"after":null,"children":[]}}""")
        // A page focused on one comment (what "load more" reads) is demo/comment_<id>.json where there is one.
        val focused = focus?.let { "demo/comment_$it.json" }?.takeIf { name -> runCatching { assets.open(name).close() }.isSuccess }
        val file = when {
            focused != null -> focused
            path.startsWith("/comments/") -> "demo/comments.json"
            path.startsWith("/api/v1/me") -> "demo/me.json"
            path.startsWith("/subreddits/mine") -> "demo/subreddits.json"
            path.startsWith("/subreddits/search") || path.startsWith("/subreddits/popular") -> "demo/subreddit_search.json"
            path.matches(Regex("/r/[^/]+/about")) -> "demo/subreddit_about.json"
            path.matches(Regex("/user/[^/]+/about")) -> "demo/user_about.json"
            path.startsWith("/user/") -> "demo/user_overview.json"
            path.startsWith("/message/") -> "demo/inbox.json"
            else -> "demo/listing.json"
        }
        val text = runCatching { assets.open(file).bufferedReader().use { it.readText() } }
            .getOrElse { throw RedditException(t("Demo data is only bundled in debug builds")) }
        JSON.parseToJsonElement(text)
    }

    /** Debug builds log each exchange and the start of any error body; headers (and so tokens) never. */
    private fun logExchange(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val line = "${request.method} ${request.url.encodedPath} -> ${response.code}"
        if (response.isSuccessful) Log.d(TAG, line) else Log.w(TAG, "$line ${response.peekBody(500).string()}")
        return response
    }

    private fun httpError(code: Int) = when (code) {
        401 -> t("Reddit rejected the login (401). Try logging in again.")
        403 -> t("Reddit refused the request (403). It may be private, quarantined, or blocked for this client ID.")
        404 -> t("Not found (404).")
        429 -> t("Rate limited by Reddit (429). Slow down for a minute.")
        in 500..599 -> t("Reddit is having trouble ({0}).", code)
        else -> t("Request failed ({0}).", code)
    }

    private fun tokenError(error: String) = when (error) {
        "invalid_grant" -> t("Login expired or was revoked. Log in again.")
        "unauthorized_client", "invalid_client", "http 401" ->
            t("Reddit rejected the client ID. Check it in Settings.")
        else -> t("Couldn't get a Reddit token ({0}).", error)
    }

    companion object {
        private const val TAG = "FadenHttp"
        private const val API_BASE = "https://oauth.reddit.com"
        private const val INSTALLED_CLIENT_GRANT = "https://oauth.reddit.com/grants/installed_client"
        private const val SCOPES =
            "account,identity,edit,flair,history,mysubreddits,privatemessages,read,report,save,submit,subscribe,vote,wikiread,modposts,modflair,modmail"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

/** An image uploaded to Reddit: its hosted [url], and the [assetId] a gallery post refers to it by. */
data class UploadedMedia(val url: String, val assetId: String?)
