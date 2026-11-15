package io.github.radixtrator.resync.ui.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import io.github.radixtrator.resync.data.Http
import java.io.File

/** The video player's network stack: the app's OkHttp client behind one app-wide on-disk cache (LRU, 100 MB). */
@OptIn(UnstableApi::class)
internal object VideoCache {
    private const val MAX_BYTES = 100L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    // A SimpleCache must be the only one on its folder, so it's made once and never released.
    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "video"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }

    /** [userAgent] replaces OkHttp's own for hosts that check it (RedGifs). */
    fun dataSourceFactory(context: Context, userAgent: String?): DataSource.Factory {
        val upstream = OkHttpDataSource.Factory(Http.base).apply { if (userAgent != null) setUserAgent(userAgent) }
        return CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
