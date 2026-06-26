package com.appplayer.music.data.cache

import android.content.Context
import androidx.media3.datasource.cache.SimpleCache
import com.appplayer.music.di.PlayerCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @PlayerCache private val playerCache: SimpleCache
) {
    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        var size = 0L
        val playerDir = File(context.cacheDir, "player")
        if (playerDir.exists()) {
            size += getFolderSize(playerDir)
        }
        size
    }

    suspend fun clearPlayerCache() = withContext(Dispatchers.IO) {
        try {
            val keys = playerCache.keys.toList()
            for (key in keys) {
                playerCache.removeResource(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun evictOldCacheFiles() = withContext(Dispatchers.IO) {
        val limit = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24 hours ago
        try {
            val keys = playerCache.keys.toList()
            for (key in keys) {
                val spans = playerCache.getCachedSpans(key)
                var allOld = true
                for (span in spans) {
                    val file = span.file
                    if (file != null && file.exists() && file.lastModified() > limit) {
                        allOld = false
                        break
                    }
                }
                if (allOld) {
                    playerCache.removeResource(key)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFolderSize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null) {
                for (child in files) {
                    size += getFolderSize(child)
                }
            }
        } else {
            size += file.length()
        }
        return size
    }
}
