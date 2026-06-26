package com.appplayer.music.utils

import android.content.Context
import android.media.audiofx.Equalizer
import timber.log.Timber

class EqualizerManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int? = null

    var isEnabled: Boolean
        get() = prefs.getBoolean("eq_enabled", false)
        set(value) {
            prefs.edit().putBoolean("eq_enabled", value).apply()
            equalizer?.enabled = value
            Timber.d("Equalizer enabled status set to: $value")
        }

    fun initEqualizer(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (currentSessionId == audioSessionId && equalizer != null) return

        release()
        currentSessionId = audioSessionId
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            eq.enabled = isEnabled
            
            val numBands = eq.numberOfBands
            for (i in 0 until numBands) {
                val savedLevel = prefs.getInt("eq_band_$i", 0)
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]
                val clamped = savedLevel.coerceIn(minLevel.toInt(), maxLevel.toInt())
                eq.setBandLevel(i.toShort(), clamped.toShort())
            }
            Timber.d("Equalizer initialized successfully for audioSessionId: $audioSessionId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Equalizer for session $audioSessionId")
        }
    }

    fun getBandCount(): Int {
        return equalizer?.numberOfBands?.toInt() ?: 5
    }

    fun getCenterFreq(band: Int): Int {
        return try {
            equalizer?.getCenterFreq(band.toShort()) ?: getDefaultCenterFreq(band)
        } catch (e: Exception) {
            getDefaultCenterFreq(band)
        }
    }

    private fun getDefaultCenterFreq(band: Int): Int {
        return when (band) {
            0 -> 60000
            1 -> 230000
            2 -> 910000
            3 -> 4000000
            else -> 14000000
        }
    }

    fun getBandLevel(band: Int): Int {
        return try {
            equalizer?.getBandLevel(band.toShort())?.toInt() ?: prefs.getInt("eq_band_$band", 0)
        } catch (e: Exception) {
            prefs.getInt("eq_band_$band", 0)
        }
    }

    fun setBandLevel(band: Int, milliBels: Int) {
        prefs.edit().putInt("eq_band_$band", milliBels).apply()
        try {
            equalizer?.setBandLevel(band.toShort(), milliBels.toShort())
            Timber.d("EQ Band $band level set to ${milliBels / 100}dB")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set EQ band level")
        }
    }

    fun getLevelRange(): Pair<Int, Int> {
        return try {
            val range = equalizer?.bandLevelRange
            if (range != null && range.size >= 2) {
                range[0].toInt() to range[1].toInt()
            } else {
                -1500 to 1500
            }
        } catch (e: Exception) {
            -1500 to 1500
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            // ignore
        }
        equalizer = null
        currentSessionId = null
    }

    companion object {
        @Volatile
        private var instance: EqualizerManager? = null

        fun getInstance(context: Context): EqualizerManager {
            return instance ?: synchronized(this) {
                instance ?: EqualizerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
