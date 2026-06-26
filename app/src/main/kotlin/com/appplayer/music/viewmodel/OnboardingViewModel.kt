package com.appplayer.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.UserPreferences
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val selectedLanguages: List<String> = emptyList(),
    val selectedGenres: List<String> = emptyList(),
    val selectedArtists: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSeeding: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    val availableLanguages = listOf("Malayalam", "Tamil", "Telugu", "Hindi", "English", "Punjabi", "Kannada")
    val availableGenres = listOf("Melody", "Pop", "Rock", "Hip Hop", "Classical", "Indie", "R&B", "Devotional")
    val availableArtists = listOf(
        "A. R. Rahman", "Sid Sriram", "Shreya Ghoshal", "Anirudh Ravichander",
        "K. S. Chithra", "Harris Jayaraj", "Yuvan Shankar Raja", "Vidyasagar",
        "Santhosh Narayanan", "Sushin Shyam", "G. V. Prakash Kumar", "K. J. Yesudas", "S. P. Balasubrahmanyam"
    )

    fun toggleLanguage(language: String) {
        _uiState.update { state ->
            val list = state.selectedLanguages.toMutableList()
            if (list.contains(language)) list.remove(language) else list.add(language)
            state.copy(selectedLanguages = list)
        }
    }

    fun toggleGenre(genre: String) {
        _uiState.update { state ->
            val list = state.selectedGenres.toMutableList()
            if (list.contains(genre)) list.remove(genre) else list.add(genre)
            state.copy(selectedGenres = list)
        }
    }

    fun toggleArtist(artist: String) {
        _uiState.update { state ->
            val list = state.selectedArtists.toMutableList()
            if (list.contains(artist)) list.remove(artist) else list.add(artist)
            state.copy(selectedArtists = list)
        }
    }

    fun finishOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val prefs = UserPreferences(
                favoriteLanguages = _uiState.value.selectedLanguages,
                favoriteGenres = _uiState.value.selectedGenres,
                favoriteArtists = _uiState.value.selectedArtists,
                onboardingDone = true
            )
            when (val saveResult = authRepository.savePreferences(prefs)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, isSeeding = true) }
                    // Seed recommendations
                    val seedResult = authRepository.seedRecommendations()
                    _uiState.update { it.copy(isSeeding = false) }
                    when (seedResult) {
                        is ApiResult.Success -> {
                            _uiState.update { it.copy(success = true) }
                            onSuccess()
                        }
                        is ApiResult.Error -> {
                            _uiState.update { it.copy(errorMessage = "Onboarding completed but seed recommendations failed: ${seedResult.message}") }
                            // Non-fatal, still navigate home
                            onSuccess()
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = saveResult.message) }
                }
            }
        }
    }
}
