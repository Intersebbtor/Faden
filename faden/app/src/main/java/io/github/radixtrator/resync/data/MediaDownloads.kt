package io.github.radixtrator.resync.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.radixtrator.resync.BuildConfig
import io.github.radixtrator.resync.i18n.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

private val REDDIT_VIDEO = Regex("""^https?://v\.redd\.it/([a-z0-9]+)""", RegexOption.IGNORE_CASE)
private val VIDEO_FILE = Regex("""\.(mp4|webm|mov)(\?|$)""", RegexOption.IGNORE_CASE)

/** The cache folder shared files are served from; res/xml/shared_files.xml exposes it to [FileProvider]. */
private const val SHARED_DIR = "shared"

/**
 * Sync's download button: saves images to Pictures/Faden and videos to Movies/Faden. Reddit videos
 * are separate DASH video and audio files, so those are fetched and muxed into one mp4 first.
 * Saving runs in the background (leaving the viewer doesn't stop it) and reports with toasts. [share] sends the
 * file itself to another app.
 */
class MediaDownloads(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = Http.base.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Faden/${BuildConfig.VERSION_NAME} (Android)").build())
        }
        .build()

    fun save(url: String) {
        toast(t("Downloading…"))
        scope.launch {
            // A folder per save, so two saves of same-named files can't overwrite each other.
            val dir = File(context.cacheDir, "downloads/${System.nanoTime()}")
            val result = runCatching { store(fetchToCache(url, dir)) }
            dir.deleteRecursively()
            withContext(Dispatchers.Main) {
                toast(result.fold({ t("Saved to {0}", it) }, { t("Download failed: {0}", it.message ?: it.javaClass.simpleName) }))
            }
        }
    }

    /**
     * Shares the image or video itself rather than its link: downloads it into the cache (Reddit videos muxed
     * as for [save]) and offers it to other apps through [FileProvider]. Only the latest shared file is kept.
     */
    fun share(url: String) {
        toast(t("Getting the file ready…"))
        scope.launch {
            val dir = File(context.cacheDir, SHARED_DIR).apply { deleteRecursively() }
            val result = runCatching { fetchToCache(url, dir) }
            withContext(Dispatchers.Main) {
                result.fold(
                    { file ->
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file.file)
                        val send = Intent(Intent.ACTION_SEND)
                            .setType(file.mime)
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        send.clipData = ClipData.newRawUri(file.name, uri)
                        context.startActivity(
                            Intent.createChooser(send, null)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                    },
                    { toast(t("Couldn't share the file: {0}", it.message ?: it.javaClass.simpleName)) },
                )
            }
        }
    }

    /** The file's size from a HEAD request, for the info sheet; null when the host doesn't say. */
    suspend fun contentLength(url: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).head().build()).execute().use { it.header("Content-Length")?.toLongOrNull() }
        }.getOrNull()
    }

    /** A downloaded file in the cache, with the name and type it's saved or shared under. */
    private class Fetched(val file: File, val name: String, val mime: String, val video: Boolean)

    /** Downloads [url] into [dir]: a RedGifs or Streamable page as its HD mp4, a Reddit video as one muxed mp4, else the file. */
    private suspend fun fetchToCache(url: String, dir: File): Fetched {
        dir.mkdirs()
        REDDIT_VIDEO.find(url)?.let { return fetchRedditVideo(it.groupValues[1], dir) }
        if (ClipHosts.isHosted(url)) return fetchToCache(ClipHosts.resolve(url).hd, dir)
        return fetch(url).use { response ->
            val type = response.header("Content-Type")?.substringBefore(';')?.trim()
            val video = type?.startsWith("video/") ?: VIDEO_FILE.containsMatchIn(url)
            val mime = type ?: if (video) "video/mp4" else "image/jpeg"
            val name = fileName(url, mime)
            val file = File(dir, name)
            file.outputStream().use { out -> response.body.byteStream().use { it.copyTo(out) } }
            Fetched(file, name, mime, video)
        }
    }

    private fun fetchRedditVideo(id: String, dir: File): Fetched {
        val base = "https://v.redd.it/$id/"
        val manifest = fetch(base + "DASHPlaylist.mpd").use { it.body.string() }
        val tracks = dashTracks(manifest) ?: throw IOException(t("no video in the DASH playlist"))
        val video = File(dir, "$id-video.mp4")
        val audio = tracks.audio?.let { File(dir, "$id-audio.mp4") }
        val merged = File(dir, "$id.mp4")
        try {
            download(base + tracks.video, video)
            if (audio != null) {
                download(base + tracks.audio, audio)
                mux(listOf(video, audio), merged)
            } else {
                video.copyTo(merged, overwrite = true)
            }
            return Fetched(merged, "$id.mp4", "video/mp4", video = true)
        } catch (e: Exception) {
            merged.delete()
            throw e
        } finally {
            video.delete()
            audio?.delete()
        }
    }

    private fun fetch(url: String): Response {
        val response = http.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        return response
    }

    private fun download(url: String, to: File) {
        fetch(url).use { response -> to.outputStream().use { out -> response.body.byteStream().use { it.copyTo(out) } } }
    }

    /** Copies each file's first track into one mp4 (here: DASH video plus DASH audio). */
    private fun mux(sources: List<File>, to: File) {
        val muxer = MediaMuxer(to.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = sources.map { MediaExtractor().apply { setDataSource(it.path); selectTrack(0) } }
        try {
            val formats = extractors.map { it.getTrackFormat(0) }
            val tracks = formats.map { muxer.addTrack(it) }
            muxer.start()
            val capacity = formats.maxOf { if (it.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) it.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0 }
            val buffer = ByteBuffer.allocate(maxOf(capacity, 2 shl 20))
            val info = MediaCodec.BufferInfo()
            extractors.forEachIndexed { i, extractor ->
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val keyFrame = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    info.set(0, size, extractor.sampleTime, if (keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(tracks[i], buffer, info)
                    extractor.advance()
                }
            }
            muxer.stop()
        } finally {
            extractors.forEach { it.release() }
            muxer.release()
        }
    }

    /** Copies [fetched] into the shared Pictures or Movies folder and returns the folder it landed in, for the toast. */
    private fun store(fetched: Fetched): String {
        val name = fetched.name
        val mime = fetched.mime
        val video = fetched.video
        val write: (OutputStream) -> Unit = { out -> fetched.file.inputStream().use { it.copyTo(out) } }
        val folder = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val relative = "$folder/Faden"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: throw IOException(t("couldn't create the file"))
            try {
                resolver.openOutputStream(uri)?.use(write) ?: throw IOException(t("couldn't open the file"))
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            // API 26–28: the caller holds WRITE_EXTERNAL_STORAGE.
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(folder), "Faden").apply { mkdirs() }
            val file = File(dir, name)
            file.outputStream().use(write)
            MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mime), null)
        }
        return relative
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** A v.redd.it DASH manifest's best video file and, if it has sound, its best audio file (names relative to the post's folder). */
data class DashTracks(val video: String, val audio: String?)

