package com.appplayer.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.LyricsResponseItem
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.MusicRepository
import com.appplayer.music.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncedLine(val timeMs: Long, val text: String)

data class LyricsUiState(
    val isLoading: Boolean = false,
    val isInstrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLines: List<SyncedLine> = emptyList(),
    val activeLineIndex: Int = -1,
    val error: String? = null
)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    private val _uiState = MutableStateFlow(LyricsUiState())
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    init {
        // Observe current track and duration changes
        combine(playerConnection.currentTrack, playerConnection.playbackDuration) { track, durationMs ->
            Pair(track, durationMs)
        }
        .distinctUntilChanged { old, new ->
            old.first?.videoId == new.first?.videoId && (old.second > 0L) == (new.second > 0L)
        }
        .onEach { (track, durationMs) ->
            if (track != null) {
                fetchLyrics(track.title, track.artist, (durationMs / 1000).toInt())
            } else {
                _uiState.value = LyricsUiState()
            }
        }
        .launchIn(viewModelScope)

        // Observe playback position to update active line index
        playerConnection.playbackPosition
            .onEach { positionMs ->
                val lines = _uiState.value.syncedLines
                if (lines.isNotEmpty()) {
                    val index = getCurrentLineIndex(lines, positionMs)
                    if (index != _uiState.value.activeLineIndex) {
                        _uiState.update { it.copy(activeLineIndex = index) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun fetchLyrics(trackName: String, artistName: String, durationSecs: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, plainLyrics = null, syncedLines = emptyList(), activeLineIndex = -1, isInstrumental = false) }
            val result = musicRepository.getLyrics(trackName, artistName, durationSecs)
            when (result) {
                is ApiResult.Success -> {
                    val data = result.data
                    if (data == null) {
                        _uiState.update { it.copy(isLoading = false, error = "No lyrics found") }
                    } else if (data.instrumental == true) {
                        _uiState.update { it.copy(isLoading = false, isInstrumental = true) }
                    } else {
                        val parsed = data.syncedLyrics?.let { parseSyncedLyrics(it) } ?: emptyList()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                plainLyrics = data.plainLyrics,
                                syncedLines = parsed
                            )
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    private fun parseSyncedLyrics(syncedLyrics: String): List<SyncedLine> {
        val lines = syncedLyrics.split("\n").filter { it.trim().isNotEmpty() }
        val parsed = mutableListOf<SyncedLine>()
        val regex = Regex("\\[(\\d+):(\\d+)(?:\\.(\\d+))?\\]\\s*(.*)$")
        
        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionStr = match.groupValues[3]
                val fractionMs = if (fractionStr.isNotEmpty()) {
                    val ms = fractionStr.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
                    if (fractionStr.length == 2) ms * 10 else ms
                } else 0L
                val timeMs = (minutes * 60 + seconds) * 1000 + fractionMs
                val text = match.groupValues[4].trim()
                parsed.add(SyncedLine(timeMs, text))
            }
        }
        return parsed.sortedBy { it.timeMs }
    }

    private fun getCurrentLineIndex(lines: List<SyncedLine>, currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        for (i in lines.indices.reversed()) {
            if (currentPositionMs >= lines[i].timeMs) {
                return i
            }
        }
        return -1
    }
}
