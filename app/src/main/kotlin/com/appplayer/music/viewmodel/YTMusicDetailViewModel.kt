package com.appplayer.music.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.Track
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YTMusicDetailUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val thumbnail: String? = null,
    val tracks: List<Track> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class YTMusicDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val type: String = savedStateHandle.get<String>("type") ?: ""
    private val id: String = savedStateHandle.get<String>("id") ?: ""

    private val _uiState = MutableStateFlow(YTMusicDetailUiState())
    val uiState: StateFlow<YTMusicDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (type) {
                "playlist" -> {
                    when (val result = musicRepository.getYTMusicPlaylist(id)) {
                        is ApiResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    title = "YouTube Playlist",
                                    subtitle = "YT Music Catalog",
                                    tracks = result.data,
                                    thumbnail = if (result.data.isNotEmpty()) result.data.first().thumbnail else null
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            _uiState.update { it.copy(isLoading = false, error = result.message) }
                        }
                    }
                }
                "album" -> {
                    when (val result = musicRepository.getYTMusicAlbum(id)) {
                        is ApiResult.Success -> {
                            val data = result.data
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    title = data.title ?: "Unknown Album",
                                    subtitle = data.artist ?: "Unknown Artist",
                                    thumbnail = data.thumbnail,
                                    tracks = data.tracks ?: emptyList()
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            _uiState.update { it.copy(isLoading = false, error = result.message) }
                        }
                    }
                }
                "artist" -> {
                    when (val result = musicRepository.getYTMusicArtist(id)) {
                        is ApiResult.Success -> {
                            val data = result.data
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    title = data.name ?: "Unknown Artist",
                                    subtitle = data.subscribers ?: "Artist",
                                    thumbnail = data.thumbnail,
                                    tracks = data.tracks ?: emptyList()
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            _uiState.update { it.copy(isLoading = false, error = result.message) }
                        }
                    }
                }
                else -> {
                    _uiState.update { it.copy(isLoading = false, error = "Invalid detail type") }
                }
            }
        }
    }
}
