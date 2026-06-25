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

data class HomeUiState(
    val isLoading: Boolean = false,
    val recommendations: Recommendations? = null,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRecommendations()
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = musicRepository.getRecommendations()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, recommendations = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun refresh() = loadRecommendations()
}
