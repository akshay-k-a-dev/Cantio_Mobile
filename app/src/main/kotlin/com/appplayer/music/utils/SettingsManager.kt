package com.appplayer.music.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cantio_settings", Context.MODE_PRIVATE)

    fun setVolumeNormalization(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_NORM, enabled).apply()
    }
    fun getVolumeNormalization(): Boolean = prefs.getBoolean(KEY_VOLUME_NORM, false)

    fun setAudioQuality(quality: String) {
        prefs.edit().putString(KEY_AUDIO_QUALITY, quality).apply()
    }
    fun getAudioQuality(): String = prefs.getString(KEY_AUDIO_QUALITY, "High") ?: "High"

    fun setAutoplay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, enabled).apply()
    }
    fun getAutoplay(): Boolean = prefs.getBoolean(KEY_AUTOPLAY, true)

    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }
    fun getTheme(): String = prefs.getString(KEY_THEME, "System") ?: "System"

    fun setBackgroundPlayback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BG_PLAYBACK, enabled).apply()
    }
    fun getBackgroundPlayback(): Boolean = prefs.getBoolean(KEY_BG_PLAYBACK, true)

    // ─── Smart Crossfade Settings ─────────────────────────────────────────────

    fun setSmartCrossfade(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_CROSSFADE, enabled).apply()
    }
    fun getSmartCrossfade(): Boolean = prefs.getBoolean(KEY_SMART_CROSSFADE, false)

    fun setDynamicFadeLength(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_FADE_LENGTH, enabled).apply()
    }
    fun getDynamicFadeLength(): Boolean = prefs.getBoolean(KEY_DYNAMIC_FADE_LENGTH, true)

    fun setLoudnessMatching(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOUDNESS_MATCHING, enabled).apply()
    }
    fun getLoudnessMatching(): Boolean = prefs.getBoolean(KEY_LOUDNESS_MATCHING, true)

    fun setGaplessPlayback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GAPLESS_PLAYBACK, enabled).apply()
    }
    fun getGaplessPlayback(): Boolean = prefs.getBoolean(KEY_GAPLESS_PLAYBACK, true)

    fun setAnalyzeSongsAutomatically(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYZE_SONGS_AUTO, enabled).apply()
    }
    fun getAnalyzeSongsAutomatically(): Boolean = prefs.getBoolean(KEY_ANALYZE_SONGS_AUTO, true)

    companion object {
        private const val KEY_VOLUME_NORM = "volume_normalization"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_AUTOPLAY = "autoplay"
        private const val KEY_THEME = "theme"
        private const val KEY_BG_PLAYBACK = "background_playback"

        private const val KEY_SMART_CROSSFADE = "smart_crossfade"
        private const val KEY_DYNAMIC_FADE_LENGTH = "dynamic_fade_length"
        private const val KEY_LOUDNESS_MATCHING = "loudness_matching"
        private const val KEY_GAPLESS_PLAYBACK = "gapless_playback"
        private const val KEY_ANALYZE_SONGS_AUTO = "analyze_songs_automatically"
    }
}
