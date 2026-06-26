package com.appplayer.music.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SongAnalysis(
    val videoId: String,
    val duration: Long, // in milliseconds
    val introStart: Long,
    val introEnd: Long,
    val outroStart: Long,
    val outroEnd: Long,
    val averageLoudness: Double, // in LUFS / dB
    val bpm: Double?,
    val energyCurve: String,
    val waveformPeaks: String,
    val lastAnalyzed: Long,
    val analysisVersion: Int
)

class SongAnalysisDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS song_analysis (
                video_id TEXT PRIMARY KEY,
                duration INTEGER,
                intro_start INTEGER,
                intro_end INTEGER,
                outro_start INTEGER,
                outro_end INTEGER,
                average_loudness REAL,
                bpm REAL,
                energy_curve TEXT,
                waveform_peaks TEXT,
                last_analyzed INTEGER,
                analysis_version INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS song_analysis")
        onCreate(db)
    }

    fun getAnalysis(videoId: String): SongAnalysis? {
        val db = readableDatabase
        val cursor = db.query(
            "song_analysis",
            null,
            "video_id = ?",
            arrayOf(videoId),
            null, null, null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                return SongAnalysis(
                    videoId = c.getString(c.getColumnIndexOrThrow("video_id")),
                    duration = c.getLong(c.getColumnIndexOrThrow("duration")),
                    introStart = c.getLong(c.getColumnIndexOrThrow("intro_start")),
                    introEnd = c.getLong(c.getColumnIndexOrThrow("intro_end")),
                    outroStart = c.getLong(c.getColumnIndexOrThrow("outro_start")),
                    outroEnd = c.getLong(c.getColumnIndexOrThrow("outro_end")),
                    averageLoudness = c.getDouble(c.getColumnIndexOrThrow("average_loudness")),
                    bpm = if (c.isNull(c.getColumnIndexOrThrow("bpm"))) null else c.getDouble(c.getColumnIndexOrThrow("bpm")),
                    energyCurve = c.getString(c.getColumnIndexOrThrow("energy_curve")),
                    waveformPeaks = c.getString(c.getColumnIndexOrThrow("waveform_peaks")),
                    lastAnalyzed = c.getLong(c.getColumnIndexOrThrow("last_analyzed")),
                    analysisVersion = c.getInt(c.getColumnIndexOrThrow("analysis_version"))
                )
            }
        }
        return null
    }

    fun saveAnalysis(analysis: SongAnalysis) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("video_id", analysis.videoId)
            put("duration", analysis.duration)
            put("intro_start", analysis.introStart)
            put("intro_end", analysis.introEnd)
            put("outro_start", analysis.outroStart)
            put("outro_end", analysis.outroEnd)
            put("average_loudness", analysis.averageLoudness)
            put("bpm", analysis.bpm)
            put("energy_curve", analysis.energyCurve)
            put("waveform_peaks", analysis.waveformPeaks)
            put("last_analyzed", analysis.lastAnalyzed)
            put("analysis_version", analysis.analysisVersion)
        }
        db.insertWithOnConflict("song_analysis", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clearCache() {
        val db = writableDatabase
        db.delete("song_analysis", null, null)
    }

    companion object {
        private const val DATABASE_NAME = "song_analysis.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var instance: SongAnalysisDatabase? = null

        fun getInstance(context: Context): SongAnalysisDatabase {
            return instance ?: synchronized(this) {
                instance ?: SongAnalysisDatabase(context.applicationContext).also { instance = it }
            }
        }
    }
}
