package com.appplayer.music.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.Playlist
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val isLoading: Boolean = false,
    val playlist: Playlist? = null,
    val error: String? = null,
    val isRemoving: String? = null, // trackId being removed
    val isUpdatingPublic: Boolean = false
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow(PlaylistDetailUiState(isLoading = true))
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        loadPlaylist()
    }

    fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = musicRepository.getPlaylist(playlistId)
            if (result is ApiResult.Success) {
                _uiState.update {
                    it.copy(isLoading = false, playlist = result.data, isRemoving = null)
                }
            } else {
                when (val publicResult = musicRepository.getPublicPlaylist(playlistId)) {
                    is ApiResult.Success -> _uiState.update {
                        it.copy(isLoading = false, playlist = publicResult.data, isRemoving = null)
                    }
                    is ApiResult.Error -> _uiState.update {
                        it.copy(isLoading = false, error = publicResult.message)
                    }
                }
            }
        }
    }

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRemoving = trackId) }
            when (val result = musicRepository.removeTrackFromPlaylist(playlistId, trackId)) {
                is ApiResult.Success -> {
                    loadPlaylist()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isRemoving = null, error = result.message) }
                }
            }
        }
    }

    fun togglePublic(isPublic: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingPublic = true) }
            when (val result = musicRepository.updatePlaylist(playlistId, isPublic = isPublic)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isUpdatingPublic = false, playlist = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isUpdatingPublic = false, error = result.message) }
                }
            }
        }
    }
}