private val ADAPTATION_SET = Regex("""<AdaptationSet\b(.*?)</AdaptationSet>""", RegexOption.DOT_MATCHES_ALL)
private val REPRESENTATION = Regex("""<Representation\b([^>]*)>(.*?)</Representation>""", RegexOption.DOT_MATCHES_ALL)
private val BANDWIDTH = Regex("""bandwidth="(\d+)"""")
private val BASE_URL = Regex("""<BaseURL>\s*([^<]+?)\s*</BaseURL>""")
private val AUDIO_SET = Regex("""(contentType|mimeType)="audio""")

fun dashTracks(manifest: String): DashTracks? {
    var video: Pair<Long, String>? = null
    var audio: Pair<Long, String>? = null
    for (set in ADAPTATION_SET.findAll(manifest)) {
        val isAudio = AUDIO_SET.containsMatchIn(set.value)
        for (rep in REPRESENTATION.findAll(set.groupValues[1])) {
            val file = BASE_URL.find(rep.groupValues[2])?.groupValues?.get(1) ?: continue
            val bandwidth = BANDWIDTH.find(rep.groupValues[1])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            if (isAudio) {
                if (audio == null || bandwidth > audio.first) audio = bandwidth to file
            } else if (video == null || bandwidth > video.first) {
                video = bandwidth to file
            }
        }
    }
    return video?.let { DashTracks(it.second, audio?.second) }
}

/** The URL's file name, with an extension that matches [mime] when it has none. */
fun fileName(url: String, mime: String): String {
    // "." or ".." would name a folder, not a file, on Android 9 and older (which save by path).
    val last = url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        .takeUnless { it.isEmpty() || it.all { c -> c == '.' } } ?: "faden"
    if ('.' in last) return last
    val ext = when (mime) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        else -> if (mime.startsWith("video/")) "mp4" else "jpg"
    }
    return "$last.$ext"
}
