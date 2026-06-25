package com.appplayer.music.utils

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.PlaybackException
import com.appplayer.innertube.InnerTube
import com.appplayer.innertube.models.YouTubeClient
import com.appplayer.innertube.models.response.PlayerResponse
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackData(
    val streamUrl: String,
    val streamClient: String,
    val streamExpiresInSeconds: Long,
    val format: StreamFormat
)

data class StreamFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Long,
    val contentLength: Long?
)

@Singleton
class YTPlayerUtils @Inject constructor(
    private val innerTube: InnerTube
) {

    // Track which clients are temporarily blocked (403/416 responses)
    // Maps clientName → unblock timestamp (ms)
    private val blockedClients = ConcurrentHashMap<String, Long>()

    // Remember the last successful client for LRU promotion
    @Volatile private var lastSuccessfulClient: YouTubeClient? = null

    /**
     * Resolve a playback stream for a given videoId.
     *
     * Strategy (derived from ArchiveTune/YTPlayerUtils):
     * 1. Try last successful client first (LRU promotion)
     * 2. Try preferred client (ANDROID_VR — no auth needed, fast)
     * 3. Fallback through all known clients
     *
     * Each client attempt:
     *   - Skip if blocked for < 10 min (403/404/416 backoff)
     *   - Call InnerTube.player() with appropriate context
     *   - Check playabilityStatus
     *   - Select best audio-only adaptive format
     *   - Return PlaybackData
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        connectivityManager: ConnectivityManager? = null
    ): Result<PlaybackData> {
        val clientOrder = buildClientOrder()

        for (client in clientOrder) {
            val clientName = client.clientName

            // Skip temporarily blocked clients
            val blockedUntil = blockedClients[clientName]
            if (blockedUntil != null && System.currentTimeMillis() < blockedUntil) {
                Timber.d("Skipping blocked client: $clientName (unblocks in ${(blockedUntil - System.currentTimeMillis()) / 1000}s)")
                continue
            }

            try {
                Timber.d("Trying client: $clientName for videoId=$videoId")
                val result = tryClient(client, videoId)
                if (result != null) {
                    lastSuccessfulClient = client
                    return Result.success(result)
                }
            } catch (e: Exception) {
                Timber.w(e, "Client $clientName failed for $videoId")
                // Block client on 403-like errors
                if (isBlockingError(e)) {
                    blockedClients[clientName] = System.currentTimeMillis() + BLOCK_DURATION_MS
                    Timber.w("Blocked client $clientName for ${BLOCK_DURATION_MS / 60_000} min")
                }
            }
        }

        return Result.failure(
            PlaybackException(
                "No working stream client found for $videoId",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        )
    }

    private suspend fun tryClient(client: YouTubeClient, videoId: String): PlaybackData? {
        val signatureTimestamp = if (client.useSignatureTimestamp) {
            com.appplayer.innertube.pages.NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        } else {
            null
        }

        if (client.useWebPoTokens && innerTube.visitorData == null) {
            try {
                Timber.d("visitorData is null, fetching from sw.js_data...")
                val visitorDataResponse = innerTube.getSwJsData()
                val text = visitorDataResponse.body<String>()
                val cleaned = if (text.startsWith(")]}'")) text.substring(5) else text
                val parsed = Json.parseToJsonElement(cleaned)
                var foundVisitorData: String? = null
                val array = parsed.jsonArray[0].jsonArray[2].jsonArray
                for (element in array) {
                    if (element is JsonPrimitive) {
                        val content = element.contentOrNull
                        if (content != null && VISITOR_DATA_REGEX.containsMatchIn(content)) {
                            foundVisitorData = content
                            break
                        }
                    }
                }
                if (foundVisitorData != null) {
                    innerTube.visitorData = foundVisitorData
                    Timber.d("Successfully fetched and cached visitorData: $foundVisitorData")
                } else {
                    Timber.w("visitorData not found in sw.js_data array")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch visitorData from sw.js_data")
            }
        }

        val poTokenResult = if (client.useWebPoTokens) {
            val sessionId = innerTube.visitorData ?: java.util.UUID.randomUUID().toString()
            com.appplayer.music.utils.potoken.BotGuardTokenGenerator.mintToken(videoId, sessionId)
        } else {
            null
        }

        val response = innerTube.player(
            client = client,
            videoId = videoId,
            playlistId = null,
            signatureTimestamp = signatureTimestamp,
            poToken = poTokenResult?.playerToken
        )

        val body = response.body<PlayerResponse>()

        // Check playability
        val status = body.playabilityStatus?.status
        if (status != "OK") {
            val reason = body.playabilityStatus?.reason
            val errorText = body.playabilityStatus?.errorScreen?.playerErrorMessageRenderer?.reason?.runs?.firstOrNull()?.text
            Timber.d("Client ${client.clientName} returned status=$status, reason=$reason, errorText=$errorText for $videoId")
            return null
        }

        // Find best audio-only adaptive format
        val streamingData = body.streamingData ?: return null
        val format = selectBestAudioFormat(streamingData.adaptiveFormats ?: emptyList())
            ?: selectBestAudioFormat(streamingData.formats ?: emptyList())
            ?: return null

        val streamUrlResult = com.appplayer.innertube.pages.NewPipeUtils.getStreamUrl(format, videoId)
        val streamUrl = streamUrlResult.getOrNull() ?: return null

        var finalStreamUrl = streamUrl
        if (poTokenResult != null) {
            val separator = if ("?" in finalStreamUrl) "&" else "?"
            finalStreamUrl = "$finalStreamUrl${separator}pot=${poTokenResult.sessionToken}"
        }

        val expirySeconds = extractExpireSecondsFromUrl(finalStreamUrl)

        return PlaybackData(
            streamUrl = finalStreamUrl,
            streamClient = client.clientName,
            streamExpiresInSeconds = expirySeconds,
            format = StreamFormat(
                itag = format.itag ?: 0,
                mimeType = format.mimeType ?: "audio/mp4",
                bitrate = format.bitrate?.toLong() ?: 0L,
                contentLength = format.contentLength?.toLong()
            )
        )
    }

    /**
     * Select the best audio-only adaptive format.
     * Priority: Opus/WebM > M4A/MP4 > highest bitrate
     */
    private fun selectBestAudioFormat(formats: List<PlayerResponse.Format>): PlayerResponse.Format? {
        val audioFormats = formats.filter { f ->
            val mime = f.mimeType ?: return@filter false
            (mime.startsWith("audio/") || mime.contains("audio")) &&
            (f.url != null || f.signatureCipher != null || f.cipher != null) &&
            f.audioSampleRate != null
        }

        if (audioFormats.isEmpty()) return null

        return audioFormats.maxByOrNull { f ->
            var score = f.bitrate?.toLong() ?: 0L
            // Prefer opus (better quality per bit)
            if (f.mimeType?.contains("opus") == true) score += 50_000
            score
        }
    }

    /**
     * Extract stream expiry from URL's `expire=` parameter.
     * Falls back to 5 minutes if not found.
     */
    private fun extractExpireSecondsFromUrl(url: String): Long {
        return try {
            val expireParam = url.split("&").firstOrNull { it.startsWith("expire=") }
                ?: url.split("?").getOrNull(1)?.split("&")?.firstOrNull { it.startsWith("expire=") }
            val expireTimestamp = expireParam?.removePrefix("expire=")?.toLong() ?: return 300L
            val remaining = expireTimestamp - (System.currentTimeMillis() / 1000L)
            remaining.coerceAtLeast(60L) // minimum 60s
        } catch (e: Exception) {
            300L // default 5 min
        }
    }

    private fun buildClientOrder(): List<YouTubeClient> {
        val ordered = mutableListOf<YouTubeClient>()

        // 1. Last successful client (LRU promotion)
        lastSuccessfulClient?.let { ordered.add(it) }

        // 2. Preferred: ANDROID_VR (no auth needed, works without login)
        if (ordered.none { it.clientName == YouTubeClient.ANDROID_VR.clientName }) {
            ordered.add(YouTubeClient.ANDROID_VR)
        }

        // 3. Full fallback list
        val fallbacks = listOf(
            YouTubeClient.IOS,
            YouTubeClient.ANDROID_MUSIC,
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.IOS_MUSIC,
            YouTubeClient.WEB_REMIX,
            YouTubeClient.WEB,
            YouTubeClient.TVHTML5,
        )

        for (client in fallbacks) {
            if (ordered.none { it.clientName == client.clientName }) {
                ordered.add(client)
            }
        }

        return ordered
    }

    private fun isBlockingError(e: Exception): Boolean {
        val message = e.message ?: return false
        return message.contains("403") || message.contains("416") ||
            message.contains("404") || message.contains("410")
    }

    fun blockClient(clientName: String) {
        blockedClients[clientName] = System.currentTimeMillis() + BLOCK_DURATION_MS
        Timber.w("Blocked client $clientName for ${BLOCK_DURATION_MS / 60_000} min")
    }

    companion object {
        private const val BLOCK_DURATION_MS = 10 * 60 * 1000L // 10 minutes
        private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")
    }
}

