package com.appplayer.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.SearchResult
import com.appplayer.music.data.api.models.Track
import com.appplayer.music.data.api.models.Playlist
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class SearchTab(val value: String, val label: String) {
    SONGS("songs", "Songs"),
    PLAYLISTS("playlists", "Playlists"),
    ALBUMS("albums", "Albums"),
    ARTISTS("artists", "Artists")
}

data class SearchUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val activeTab: SearchTab = SearchTab.SONGS,
    val results: List<SearchResult> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    private val _activeTab = MutableStateFlow(SearchTab.SONGS)
    private var searchJob: Job? = null

    // IMPORTANT: _playlists must be declared BEFORE init{} so it is initialized
    // before the init block references it. Kotlin initializes class members in
    // declaration order — declaring it after init caused a NullPointerException
    // every time SearchScreen was navigated to.
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        viewModelScope.launch {
            musicRepository.playlists.collect { list ->
                _playlists.value = list
            }
        }

        combine(_query, _activeTab) { q, tab -> Pair(q, tab) }
            .debounce(400)
            .distinctUntilChanged()
            .onEach { (q, tab) ->
                if (q.length >= 2) {
                    performSearch(q, tab)
                } else {
                    _uiState.update { it.copy(results = emptyList(), isLoading = false, error = null) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        _query.value = query
        _uiState.update { it.copy(query = query) }
        if (query.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false, error = null) }
        }
    }

    fun onTabSelected(tab: SearchTab) {
        _activeTab.value = tab
        _uiState.update { it.copy(activeTab = tab) }
    }

    private fun performSearch(query: String, tab: SearchTab) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (tab == SearchTab.PLAYLISTS) {
                val publicPlaylistsDeferred = async { musicRepository.getPublicPlaylists(query, 15) }
                val ytPlaylistsDeferred = async { musicRepository.searchMusic(query, "playlists", 25) }

                val publicRes = publicPlaylistsDeferred.await()
                val ytRes = ytPlaylistsDeferred.await()

                val mergedResults = mutableListOf<SearchResult>()
                var errorMessage: String? = null

                if (publicRes is ApiResult.Success) {
                    publicRes.data.forEach { p ->
                        mergedResults.add(
                            SearchResult(
                                playlistId = p.id,
                                title = p.name,
                                name = p.name,
                                author = p.user?.name ?: p.user?.username ?: "Cantio User",
                                artist = p.user?.name ?: p.user?.username ?: "Cantio User",
                                thumbnail = p.thumbnail,
                                trackCount = p.count?.tracks ?: 0,
                                type = "cantio_playlist"
                            )
                        )
                    }
                } else if (publicRes is ApiResult.Error) {
                    errorMessage = publicRes.message
                }

                if (ytRes is ApiResult.Success) {
                    mergedResults.addAll(ytRes.data)
                } else if (ytRes is ApiResult.Error) {
                    errorMessage = ytRes.message
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = mergedResults,
                        error = if (mergedResults.isEmpty()) errorMessage else null
                    )
                }
            } else {
                val result = if (tab == SearchTab.SONGS) {
                    musicRepository.search(query, 30)
                } else {
                    musicRepository.searchMusic(query, tab.value, 30)
                }
                when (result) {
                    is ApiResult.Success -> {
                        _uiState.update {
                            it.copy(isLoading = false, results = result.data)
                        }
                        if (tab == SearchTab.SONGS) {
                            val tracks = result.data.map {
                                Track(
                                    videoId = it.videoId ?: "",
                                    title = it.title ?: "",
                                    artist = it.artist ?: "Unknown Artist",
                                    thumbnail = it.thumbnail,
                                    duration = it.duration ?: 0
                                )
                            }
                            musicRepository.addDiscoveredTracks(tracks)
                        }
                    }
                    is ApiResult.Error -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    fun loadUserPlaylists() {
        viewModelScope.launch {
            musicRepository.getPlaylists()
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(
                playlistId = playlistId,
                videoId = track.videoId,
                title = track.title,
                artist = track.artist,
                thumbnail = track.thumbnail,
                duration = track.duration
            )
        }
    }
}
