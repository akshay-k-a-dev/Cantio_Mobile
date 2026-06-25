package com.appplayer.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.*
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = false,
    val likedTracks: List<LikedTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val blends: List<Blend> = emptyList(),
    val blendInvites: List<BlendInvite> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState(isLoading = true))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Load all in parallel
            val likesDeferred = launch {
                when (val r = musicRepository.getLikedTracks()) {
                    is ApiResult.Success -> _uiState.update { it.copy(likedTracks = r.data) }
                    is ApiResult.Error -> Unit // non-fatal
                }
            }
            val playlistsDeferred = launch {
                when (val r = musicRepository.getPlaylists()) {
                    is ApiResult.Success -> _uiState.update { it.copy(playlists = r.data) }
                    is ApiResult.Error -> Unit
                }
            }
            val blendsDeferred = launch {
                when (val r = musicRepository.getBlends()) {
                    is ApiResult.Success -> _uiState.update { it.copy(blends = r.data) }
                    is ApiResult.Error -> Unit
                }
            }
            val invitesDeferred = launch {
                when (val r = musicRepository.getBlendInvites()) {
                    is ApiResult.Success -> _uiState.update { it.copy(blendInvites = r.data) }
                    is ApiResult.Error -> Unit
                }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun likeTrack(videoId: String, title: String, artist: String, thumbnail: String?, duration: Int?) {
        viewModelScope.launch {
            musicRepository.likeTrack(videoId, title, artist, thumbnail, duration)
            loadAll()
        }
    }

    fun unlikeTrack(videoId: String) {
        viewModelScope.launch {
            musicRepository.unlikeTrack(videoId)
            _uiState.update { it.copy(likedTracks = it.likedTracks.filter { t -> t.trackId != videoId }) }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            when (val result = musicRepository.createPlaylist(name)) {
                is ApiResult.Success -> _uiState.update { it.copy(playlists = listOf(result.data) + it.playlists) }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(id)
            _uiState.update { it.copy(playlists = it.playlists.filter { p -> p.id != id }) }
        }
    }

    fun acceptBlendInvite(inviteId: String) {
        viewModelScope.launch {
            when (val result = musicRepository.acceptBlendInvite(inviteId)) {
                is ApiResult.Success -> loadAll()
                is ApiResult.Error -> Unit
            }
        }
    }
}
