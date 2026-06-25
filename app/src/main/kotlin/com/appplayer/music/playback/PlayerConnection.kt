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
    @ApplicationContext private val context: Context
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
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
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

    // ─── Player.Listener callbacks ────────────────────────────────────────────

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
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
}

data class NowPlayingState(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUri: String?
)
