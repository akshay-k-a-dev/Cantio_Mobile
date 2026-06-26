package com.appplayer.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.*
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val recommendations: Recommendations? = null,
    val popularTracks: List<Track> = emptyList(),
    val discoveredTracks: List<Track> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            musicRepository.discoveredTracks.collect { tracks ->
                _uiState.update { it.copy(discoveredTracks = tracks) }
            }
        }
        loadRecommendations()
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val recDeferred = async { musicRepository.getRecommendations() }
            val popularDeferred = async { musicRepository.getPopularTracks() }

            val recResult = recDeferred.await()
            val popularResult = popularDeferred.await()

            var hasError = false
            var errorMessage: String? = null

            if (recResult is ApiResult.Success<*>) {
                (recResult.data as? Recommendations)?.let { data ->
                    _uiState.update { it.copy(recommendations = data) }
                }
            } else if (recResult is ApiResult.Error) {
                hasError = true
                errorMessage = recResult.message
            }

            if (popularResult is ApiResult.Success<*>) {
                (popularResult.data as? List<Track>)?.let { data ->
                    _uiState.update { it.copy(popularTracks = data) }
                }
            } else if (popularResult is ApiResult.Error) {
                if (errorMessage == null) {
                    errorMessage = popularResult.message
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (hasError) errorMessage else null
                )
            }
        }
    }

    fun refresh() = loadRecommendations()
}
