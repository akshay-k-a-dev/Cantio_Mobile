package com.appplayer.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.appplayer.music.data.api.models.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Bridge between the UI and MusicService.
 * Exposes playback state as Compose-friendly StateFlows.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: com.appplayer.music.data.repository.MusicRepository,
    private val innerTube: com.appplayer.innertube.InnerTube
) : Player.Listener {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    // ─── State ────────────────────────────────────────────────────────────────

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<NowPlayingState?>(null)
    val currentTrack: StateFlow<NowPlayingState?> = _currentTrack.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _upcomingQueue = MutableStateFlow<List<Track>>(emptyList())
    val upcomingQueue: StateFlow<List<Track>> = _upcomingQueue.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerTimeLeft = MutableStateFlow(0L)
    val sleepTimerTimeLeft: StateFlow<Long> = _sleepTimerTimeLeft.asStateFlow()

    private var positionUpdateJob: kotlinx.coroutines.Job? = null

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (true) {
                controller?.let { ctrl ->
                    _playbackPosition.value = ctrl.currentPosition.coerceAtLeast(0)
                    _playbackDuration.value = ctrl.duration.coerceAtLeast(0)
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private val queuePrefs = context.getSharedPreferences("cantio_queue_prefs", Context.MODE_PRIVATE)

    private fun saveQueueState() {
        val ctrl = controller ?: return
        val list = mutableListOf<String>()
        for (i in 0 until ctrl.mediaItemCount) {
            val item = ctrl.getMediaItemAt(i)
            val json = org.json.JSONObject().apply {
                put("videoId", item.mediaId)
                put("title", item.mediaMetadata.title?.toString() ?: "")
                put("artist", item.mediaMetadata.artist?.toString() ?: "")
                put("artworkUri", item.mediaMetadata.artworkUri?.toString() ?: "")
            }.toString()
            list.add(json)
        }
        val currentIndex = ctrl.currentMediaItemIndex
        val currentPosition = ctrl.currentPosition

        queuePrefs.edit().apply {
            val jsonArray = org.json.JSONArray()
            list.forEach { jsonArray.put(org.json.JSONObject(it)) }
            putString("queue_json", jsonArray.toString())
            putInt("queue_index", currentIndex)
            putLong("queue_position", currentPosition)
            apply()
        }
        _queue.value = getQueue()
        _upcomingQueue.value = getUpcomingQueue()
    }

    private fun restoreQueueState() {
        val ctrl = controller ?: return
        if (ctrl.mediaItemCount > 0) return // Don't overwrite active queue

        val jsonStr = queuePrefs.getString("queue_json", null) ?: return
        val currentIndex = queuePrefs.getInt("queue_index", 0)
        val currentPosition = queuePrefs.getLong("queue_position", 0L)

        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            val mediaItems = mutableListOf<MediaItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val videoId = obj.getString("videoId")
                val title = obj.getString("title")
                val artist = obj.getString("artist")
                val artworkUri = obj.optString("artworkUri", "")

                mediaItems.add(
                    MediaItem.Builder()
                        .setMediaId(videoId)
                        .setCustomCacheKey(videoId)
                        .setUri("https://music.youtube.com/watch?v=$videoId")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .setArtworkUri(if (artworkUri.isNotEmpty()) android.net.Uri.parse(artworkUri) else null)
                                .build()
                        )
                        .build()
                )
            }
            if (mediaItems.isNotEmpty()) {
                ctrl.setMediaItems(mediaItems, currentIndex, currentPosition)
                ctrl.prepare()
                _queue.value = getQueue()
                _upcomingQueue.value = getUpcomingQueue()
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerConnection", "Failed to restore queue state", e)
        }
    }

    // ─── Connect / Disconnect ─────────────────────────────────────────────────

    fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(this)
            _isConnected.value = true
            
            // Sync initial state
            controller?.let { ctrl ->
                _isPlaying.value = ctrl.isPlaying
                _playbackPosition.value = ctrl.currentPosition.coerceAtLeast(0)
                _playbackDuration.value = ctrl.duration.coerceAtLeast(0)
                _shuffleModeEnabled.value = ctrl.shuffleModeEnabled
                _repeatMode.value = ctrl.repeatMode
                ctrl.currentMediaItem?.let { item ->
                    _currentTrack.value = NowPlayingState(
                        videoId = item.mediaId,
                        title = item.mediaMetadata.title?.toString() ?: "",
                        artist = item.mediaMetadata.artist?.toString() ?: "",
                        artworkUri = item.mediaMetadata.artworkUri?.toString()
                    )
                }
                if (ctrl.isPlaying) {
                    startPositionUpdates()
                } else {
                    restoreQueueState()
                }
                _queue.value = getQueue()
                _upcomingQueue.value = getUpcomingQueue()
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        stopPositionUpdates()
        controller?.removeListener(this)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        _isConnected.value = false
    }

    // ─── Playback Controls ────────────────────────────────────────────────────

    fun play() { controller?.play() }
    fun pause() { controller?.pause() }
    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipToNext() { controller?.seekToNextMediaItem() }
    fun skipToPrevious() { controller?.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
    fun skipToQueueItem(index: Int) { controller?.seekToDefaultPosition(index) }

    fun setShuffleModeEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        _shuffleModeEnabled.value = enabled
    }

    fun toggleShuffleMode() {
        setShuffleModeEnabled(!_shuffleModeEnabled.value)
    }

    fun setRepeatMode(repeatMode: Int) {
        controller?.repeatMode = repeatMode
        _repeatMode.value = repeatMode
    }

    fun toggleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        setRepeatMode(nextMode)
    }

    fun startSleepTimer(durationMinutes: Int) {
        sleepTimerJob?.cancel()
        if (durationMinutes <= 0) {
            _sleepTimerTimeLeft.value = 0L
            return
        }
        sleepTimerJob = scope.launch {
            var timeLeftMs = durationMinutes * 60 * 1000L
            while (timeLeftMs > 0) {
                _sleepTimerTimeLeft.value = timeLeftMs
                kotlinx.coroutines.delay(1000)
                timeLeftMs -= 1000
            }
            _sleepTimerTimeLeft.value = 0L
            pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerTimeLeft.value = 0L
    }

    fun playTrack(track: Track) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.videoId)
            .setCustomCacheKey(track.videoId)
            .setUri("https://music.youtube.com/watch?v=${track.videoId}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.thumbnail?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
        controller?.setMediaItem(mediaItem)
        controller?.prepare()
        controller?.play()
    }

    fun playWithRecommendations(
        track: Track,
        musicRepository: com.appplayer.music.data.repository.MusicRepository
    ) {
        playTrack(track)
        this.scope.launch(Dispatchers.IO) {
            // ── 1. Try YouTube Music /next (InnerTube) for real autoplay queue ──
            val ytTracks = runCatching {
                val response = innerTube.next(
                    client = com.appplayer.innertube.models.YouTubeClient.WEB_REMIX,
                    videoId = track.videoId,
                    playlistId = "RDAMVM${track.videoId}" // YTM radio playlist seed
                )
                val bodyText = response.bodyAsText()
                parseNextResponseTracks(bodyText, currentVideoId = track.videoId)
            }.getOrElse { e ->
                android.util.Log.w("PlayerConnection", "InnerTube next() failed: ${e.message}")
                emptyList()
            }

            val tracksToAdd = if (ytTracks.isNotEmpty()) {
                ytTracks
            } else {
                // ── 2. Fallback: Cantio backend /related ──────────────────────
                when (val res = musicRepository.getRelatedTracks(track.videoId)) {
                    is com.appplayer.music.data.repository.ApiResult.Success -> res.data
                    else -> emptyList()
                }
            }

            if (tracksToAdd.isNotEmpty()) {
                val mediaItems = tracksToAdd.map { t ->
                    MediaItem.Builder()
                        .setMediaId(t.videoId)
                        .setCustomCacheKey(t.videoId)
                        .setUri("https://music.youtube.com/watch?v=${t.videoId}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(t.title)
                                .setArtist(t.artist)
                                .setArtworkUri(t.thumbnail?.let { android.net.Uri.parse(it) })
                                .build()
                        )
                        .build()
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val ctrl = controller
                    if (ctrl != null) {
                        val count = ctrl.mediaItemCount
                        val currentIndex = ctrl.currentMediaItemIndex
                        if (count > currentIndex + 1) {
                            ctrl.removeMediaItems(currentIndex + 1, count)
                        }
                        ctrl.addMediaItems(mediaItems)
                    }
                }
                saveQueueState()
            }
        }
    }

    /**
     * Parses the YouTube Music /next endpoint JSON response to extract
     * the autoplay queue tracks (playlistPanelVideoRenderer items).
     */
    private fun parseNextResponseTracks(json: String, currentVideoId: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val root = org.json.JSONObject(json)
            // Navigate: contents → singleColumnMusicWatchNextResultsRenderer →
            //   tabbedRenderer → watchNextTabbedResultsRenderer → tabs[0] →
            //   tabRenderer → content → musicQueueRenderer → content →
            //   playlistPanelRenderer → contents[]
            val contents = root.optJSONObject("contents") ?: return tracks
            val singleCol = contents.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            val tabbedRenderer = singleCol?.optJSONObject("tabbedRenderer")
            val watchNext = tabbedRenderer?.optJSONObject("watchNextTabbedResultsRenderer")
            val tabs = watchNext?.optJSONArray("tabs") ?: return tracks

            var panelContents: org.json.JSONArray? = null
            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i)
                val tabRenderer = tab?.optJSONObject("tabRenderer") ?: continue
                val content = tabRenderer.optJSONObject("content") ?: continue
                val queueRenderer = content.optJSONObject("musicQueueRenderer") ?: continue
                val queueContent = queueRenderer.optJSONObject("content") ?: continue
                val panelRenderer = queueContent.optJSONObject("playlistPanelRenderer") ?: continue
                panelContents = panelRenderer.optJSONArray("contents")
                break
            }

            panelContents ?: return tracks

            for (i in 0 until panelContents.length()) {
                val item = panelContents.optJSONObject(i) ?: continue
                val videoRenderer = item.optJSONObject("playlistPanelVideoRenderer") ?: continue

                val videoId = videoRenderer.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId") ?: continue

                if (videoId == currentVideoId) continue // skip the currently playing track

                val title = videoRenderer.optJSONObject("title")
                    ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: continue

                val artist = videoRenderer.optJSONObject("shortBylineText")
                    ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: "Unknown Artist"

                val thumbnail = videoRenderer.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                    ?.let { thumbs ->
                        // pick the highest resolution thumbnail
                        var best: String? = null
                        var bestWidth = 0
                        for (j in 0 until thumbs.length()) {
                            val t = thumbs.optJSONObject(j) ?: continue
                            val w = t.optInt("width", 0)
                            if (w >= bestWidth) {
                                bestWidth = w
                                best = t.optString("url")
                            }
                        }
                        best
                    }

                tracks.add(Track(videoId = videoId, title = title, artist = artist, thumbnail = thumbnail))
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerConnection", "parseNextResponseTracks failed: ${e.message}")
        }
        return tracks
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.videoId)
                .setCustomCacheKey(track.videoId)
                .setUri("https://music.youtube.com/watch?v=${track.videoId}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(track.thumbnail?.let { android.net.Uri.parse(it) })
                        .build()
                )
                .build()
        }
        controller?.setMediaItems(mediaItems, startIndex, 0)
        controller?.prepare()
        controller?.play()
    }

    fun addToQueue(track: Track) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.videoId)
            .setCustomCacheKey(track.videoId)
            .setUri("https://music.youtube.com/watch?v=${track.videoId}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build()
            )
            .build()
        val currentCount = controller?.mediaItemCount ?: 0
        controller?.addMediaItem(currentCount, mediaItem)
    }

    fun playNext(track: Track) {
        val ctrl = controller ?: return
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.videoId)
            .setCustomCacheKey(track.videoId)
            .setUri("https://music.youtube.com/watch?v=${track.videoId}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.thumbnail?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
        val nextIndex = if (ctrl.mediaItemCount > 0) ctrl.currentMediaItemIndex + 1 else 0
        ctrl.addMediaItem(nextIndex, mediaItem)
    }

    fun removeTrackAt(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        controller?.moveMediaItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        _currentTrack.value = null
        _queue.value = emptyList()
        _upcomingQueue.value = emptyList()
        saveQueueState()
    }

    fun getUpcomingQueue(): List<Track> {
        val ctrl = controller ?: return emptyList()
        val list = mutableListOf<Track>()
        val start = if (ctrl.mediaItemCount > 0) ctrl.currentMediaItemIndex + 1 else 0
        for (i in start until ctrl.mediaItemCount) {
            val item = ctrl.getMediaItemAt(i)
            list.add(
                Track(
                    videoId = item.mediaId,
                    title = item.mediaMetadata.title?.toString() ?: "",
                    artist = item.mediaMetadata.artist?.toString() ?: "",
                    thumbnail = item.mediaMetadata.artworkUri?.toString()
                )
            )
        }
        return list
    }

    fun removeUpcomingTrackAt(relativeIndex: Int) {
        val ctrl = controller ?: return
        val absoluteIndex = ctrl.currentMediaItemIndex + 1 + relativeIndex
        if (absoluteIndex in 0 until ctrl.mediaItemCount) {
            ctrl.removeMediaItem(absoluteIndex)
        }
    }

    fun moveUpcomingTrack(fromRelative: Int, toRelative: Int) {
        val ctrl = controller ?: return
        val absoluteFrom = ctrl.currentMediaItemIndex + 1 + fromRelative
        val absoluteTo = ctrl.currentMediaItemIndex + 1 + toRelative
        if (absoluteFrom in 0 until ctrl.mediaItemCount && absoluteTo in 0 until ctrl.mediaItemCount) {
            ctrl.moveMediaItem(absoluteFrom, absoluteTo)
        }
    }

    fun skipToUpcomingQueueItem(relativeIndex: Int) {
        val ctrl = controller ?: return
        val absoluteIndex = ctrl.currentMediaItemIndex + 1 + relativeIndex
        if (absoluteIndex in 0 until ctrl.mediaItemCount) {
            ctrl.seekToDefaultPosition(absoluteIndex)
        }
    }

    fun getQueue(): List<Track> {
        val ctrl = controller ?: return emptyList()
        val list = mutableListOf<Track>()
        for (i in 0 until ctrl.mediaItemCount) {
            val item = ctrl.getMediaItemAt(i)
            list.add(
                Track(
                    videoId = item.mediaId,
                    title = item.mediaMetadata.title?.toString() ?: "",
                    artist = item.mediaMetadata.artist?.toString() ?: "",
                    thumbnail = item.mediaMetadata.artworkUri?.toString()
                )
            )
        }
        return list
    }

    // ─── Player.Listener callbacks ────────────────────────────────────────────

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
        if (isPlaying) {
            startPositionUpdates()
        } else {
            stopPositionUpdates()
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        val ctrl = controller ?: return
        _currentTrack.value = NowPlayingState(
            videoId = ctrl.currentMediaItem?.mediaId ?: "",
            title = mediaMetadata.title?.toString() ?: "",
            artist = mediaMetadata.artist?.toString() ?: "",
            artworkUri = mediaMetadata.artworkUri?.toString()
        )
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val ctrl = controller ?: return
        _playbackPosition.value = ctrl.currentPosition.coerceAtLeast(0)
        _playbackDuration.value = ctrl.duration.coerceAtLeast(0)
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        _shuffleModeEnabled.value = shuffleModeEnabled
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        _repeatMode.value = repeatMode
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        saveQueueState()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        val ctrl = controller
        if (ctrl != null) {
            ctrl.currentMediaItem?.let { item ->
                val videoId = item.mediaId
                val title = item.mediaMetadata.title?.toString() ?: ""
                val artist = item.mediaMetadata.artist?.toString() ?: ""
                val artworkUri = item.mediaMetadata.artworkUri?.toString()

                _currentTrack.value = NowPlayingState(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    artworkUri = artworkUri
                )

                musicRepository.removeDiscoveredTrack(videoId)

                scope.launch {
                    val durationMs = ctrl.duration
                    val durationSec = if (durationMs > 0) (durationMs / 1000).toInt() else null
                    musicRepository.recordPlay(
                        trackId = videoId,
                        title = title,
                        artist = artist,
                        thumbnail = artworkUri,
                        duration = durationSec
                    )
                }
            }
        }
        saveQueueState()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        saveQueueState()
    }
}

data class NowPlayingState(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUri: String?
)
