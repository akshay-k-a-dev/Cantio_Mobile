package com.appplayer.innertube.models

import kotlinx.serialization.Serializable

/**
 * YouTube client definitions.
 * Based on Metrolist + ArchiveTune analysis.
 *
 * These values mimic what real YouTube clients send — wrong values cause 403s.
 */
@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientId: String,          // Sent as X-YouTube-Client-Name
    val clientVersion: String,     // Sent as X-YouTube-Client-Version
    val userAgent: String,
    val apiKey: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3", // YTM API key
    val referer: String? = null,
    val origin: String? = null,
    val isEmbedded: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val useWebPoTokens: Boolean = false,
    val loginSupported: Boolean = true
) {
    companion object {
        // ─── Android Clients ─────────────────────────────────────────────────
        // These don't need Origin/Referer headers and work without login

        val ANDROID_VR = YouTubeClient(
            clientName = "ANDROID_VR",
            clientId = "28",
            clientVersion = "1.60.19",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; GB) gzip",
            loginSupported = false
        )

        val ANDROID_VR_NO_AUTH = YouTubeClient(
            clientName = "ANDROID_VR",
            clientId = "28",
            clientVersion = "1.56.21",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.56.21 (Linux; U; Android 14; GB) gzip",
            loginSupported = false
        )

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientId = "21",
            clientVersion = "7.16.52",
            userAgent = "com.google.android.apps.youtube.music/7.16.52 (Linux; U; Android 13; en_US) gzip"
        )

        // ─── iOS Clients ──────────────────────────────────────────────────────
        // No bot detection, reliable

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientId = "5",
            clientVersion = "19.45.4",
            userAgent = "com.google.ios.youtube/19.45.4 like iPhone"
        )

        val IOS_MUSIC = YouTubeClient(
            clientName = "IOS_MUSIC",
            clientId = "26",
            clientVersion = "7.08.3",
            userAgent = "com.google.ios.youtubemusic/7.08.3 like iPhone"
        )

        // ─── Web Clients ──────────────────────────────────────────────────────
        // Require Origin/Referer headers; WEB_REMIX gives loudnessDb metadata

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientId = "67",
            clientVersion = "1.20241127.01.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
            referer = "https://music.youtube.com/",
            origin = "https://music.youtube.com",
            useSignatureTimestamp = true,
            useWebPoTokens = true
        )

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientId = "1",
            clientVersion = "2.20241127.00.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
            referer = "https://www.youtube.com/",
            origin = "https://www.youtube.com",
            useSignatureTimestamp = true,
            useWebPoTokens = true
        )

        // ─── TV / Embedded clients ────────────────────────────────────────────

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5",
            clientId = "7",
            clientVersion = "7.20241201.18.00",
            userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.0) AppleWebKit/538.1 " +
                        "(KHTML, like Gecko) Version/6.0 TV Safari/538.1",
            isEmbedded = true
        )

        // ─── API constants ────────────────────────────────────────────────────

        const val API_URL = "https://www.youtube.com/youtubei/v1/"
        const val API_URL_YOUTUBE_MUSIC = "https://music.youtube.com/youtubei/v1/"
        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"
    }
}
