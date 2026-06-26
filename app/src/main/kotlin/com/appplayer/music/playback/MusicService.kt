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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    @Inject lateinit var settingsManager: com.appplayer.music.utils.SettingsManager

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
        startCrossfadeMonitor()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaSession

    override fun onDestroy() {
        crossfadeJob?.cancel()
        com.appplayer.music.utils.EqualizerManager.getInstance(this).release()
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
        com.appplayer.music.utils.EqualizerManager.getInstance(this).initEqualizer(player.audioSessionId)
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        super.onAudioSessionIdChanged(audioSessionId)
        Timber.d("Audio session ID changed: $audioSessionId")
        com.appplayer.music.utils.EqualizerManager.getInstance(this).initEqualizer(audioSessionId)
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
            val playbackData = kotlinx.coroutines.runBlocking {
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
            } else {
                // Retry failed as well. Toast and move to next
                val trackTitle = player.currentMediaItem?.mediaMetadata?.title ?: "Track"
                android.widget.Toast.makeText(
                    this,
                    "\"$trackTitle\" is unavailable, skipping...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                if (player.hasNextMediaItem()) {
                    Timber.i("Skipping to next media item because $currentMediaId failed to play.")
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } else {
                    Timber.w("No next media item to skip to. Stopping playback.")
                    player.stop()
                }
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

    private var crossfadeJob: Job? = null

    private fun startCrossfadeMonitor() {
        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch {
            var currentTrackId: String? = null
            var currentAnalysis: com.appplayer.music.utils.SongAnalysis? = null
            var isFadingIn = false
            var fadeInDuration = 2000L
            var fadeInStart = 0L

            while (isActive) {
                delay(200)
                
                if (!player.isPlaying) continue

                val mediaItem = player.currentMediaItem ?: continue
                val videoId = mediaItem.mediaId
                val position = player.currentPosition
                val duration = player.duration

                // 1. If song changed, load analysis
                if (videoId != currentTrackId) {
                    currentTrackId = videoId
                    currentAnalysis = null
                    isFadingIn = false
                    
                    if (settingsManager.getSmartCrossfade() && duration > 0) {
                        try {
                            val analysis = com.appplayer.music.utils.SongAnalyzer.analyzeSong(this@MusicService, videoId, duration)
                            currentAnalysis = analysis
                            
                            // Check if we should skip intro silence
                            if (analysis.introStart > 0 && position < analysis.introStart) {
                                player.seekTo(analysis.introStart)
                            }
                            
                            // Initialize fade in
                            isFadingIn = true
                            fadeInStart = analysis.introStart
                            fadeInDuration = (analysis.introEnd - analysis.introStart).coerceIn(1000L, 5000L)
                            
                            Timber.d("Crossfade: loaded analysis for $videoId (Intro: ${analysis.introStart} to ${analysis.introEnd}, Outro starts at ${analysis.outroStart})")
                        } catch (e: Exception) {
                            Timber.e(e, "Error loading song analysis")
                        }
                    }
                }

                val analysis = currentAnalysis
                val isSmart = settingsManager.getSmartCrossfade() && analysis != null && !isGaplessTrack(mediaItem)

                if (isSmart && analysis != null) {
                    val targetVolume = if (settingsManager.getLoudnessMatching()) {
                        val dbDiff = -14.0 - analysis.averageLoudness
                        Math.pow(10.0, dbDiff / 20.0).toFloat().coerceIn(0.2f, 1.0f)
                    } else {
                        1.0f
                    }

                    if (isFadingIn) {
                        val elapsed = position - fadeInStart
                        if (elapsed >= fadeInDuration || elapsed < 0) {
                            isFadingIn = false
                            player.volume = targetVolume
                        } else {
                            val progress = elapsed.toFloat() / fadeInDuration
                            val volumeFactor = kotlin.math.sin(progress * (Math.PI / 2)).toFloat()
                            player.volume = targetVolume * volumeFactor
                        }
                    } else if (position >= analysis.outroStart) {
                        // Preload next track
                        if (player.hasNextMediaItem()) {
                            val nextIndex = player.nextMediaItemIndex
                            val nextItem = player.getMediaItemAt(nextIndex)
                            val nextVideoId = nextItem.mediaId
                            if (!songUrlCache.containsKey(nextVideoId)) {
                                serviceScope.launch(Dispatchers.IO) {
                                    try {
                                        ytPlayerUtils.playerResponseForPlayback(nextVideoId, connectivityManager).getOrNull()?.let { data ->
                                            val expiryMs = System.currentTimeMillis() + (data.streamExpiresInSeconds * 1000L)
                                            songUrlCache[nextVideoId] = data.streamUrl to expiryMs
                                            songClientMap[nextVideoId] = data.streamClient
                                            Timber.d("Crossfade: Preloaded next song stream URL for $nextVideoId")
                                        }
                                    } catch (e: Exception) {
                                        Timber.w(e, "Crossfade: Failed to preload next song $nextVideoId")
                                    }
                                }
                            }
                        }

                        // Fade out
                        val outroDuration = (analysis.outroEnd - analysis.outroStart).coerceIn(2000L, 10000L)
                        val elapsed = position - analysis.outroStart
                        if (elapsed >= outroDuration) {
                            player.volume = 0f
                            // Transition to next song
                            if (player.hasNextMediaItem()) {
                                player.seekToNextMediaItem()
                            } else {
                                player.pause()
                            }
                        } else {
                            val progress = (elapsed.toFloat() / outroDuration).coerceIn(0f, 1f)
                            val volumeFactor = kotlin.math.cos(progress * (Math.PI / 2)).toFloat()
                            player.volume = targetVolume * volumeFactor
                        }
                    } else {
                        // Normal playback: target volume
                        player.volume = targetVolume
                    }
                } else {
                    // Fallback to normal volume
                    player.volume = 1.0f
                }
            }
        }
    }

    private fun isGaplessTrack(mediaItem: MediaItem): Boolean {
        if (!settingsManager.getGaplessPlayback()) return false
        val title = mediaItem.mediaMetadata.title?.toString()?.lowercase() ?: ""
        val artist = mediaItem.mediaMetadata.artist?.toString()?.lowercase() ?: ""
        return title.contains("live") || title.contains("mix") || title.contains("nonstop") || 
               title.contains("non-stop") || title.contains("continuous") || title.contains("session") ||
               artist.contains("dj") || artist.contains("mix")
    }

    companion object {
        const val CHANNEL_ID = "music_playback"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "MusicService"
    }
}
