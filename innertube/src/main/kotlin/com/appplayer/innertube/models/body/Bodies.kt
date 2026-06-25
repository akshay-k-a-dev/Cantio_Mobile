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
        val userAgent: String? = null,
        val visitorData: String? = null
    )

    @Serializable
    data class UserContext(val lockedSafetyMode: Boolean = false)

    @Serializable
    data class ThirdPartyContext(val embedUrl: String)

    companion object {
        fun fromClient(client: YouTubeClient, visitorData: String? = null): Context {
            val name = client.clientName
            val isAndroid = name.contains("ANDROID")
            val isIos = name.contains("IOS") || name == "IOS"
            val isWeb = name.contains("WEB") || name == "WEB"
            val isTv = name.contains("TV")

            val osName = when {
                isAndroid -> "Android"
                isIos -> "iOS"
                isWeb -> "Windows"
                isTv -> "Tizen"
                else -> null
            }
            val osVersion = when {
                isAndroid -> "12"
                isIos -> "17.4.1"
                isWeb -> "10.0"
                else -> null
            }
            val platform = when {
                isAndroid || isIos -> "MOBILE"
                isWeb -> "DESKTOP"
                isTv -> "TV"
                else -> null
            }
            val deviceMake = when {
                isAndroid -> "Google"
                isIos -> "Apple"
                else -> null
            }
            val deviceModel = when {
                isAndroid -> "Pixel 6"
                isIos -> "iPhone"
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
