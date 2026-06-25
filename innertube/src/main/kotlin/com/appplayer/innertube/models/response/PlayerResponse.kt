package com.appplayer.innertube.models.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import kotlinx.serialization.json.JsonElement

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
    val playerAds: List<PlayerAd>? = null,
    val playbackTracking: PlaybackTracking? = null,
    val audioConfig: AudioConfig? = null
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
        val errorScreen: ErrorScreen? = null
    )

    @Serializable
    data class ErrorScreen(
        val playerErrorMessageRenderer: PlayerErrorMessageRenderer? = null
    )

    @Serializable
    data class PlayerErrorMessageRenderer(
        val reason: Reason? = null
    )

    @Serializable
    data class Reason(val runs: List<Run>? = null)

    @Serializable
    data class Run(val text: String? = null)

    @Serializable
    data class StreamingData(
        val formats: List<Format>? = null,
        val adaptiveFormats: List<Format>? = null,
        @SerialName("expiresInSeconds") val expiresInSeconds: String? = null,
        val dashManifestUrl: String? = null,
        val hlsManifestUrl: String? = null
    )

    @Serializable
    data class Format(
        val itag: Int? = null,
        val url: String? = null,
        val mimeType: String? = null,
        val bitrate: Int? = null,
        val width: Int? = null,
        val height: Int? = null,
        val contentLength: String? = null,
        val quality: String? = null,
        val audioQuality: String? = null,
        val approxDurationMs: String? = null,
        @SerialName("audioSampleRate") val audioSampleRate: String? = null,
        @SerialName("audioChannels") val audioChannels: Int? = null,
        val loudnessDb: Double? = null,
        val signatureCipher: String? = null,
        val cipher: String? = null
    )

    @Serializable
    data class VideoDetails(
        val videoId: String? = null,
        val title: String? = null,
        val author: String? = null,
        val channelId: String? = null,
        val lengthSeconds: String? = null,
        val thumbnail: Thumbnails? = null,
        val isLive: Boolean? = null
    )

    @Serializable
    data class Thumbnails(val thumbnails: List<Thumbnail>? = null)

    @Serializable
    data class Thumbnail(val url: String, val width: Int? = null, val height: Int? = null)

    @Serializable
    data class PlayerAd(val playerLegacyDesktopWatchAdsRenderer: JsonElement? = null)

    @Serializable
    data class PlaybackTracking(
        val videostatsPlaybackUrl: VideostatsUrl? = null,
        val videostatsWatchtimeUrl: VideostatsUrl? = null
    )

    @Serializable
    data class VideostatsUrl(val baseUrl: String? = null)

    @Serializable
    data class AudioConfig(
        val loudnessDb: Double? = null,
        val perceptualLoudnessDb: Double? = null
    )
}
