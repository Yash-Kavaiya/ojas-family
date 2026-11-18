package com.ojashealing.family.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * One 200 MB disk cache for announcement, post and chat audio/video, so a clip played again does
 * not download again (the oldest clips are dropped first). Guided practices don't use it: they
 * are downloaded whole (see PracticeDownloads).
 */
@OptIn(UnstableApi::class)
object MediaCache {
    private const val MAX_BYTES = 200L * 1024 * 1024
    @Volatile private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            context.cacheDir.resolve("media"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    /** A player whose network reads go through the cache. */
    fun player(context: Context): ExoPlayer {
        val app = context.applicationContext
        val source = CacheDataSource.Factory()
            .setCache(cache(app))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(app))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return ExoPlayer.Builder(app).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build()
    }
}
