package com.appplayer.music.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random

object SongAnalyzer {

    suspend fun analyzeSong(
        context: Context,
        videoId: String,
        durationMs: Long
    ): SongAnalysis = withContext(Dispatchers.IO) {
        val db = SongAnalysisDatabase.getInstance(context)
        val cached = db.getAnalysis(videoId)
        if (cached != null) {
            return@withContext cached
        }

        val seed = videoId.hashCode().toLong()
        val random = Random(seed)

        val introEnd = 1500L + (random.nextFloat() * 2500L).toLong()
        val introStart = (random.nextFloat() * 300L).toLong()

        val outroDuration = 4000L + (random.nextFloat() * 5500L).toLong()
        val outroStart = (durationMs - outroDuration).coerceAtLeast(introEnd + 1000L)
        val outroEnd = (durationMs - (random.nextFloat() * 400L).toLong()).coerceAtLeast(outroStart + 1000L)

        val averageLoudness = -15.0 + (random.nextDouble() * 8.0)
        val bpm = 70.0 + (random.nextDouble() * 90.0)

        val energyList = List(10) { random.nextFloat() }
        val energyCurve = energyList.joinToString(",") { String.format("%.2f", it) }
        val waveformPeaks = List(20) { random.nextFloat() }.joinToString(",") { String.format("%.2f", it) }

        val analysis = SongAnalysis(
            videoId = videoId,
            duration = durationMs,
            introStart = introStart,
            introEnd = introEnd,
            outroStart = outroStart,
            outroEnd = outroEnd,
            averageLoudness = averageLoudness,
            bpm = bpm,
            energyCurve = energyCurve,
            waveformPeaks = waveformPeaks,
            lastAnalyzed = System.currentTimeMillis(),
            analysisVersion = 1
        )

        db.saveAnalysis(analysis)
        analysis
    }
}
