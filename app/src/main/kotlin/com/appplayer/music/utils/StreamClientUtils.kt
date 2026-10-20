package com.appplayer.music.utils

import com.appplayer.innertube.models.YouTubeClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.Locale

/**
 * Shared utility for resolving the correct User-Agent, Origin, and Referer headers
 * based on the stream client query parameters embedded in YouTube stream URLs.
 *
 * Ported and aligned from ArchiveTune's StreamClientUtils.kt to ensure reliable
 * stream playback without 403s.
 *
 * Key difference from a naïve approach: headers are applied conditionally (only if
 * not already present on the request) — forcibly overriding headers breaks requests
 * where the caller has already set the correct values.
 */
object StreamClientUtils {

    data class StreamRequestProfile(
        val requestedClientName: String,
        val userAgent: String,
        val origin: String?,
        val referer: String?
    )

    fun resolveRequestProfile(url: String): StreamRequestProfile =
        resolveRequestProfile(url.toHttpUrlOrNull())

    fun resolveRequestProfile(url: HttpUrl?): StreamRequestProfile =
        resolveRequestProfile(
            clientParam = url?.queryParameter("c"),
            clientVersion = url?.queryParameter("cver")
        )

    fun resolveRequestProfile(
        clientParam: String?,
        clientVersion: String? = null
    ): StreamRequestProfile {
        val clientName = clientParam?.trim()?.uppercase(Locale.US).orEmpty()

        val client = resolveClient(clientName)

        val (origin, referer) = resolveOriginReferer(client)

        return StreamRequestProfile(
            requestedClientName = clientName.ifEmpty { client.clientName },
            userAgent = client.userAgent,
            origin = origin,
            referer = referer
        )
    }

    /**
     * Apply request profile headers to [requestBuilder].
     *
     * IMPORTANT: Headers are only set if not already present on the request,
     * matching ArchiveTune's interceptor behaviour. Forcibly overriding headers
     * can cause 403s if the caller has already set the correct values.
     */
    fun applyRequestProfile(
        requestBuilder: Request.Builder,
        profile: StreamRequestProfile
    ): Request.Builder {
        val existingRequest = requestBuilder.build()
        if (existingRequest.header("User-Agent") == null) {
            requestBuilder.header("User-Agent", profile.userAgent)
        }
        if (profile.origin != null) {
            if (existingRequest.header("Origin") == null) {
                requestBuilder.header("Origin", profile.origin)
            }
        } else {
            requestBuilder.removeHeader("Origin")
        }
        if (profile.referer != null) {
            if (existingRequest.header("Referer") == null) {
                requestBuilder.header("Referer", profile.referer)
            }
        } else {
            requestBuilder.removeHeader("Referer")
        }
        return requestBuilder
    }

    private fun resolveClient(clientName: String): YouTubeClient =
        when {
            clientName == "WEB_REMIX" -> YouTubeClient.WEB_REMIX
            clientName == "WEB" -> YouTubeClient.WEB
            clientName == "MWEB" -> YouTubeClient.MWEB
            clientName == "TVHTML5" -> YouTubeClient.TVHTML5
            clientName == "IOS_MUSIC" -> YouTubeClient.IOS_MUSIC
            clientName == "ANDROID_MUSIC" -> YouTubeClient.ANDROID_MUSIC
            clientName == "VISIONOS" -> YouTubeClient.VISIONOS
            clientName.startsWith("IOS") -> YouTubeClient.IOS
            clientName.startsWith("ANDROID_VR") -> YouTubeClient.ANDROID_VR
            clientName == "ANDROID" -> YouTubeClient.MOBILE
            clientName.startsWith("ANDROID") -> YouTubeClient.ANDROID_MUSIC
            else -> YouTubeClient.ANDROID_VR  // safe default: no Origin/Referer needed
        }

    private fun resolveOriginReferer(client: YouTubeClient): Pair<String?, String?> =
        when (client.clientName.uppercase(Locale.US)) {
            "WEB_REMIX" -> Pair("https://music.youtube.com", "https://music.youtube.com/")
            "WEB" -> Pair("https://www.youtube.com", "https://www.youtube.com/")
            "MWEB" -> Pair("https://m.youtube.com", "https://m.youtube.com/")
            "TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "TVHTML5_SIMPLY" ->
                Pair("https://www.youtube.com", "https://www.youtube.com/tv")
            else -> Pair(null, null)  // native/app clients don't need Origin/Referer
        }
}
