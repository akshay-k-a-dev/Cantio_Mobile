package com.appplayer.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.LikedTrack
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.MusicRepository
import com.appplayer.music.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    private val _likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds: StateFlow<Set<String>> = _likedTrackIds.asStateFlow()

    val isCurrentTrackLiked: StateFlow<Boolean> = combine(
        playerConnection.currentTrack,
        _likedTrackIds
    ) { current, likedIds ->
        current?.videoId != null && likedIds.contains(current.videoId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            musicRepository.likedTracks.collect { tracks ->
                _likedTrackIds.value = tracks.map { it.trackId }.toSet()
            }
        }
        loadLikedTracks()
    }

    fun loadLikedTracks() {
        viewModelScope.launch {
            musicRepository.getLikedTracks()
        }
    }

    fun toggleLike() {
        val current = playerConnection.currentTrack.value ?: return
        val videoId = current.videoId
        val isCurrentlyLiked = isCurrentTrackLiked.value

        // Immediate optimistic update so the UI responds instantly
        if (isCurrentlyLiked) {
            _likedTrackIds.update { it - videoId }
        } else {
            _likedTrackIds.update { it + videoId }
        }

        viewModelScope.launch {
            if (isCurrentlyLiked) {
                val result = musicRepository.unlikeTrack(videoId)
                if (result is ApiResult.Error) {
                    // Revert optimistic update on failure
                    _likedTrackIds.update { it + videoId }
                }
            } else {
                val result = musicRepository.likeTrack(
                    videoId = videoId,
                    title = current.title,
                    artist = current.artist,
                    thumbnail = current.artworkUri,
                    duration = (playerConnection.playbackDuration.value / 1000).toInt()
                )
                if (result is ApiResult.Error) {
                    // Revert optimistic update on failure
                    _likedTrackIds.update { it - videoId }
                }
            }
        }
    }

    // ─── Playlists ────────────────────────────────────────────────────────────

    val playlists = musicRepository.playlists

    fun loadPlaylists() {
        viewModelScope.launch {
            musicRepository.getPlaylists()
        }
    }

    fun addCurrentTrackToPlaylist(playlistId: String) {
        val current = playerConnection.currentTrack.value ?: return
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(
                playlistId = playlistId,
                videoId = current.videoId,
                title = current.title,
                artist = current.artist,
                thumbnail = current.artworkUri,
                duration = (playerConnection.playbackDuration.value / 1000).toInt().takeIf { it > 0 }
            )
        }
    }
}
