package com.appplayer.music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.appplayer.music.MainActivity
import com.appplayer.music.R
import com.appplayer.music.di.DownloadCache
import com.appplayer.music.di.PlayerCache
import com.appplayer.music.utils.YTPlayerUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService : MediaLibraryService(), Player.Listener {

    @Inject @PlayerCache lateinit var playerCache: SimpleCache
    @Inject @DownloadCache lateinit var downloadCache: SimpleCache
    @Inject lateinit var ytPlayerUtils: YTPlayerUtils

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var connectivityManager: ConnectivityManager

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // In-memory URL cache: videoId → (streamUrl, expiryMs)
    private val songUrlCache = object : LinkedHashMap<String, Pair<String, Long>>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Pair<String, Long>>): Boolean =
            size > 200
    }

    private val songClientMap = ConcurrentHashMap<String, String>()
    private var lastFailedMediaId: String? = null

    private val mediaOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                val isYouTubeHost = host.endsWith("googlevideo.com") ||
                    host.endsWith("googleusercontent.com") ||
                    host.endsWith("youtube.com")

                if (!isYouTubeHost) {
                    chain.proceed(request)
                } else {
                    // Apply matching headers per client type embedded in URL params
                    val clientParam = request.url.queryParameter("c")
                    val userAgent = when {
                        clientParam?.startsWith("ANDROID") == true ->
                            "com.google.android.apps.youtube.music/7.16.52 (Linux; U; Android 13; en_US) gzip"
                        clientParam?.startsWith("IOS") == true ->
                            "com.google.ios.youtubemusic/7.08.3 like iPhone"
                        else ->
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
                    }
                    val builder = request.newBuilder().header("User-Agent", userAgent)
                    if (clientParam?.startsWith("WEB") == true || clientParam == null) {
                        builder
                            .header("Origin", "https://music.youtube.com")
                            .header("Referer", "https://music.youtube.com/")
                    }
                    chain.proceed(builder.build())
                }
            }
            .build()
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService()!!
        ensureNotificationChannel()
        startForegroundCompat()
        createPlayer()
        createMediaSession()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaSession

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    // ─── Player Setup ─────────────────────────────────────────────────────────

    private fun createPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        30_000,   // minBuffer
                        120_000,  // maxBuffer
                        2_500,    // bufferForPlayback
                        5_000     // bufferForPlaybackAfterRebuffer
                    )
                    .build()
            )
            .build()

        player.addListener(this)
    }

    private fun createMediaSession() {
        mediaSession = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {})
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    // ─── ResolvingDataSource ──────────────────────────────────────────────────

    /**
     * This is the heart of the playback engine.
     *
     * ExoPlayer media items use videoId as URI. This factory intercepts
     * each data request and resolves the real googlevideo.com stream URL
     * lazily just before playback, using YTPlayerUtils which implements
     * the full multi-client fallback strategy from ArchiveTune.
     */
    private fun createDataSourceFactory(): ResolvingDataSource.Factory {
        // Base: OkHttp data source with our YouTube-aware client
        val httpDataSourceFactory = OkHttpDataSource.Factory(mediaOkHttpClient)
            .setDefaultRequestProperties(
                mapOf("Accept-Encoding" to "identity")
            )

        // Download cache → player cache → network
        val cacheDataSource = CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(downloadCache)
                    .setUpstreamDataSourceFactory(httpDataSourceFactory)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ResolvingDataSource.Factory(cacheDataSource) { dataSpec ->
            val mediaId = dataSpec.key
                ?: dataSpec.uri.getQueryParameter("v")
                ?: error("No media id in DataSpec (key=null, uri=${dataSpec.uri})")

            // 1. Return cached URL if still valid
            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let { (url, _) ->
                Timber.d("STREAM CACHE HIT: $mediaId")
                return@Factory dataSpec.withUri(url.toUri())
            }

            Timber.d("RESOLVING STREAM: $mediaId")

            // 2. Resolve fresh stream URL via InnerTube
            val playbackData = runBlocking(Dispatchers.IO) {
                ytPlayerUtils.playerResponseForPlayback(
                    videoId = mediaId,
                    connectivityManager = connectivityManager
                )
            }.getOrElse { throwable ->
                Timber.e(throwable, "Stream resolution failed for $mediaId")
                when (throwable) {
                    is PlaybackException -> throw throwable
                    else -> throw PlaybackException(
                        throwable.message ?: "Stream resolution failed",
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }

            // 3. Cache the resolved URL until it expires
            val expiryMs = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L)
            songUrlCache[mediaId] = playbackData.streamUrl to expiryMs
            songClientMap[mediaId] = playbackData.streamClient

            Timber.d("STREAM RESOLVED: $mediaId → client=${playbackData.streamClient} expires=${playbackData.streamExpiresInSeconds}s")

            dataSpec.withUri(playbackData.streamUrl.toUri())
        }
    }

    private fun createMediaSourceFactory() = DefaultMediaSourceFactory(
        createDataSourceFactory()
    )

    // ─── Player Listener ─────────────────────────────────────────────────────

    override fun onPlayerError(error: PlaybackException) {
        Timber.e(error, "ExoPlayer error: ${error.errorCodeName}")
        val currentMediaId = player.currentMediaItem?.mediaId
        if (currentMediaId != null) {
            val client = songClientMap.remove(currentMediaId)
            if (client != null) {
                Timber.w("Playback failed on client $client for $currentMediaId. Blocking client.")
                ytPlayerUtils.blockClient(client)
            }
            invalidateStreamCache(currentMediaId)
            if (lastFailedMediaId != currentMediaId) {
                lastFailedMediaId = currentMediaId
                Timber.w("Retrying playback for $currentMediaId with fresh resolution...")
                player.prepare()
                player.play()
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        lastFailedMediaId = null
    }

    // ─── Foreground Service ───────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background music playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat() {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Ready to play")
            .setContentIntent(contentIntent)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ─── Public API for UI ────────────────────────────────────────────────────

    fun playTrack(videoId: String, title: String, artist: String, artworkUri: String? = null) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(videoId)
            .setCustomCacheKey(videoId)
            // URI is the videoId — ResolvingDataSource resolves the real URL
            .setUri("https://music.youtube.com/watch?v=$videoId")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUri?.toUri())
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun playQueue(items: List<Triple<String, String, String>>, startIndex: Int = 0) {
        val mediaItems = items.map { (videoId, title, artist) ->
            MediaItem.Builder()
                .setMediaId(videoId)
                .setCustomCacheKey(videoId)
                .setUri("https://music.youtube.com/watch?v=$videoId")
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems, startIndex, 0)
        player.prepare()
        player.play()
    }

    fun invalidateStreamCache(videoId: String) {
        songUrlCache.remove(videoId)
    }

    companion object {
        const val CHANNEL_ID = "music_playback"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "MusicService"
    }
}
