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
        viewModelScope.launch {
            musicRepository.likedTracks.collect { tracks ->
                _uiState.update { it.copy(likedTracks = tracks) }
            }
        }
        viewModelScope.launch {
            musicRepository.playlists.collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            launch { musicRepository.getLikedTracks() }
            launch { musicRepository.getPlaylists() }
            launch {
                when (val r = musicRepository.getBlends()) {
                    is ApiResult.Success -> _uiState.update { it.copy(blends = r.data) }
                    is ApiResult.Error -> Unit
                }
            }
            launch {
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
        }
    }

    fun unlikeTrack(videoId: String) {
        viewModelScope.launch {
            musicRepository.unlikeTrack(videoId)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            musicRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(id)
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

    fun rejectBlendInvite(inviteId: String) {
        viewModelScope.launch {
            when (musicRepository.rejectBlendInvite(inviteId)) {
                is ApiResult.Success -> loadAll()
                is ApiResult.Error -> Unit
            }
        }
    }

    fun sendBlendInvite(email: String) {
        viewModelScope.launch {
            when (musicRepository.sendBlendInvite(email)) {
                is ApiResult.Success -> loadAll()
                is ApiResult.Error -> Unit
            }
        }
    }

    fun leaveBlend(blendId: String) {
        viewModelScope.launch {
            when (musicRepository.leaveBlend(blendId)) {
                is ApiResult.Success -> loadAll()
                is ApiResult.Error -> Unit
            }
        }
    }
}
