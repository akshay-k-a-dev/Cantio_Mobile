package com.appplayer.music.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.Blend
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.appplayer.music.utils.TokenManager

data class BlendDetailUiState(
    val isLoading: Boolean = false,
    val blend: Blend? = null,
    val error: String? = null,
    val isRegenerating: Boolean = false,
    val isLeaving: Boolean = false
)

@HiltViewModel
class BlendDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val tokenManager: TokenManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val currentUserId: String? = tokenManager.getUserId()
    private val blendId: String = checkNotNull(savedStateHandle["blendId"])

    private val _uiState = MutableStateFlow(BlendDetailUiState(isLoading = true))
    val uiState: StateFlow<BlendDetailUiState> = _uiState.asStateFlow()

    init {
        loadBlend()
    }

    fun loadBlend() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = musicRepository.getBlend(blendId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, blend = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun regenerateBlend() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRegenerating = true) }
            when (val result = musicRepository.regenerateBlend(blendId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isRegenerating = false, blend = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isRegenerating = false, error = result.message)
                }
            }
        }
    }

    fun leaveBlend(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLeaving = true) }
            when (val result = musicRepository.leaveBlend(blendId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLeaving = false) }
                    onSuccess()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLeaving = false, error = result.message)
                }
            }
        }
    }
}
