package com.appplayer.innertube.models.body

import com.appplayer.innertube.models.YouTubeClient
import kotlinx.serialization.Serializable

@Serializable
data class Context(
    val client: ClientContext,
    val user: UserContext? = null,
    val thirdParty: ThirdPartyContext? = null
) {
    @Serializable
    data class ClientContext(
        val clientName: String,
        val clientVersion: String,
        val gl: String = "US",
        val hl: String = "en",
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val platform: String? = null,
        val androidSdkVersion: String? = null,
        val userAgent: String? = null,
        val visitorData: String? = null
    )

    @Serializable
    data class UserContext(val lockedSafetyMode: Boolean = false)

    @Serializable
    data class ThirdPartyContext(val embedUrl: String)

    companion object {
        /**
         * Build a Context from a YouTubeClient definition.
         *
         * Critical: uses per-client osName/osVersion/deviceMake/deviceModel from the
         * YouTubeClient instance itself (matching ArchiveTune's youtube_clients.json) rather
         * than inferring from clientName. Using wrong device context (e.g., "Pixel 6"/"Android 12"
         * for ANDROID_VR which should be "Quest 3"/"Android 12L") causes YouTube's /player
         * endpoint to return LOGIN_REQUIRED or UNAVAILABLE playability status.
         */
        fun fromClient(client: YouTubeClient, visitorData: String? = null): Context {
            val name = client.clientName.uppercase()
            val isAndroid = name.startsWith("ANDROID")
            val isIos = name.startsWith("IOS")
            val isVisionOs = name == "VISIONOS"
            val isWeb = name.startsWith("WEB") || name == "WEB"
            val isTv = name.startsWith("TV") || name.startsWith("TVHTML")
            val isMweb = name == "MWEB"

            // Use the client's own metadata if available; otherwise fall back to name-based inference
            val osName = client.osName ?: when {
                isAndroid -> "Android"
                isVisionOs -> "visionOS"
                isIos -> "iPhone"
                isWeb || isMweb -> null  // Web clients don't send osName in context
                isTv -> null
                else -> null
            }
            val osVersion = client.osVersion ?: when {
                isAndroid -> "11"
                isVisionOs -> "1.3.21O771"
                isIos -> "17.5.1.21F90"
                else -> null
            }
            val platform = when {
                isAndroid || isIos || isVisionOs -> "MOBILE"
                isWeb || isMweb -> "DESKTOP"
                isTv -> "TV"
                else -> null
            }
            val deviceMake = client.deviceMake ?: when {
                isAndroid -> null  // Don't default — wrong make causes 403 for VR clients
                isVisionOs || isIos -> "Apple"
                else -> null
            }
            val deviceModel = client.deviceModel ?: when {
                isVisionOs -> "RealityDevice14,1"
                else -> null
            }

            return Context(
                client = ClientContext(
                    clientName = client.clientName,
                    clientVersion = client.clientVersion,
                    osName = osName,
                    osVersion = osVersion,
                    platform = platform,
                    deviceMake = deviceMake,
                    deviceModel = deviceModel,
                    androidSdkVersion = client.androidSdkVersion,
                    userAgent = client.userAgent,
                    visitorData = visitorData
                )
            )
        }
    }
}

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String? = null,
    val racyCheckOk: Boolean = true,
    val contentCheckOk: Boolean = true,
    val playbackContext: PlaybackContext? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null
) {
    @Serializable
    data class PlaybackContext(val contentPlaybackContext: ContentPlaybackContext) {
        @Serializable
        data class ContentPlaybackContext(val signatureTimestamp: Int)
    }

    @Serializable
    data class ServiceIntegrityDimensions(val poToken: String)
}

@Serializable
data class SearchBody(
    val context: Context,
    val query: String? = null,
    val params: String? = null
)

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String? = null,
    val params: String? = null,
    val continuation: String? = null
)

@Serializable
data class NextBody(
    val context: Context,
    val videoId: String? = null,
    val playlistId: String? = null,
    val params: String? = null,
    val index: Int? = null,
    val continuation: String? = null
)
