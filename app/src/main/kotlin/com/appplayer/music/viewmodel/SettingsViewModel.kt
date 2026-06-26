package com.appplayer.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.UserProfile
import com.appplayer.music.data.cache.CacheManager
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.AuthRepository
import com.appplayer.music.utils.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context

data class SettingsUiState(
    val userProfile: UserProfile? = null,
    val cacheSizeStr: String = "Calculating...",
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val volumeNormalization: Boolean = false,
    val audioQuality: String = "High",
    val autoplay: Boolean = true,
    val theme: String = "System",
    val backgroundPlayback: Boolean = true,
    
    // Crossfade Settings
    val smartCrossfade: Boolean = false,
    val dynamicFadeLength: Boolean = true,
    val loudnessMatching: Boolean = true,
    val gaplessPlayback: Boolean = true,
    val analyzeSongsAuto: Boolean = true,
    
    // Equalizer Settings
    val eqEnabled: Boolean = false,
    val eqBands: List<Pair<Int, Int>> = emptyList(), // Freq (Hz) to milliBels
    val eqRange: Pair<Int, Int> = -1500 to 1500,
    
    // Output Devices
    val activeDevices: List<String> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cacheManager: CacheManager,
    private val settingsManager: SettingsManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
        loadCacheSize()
        loadPreferences()
        loadEqualizerSettings()
        loadActiveDevices()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val result = authRepository.getMe()
            if (result is ApiResult.Success) {
                _uiState.update { it.copy(userProfile = result.data) }
            }
        }
    }

    fun loadCacheSize() {
        viewModelScope.launch {
            val bytes = cacheManager.getCacheSize()
            val sizeStr = formatBytes(bytes)
            _uiState.update { it.copy(cacheSizeStr = sizeStr) }
        }
    }

    private fun loadPreferences() {
        _uiState.update {
            it.copy(
                volumeNormalization = settingsManager.getVolumeNormalization(),
                audioQuality = settingsManager.getAudioQuality(),
                autoplay = settingsManager.getAutoplay(),
                theme = settingsManager.getTheme(),
                backgroundPlayback = settingsManager.getBackgroundPlayback(),
                smartCrossfade = settingsManager.getSmartCrossfade(),
                dynamicFadeLength = settingsManager.getDynamicFadeLength(),
                loudnessMatching = settingsManager.getLoudnessMatching(),
                gaplessPlayback = settingsManager.getGaplessPlayback(),
                analyzeSongsAuto = settingsManager.getAnalyzeSongsAutomatically()
            )
        }
    }

    private fun loadEqualizerSettings() {
        val eqManager = com.appplayer.music.utils.EqualizerManager.getInstance(context)
        val count = eqManager.getBandCount()
        val bands = (0 until count).map { i ->
            eqManager.getCenterFreq(i) to eqManager.getBandLevel(i)
        }
        val range = eqManager.getLevelRange()
        _uiState.update {
            it.copy(
                eqEnabled = eqManager.isEnabled,
                eqBands = bands,
                eqRange = range
            )
        }
    }

    fun loadActiveDevices() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            val list = devices.map { device ->
                val typeName = when (device.type) {
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
                    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Device"
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headphones"
                    else -> "Audio Output"
                }
                "${device.productName} ($typeName)"
            }.distinct()
            _uiState.update { it.copy(activeDevices = list) }
        } catch (e: Exception) {
            // ignore
        }
    }

    // ─── Crossfade Actions ────────────────────────────────────────────────────

    fun setSmartCrossfade(enabled: Boolean) {
        settingsManager.setSmartCrossfade(enabled)
        _uiState.update { it.copy(smartCrossfade = enabled) }
    }

    fun setDynamicFadeLength(enabled: Boolean) {
        settingsManager.setDynamicFadeLength(enabled)
        _uiState.update { it.copy(dynamicFadeLength = enabled) }
    }

    fun setLoudnessMatching(enabled: Boolean) {
        settingsManager.setLoudnessMatching(enabled)
        _uiState.update { it.copy(loudnessMatching = enabled) }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        settingsManager.setGaplessPlayback(enabled)
        _uiState.update { it.copy(gaplessPlayback = enabled) }
    }

    fun setAnalyzeSongsAuto(enabled: Boolean) {
        settingsManager.setAnalyzeSongsAutomatically(enabled)
        _uiState.update { it.copy(analyzeSongsAuto = enabled) }
    }

    fun rebuildAnalysisCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            com.appplayer.music.utils.SongAnalysisDatabase.getInstance(context).clearCache()
            _uiState.update { it.copy(isLoading = false, successMessage = "Analysis cache rebuilt successfully") }
        }
    }

    // ─── Equalizer Actions ────────────────────────────────────────────────────

    fun setEqEnabled(enabled: Boolean) {
        val eqManager = com.appplayer.music.utils.EqualizerManager.getInstance(context)
        eqManager.isEnabled = enabled
        _uiState.update { it.copy(eqEnabled = enabled) }
    }

    fun setEqBandLevel(band: Int, milliBels: Int) {
        val eqManager = com.appplayer.music.utils.EqualizerManager.getInstance(context)
        eqManager.setBandLevel(band, milliBels)
        loadEqualizerSettings()
    }

    // ─── Cache Actions ────────────────────────────────────────────────────────

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            cacheManager.clearPlayerCache()
            loadCacheSize()
            _uiState.update { it.copy(isLoading = false, successMessage = "Cache cleared successfully") }
        }
    }

    fun setVolumeNormalization(enabled: Boolean) {
        settingsManager.setVolumeNormalization(enabled)
        _uiState.update { it.copy(volumeNormalization = enabled) }
    }

    fun setAudioQuality(quality: String) {
        settingsManager.setAudioQuality(quality)
        _uiState.update { it.copy(audioQuality = quality) }
    }

    fun setAutoplay(enabled: Boolean) {
        settingsManager.setAutoplay(enabled)
        _uiState.update { it.copy(autoplay = enabled) }
    }

    fun setTheme(theme: String) {
        settingsManager.setTheme(theme)
        _uiState.update { it.copy(theme = theme) }
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        settingsManager.setBackgroundPlayback(enabled)
        _uiState.update { it.copy(backgroundPlayback = enabled) }
    }

    fun logout(onSuccess: () -> Unit) {
        authRepository.logout()
        onSuccess()
    }

    fun changePassword(current: String, new: String, onResult: (ApiResult<com.appplayer.music.data.api.models.SuccessResponse>) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.changePassword(current, new)
            _uiState.update { it.copy(isLoading = false) }
            if (result is ApiResult.Success) {
                _uiState.update { it.copy(successMessage = "Password changed successfully") }
            } else if (result is ApiResult.Error) {
                _uiState.update { it.copy(errorMessage = result.message) }
            }
            onResult(result)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
