package com.appplayer.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.SearchResult
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        _query
            .debounce(400)
            .distinctUntilChanged()
            .filter { it.length >= 2 }
            .onEach { q -> performSearch(q) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        _query.value = query
        _uiState.update { it.copy(query = query) }
        if (query.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = musicRepository.search(query, 30)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, results = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }
}
