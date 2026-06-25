package com.appplayer.music.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    @PlayerCache
    fun providePlayerCache(@ApplicationContext context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "player")
        val evictor = LeastRecentlyUsedCacheEvictor(
            512L * 1024L * 1024L // 512 MB player cache
        )
        val dbProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, evictor, dbProvider)
    }

    @Provides
    @Singleton
    @DownloadCache
    fun provideDownloadCache(@ApplicationContext context: Context): SimpleCache {
        val cacheDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
        cacheDir.mkdirs()
        val dbProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(
            2048L * 1024L * 1024L // 2 GB download cache
        ), dbProvider)
    }
}
