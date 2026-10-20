package com.appplayer.innertube.models

import kotlinx.serialization.Serializable

/**
 * YouTube client definitions.
 * Based on Metrolist + ArchiveTune (atcore/core) analysis.
 *
 * These values mimic what real YouTube clients send — wrong values cause 403s.
 * Per-client osName/osVersion/deviceMake/deviceModel match ArchiveTune's youtube_clients.json.
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
    val loginSupported: Boolean = true,
    // Per-client device/OS context — mirrors ArchiveTune's youtube_clients.json catalog
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val buildId: String? = null,
    val cronetVersion: String? = null,
    val packageName: String? = null,
) {
    companion object {
        // ─── Android VR clients ───────────────────────────────────────────────
        // No Origin/Referer, no bot detection — primary fallback clients

        val ANDROID_VR = YouTubeClient(
            clientName = "ANDROID_VR",
            clientId = "28",
            clientVersion = "1.65.10",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
            loginSupported = false,
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            buildId = "SQ3A.220605.009.A1",
            packageName = "com.google.android.apps.youtube.vr.oculus",
        )

        val ANDROID_VR_NO_AUTH = YouTubeClient(
            clientName = "ANDROID_VR",
            clientId = "28",
            clientVersion = "1.37",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.37 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
            apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
            loginSupported = false,
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
        )

        /**
         * Apple Vision Pro client — low bot-detection, no auth required.
         * IMPORTANT: clientName MUST be "VISIONOS" and clientId MUST be "101".
         */
        val VISIONOS = YouTubeClient(
            clientName = "VISIONOS",
            clientId = "101",
            clientVersion = "0.1",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            loginSupported = false,
            osName = "visionOS",
            osVersion = "1.3.21O771",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1",
        )

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientId = "21",
            clientVersion = "7.27.52",
            userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip",
            osName = "Android",
            osVersion = "15",
            deviceMake = "Google",
            deviceModel = "Pixel 9 Pro",
            androidSdkVersion = "35",
            buildId = "AP4A.250205.002",
            cronetVersion = "132.0.6834.79",
            packageName = "com.google.android.apps.youtube.music",
        )

        val MOBILE = YouTubeClient(
            clientName = "ANDROID",
            clientId = "3",
            clientVersion = "21.26.364",
            userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            loginSupported = false,
            useSignatureTimestamp = false,
            osName = "Android",
            osVersion = "11",
        )

        // ─── iOS Clients ──────────────────────────────────────────────────────
        // No bot detection, reliable

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientId = "5",
            clientVersion = "21.26.4",
            userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2;)",
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
        )

        val IOS_MUSIC = YouTubeClient(
            clientName = "IOS_MUSIC",
            clientId = "26",
            clientVersion = "7.27.0",
            userAgent = "com.google.ios.youtubemusic/7.27.0 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
            osName = "iOS",
            osVersion = "17.5.1.21F90",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
        )

        // ─── Web Clients ──────────────────────────────────────────────────────
        // Require Origin/Referer headers; WEB_REMIX gives loudnessDb metadata

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientId = "67",
            clientVersion = "1.20260707.12.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            referer = "https://music.youtube.com/",
            origin = "https://music.youtube.com",
            useSignatureTimestamp = true,
            useWebPoTokens = true
        )

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientId = "1",
            clientVersion = "2.20260708.00.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
            referer = "https://www.youtube.com/",
            origin = "https://www.youtube.com",
            useSignatureTimestamp = true,
            useWebPoTokens = true
        )

        // ─── TV / Embedded clients ────────────────────────────────────────────

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5",
            clientId = "7",
            clientVersion = "7.20260707.07.00",
            userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
            isEmbedded = true
        )

        /** Mobile web — anonymous fallback, low bot-detection. */
        val MWEB = YouTubeClient(
            clientName = "MWEB",
            clientId = "2",
            clientVersion = "2.20260708.05.00",
            userAgent = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
            referer = "https://www.youtube.com/",
            origin = "https://www.youtube.com",
            useSignatureTimestamp = true,
            useWebPoTokens = false
        )

        // ─── API constants ────────────────────────────────────────────────────

        /** Standard web browser user-agent for NewPipe downloader. */
        const val USER_AGENT_WEB =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

        const val API_URL = "https://www.youtube.com/youtubei/v1/"
        const val API_URL_YOUTUBE_MUSIC = "https://music.youtube.com/youtubei/v1/"
        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"
    }
}
