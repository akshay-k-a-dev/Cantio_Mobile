package com.appplayer.music.utils

import android.net.ConnectivityManager
import androidx.media3.common.PlaybackException
import com.appplayer.innertube.InnerTube
import com.appplayer.innertube.models.YouTubeClient
import com.appplayer.innertube.models.response.PlayerResponse
import com.appplayer.innertube.pages.NewPipeUtils
import com.appplayer.music.utils.potoken.BotGuardTokenGenerator
import io.ktor.client.call.body
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Resolve a playback stream for a given videoId.
     *
     * Strategy (from ArchiveTune/YTPlayerUtils):
     * 1. Try last successful client first (LRU promotion)
     * 2. Try VISIONOS (no auth needed, not bot-detected as easily)
     * 3. Try ANDROID_VR (no auth, well-known fallback)
     * 4. Try IOS (reliable, no cipher needed)
     * 5. Full fallback through: ANDROID_MUSIC, WEB_REMIX, WEB, TVHTML5, MWEB
     *
     * For each client:
     *   - Skip if temporarily blocked (403/404/410/416 backoff for 10 min)
     *   - Fetch signatureTimestamp via NewPipe if needed (web clients)
     *   - Fetch visitorData if needed (web clients with PoToken)
     *   - Mint PoToken and send in request body (NOT in URL)
     *   - Check playabilityStatus == "OK"
     *   - Select best audio-only adaptive format
     *   - Decipher URL if signatureCipher / throttle via NewPipe
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        connectivityManager: ConnectivityManager? = null
    ): Result<PlaybackData> {
        val clientOrder = buildClientOrder()

        for (client in clientOrder) {
            val clientName = "${client.clientName}@${client.clientVersion}"

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
                    Timber.i("Stream resolved via $clientName for $videoId (expires in ${result.streamExpiresInSeconds}s)")
                    return Result.success(result)
                }
                // null = playability status not OK — not a hard error, just try next client
                Timber.w("Client $clientName: playabilityStatus not OK for $videoId")
            } catch (e: Exception) {
                Timber.w(e, "Client $clientName threw exception for $videoId")
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

    fun blockClient(clientName: String) {
        blockedClients[clientName] = System.currentTimeMillis() + BLOCK_DURATION_MS
        Timber.w("Blocked client $clientName for ${BLOCK_DURATION_MS / 60_000} min")
    }

    // ─── Internal resolution ──────────────────────────────────────────────────

    private suspend fun tryClient(client: YouTubeClient, videoId: String): PlaybackData? {
        // 1. Get signatureTimestamp via NewPipe (web clients need this for cipher decoding)
        val signatureTimestamp = if (client.useSignatureTimestamp) {
            NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        } else {
            null
        }

        // 2. Ensure visitorData is present for web clients that need PoTokens
        if (client.useWebPoTokens && innerTube.visitorData.isNullOrBlank()) {
            ensureVisitorData(videoId)
        }

        // 3. Mint PoToken — goes in the request body ServiceIntegrityDimensions, NOT in the URL
        val poToken: String? = if (client.useWebPoTokens) {
            val sessionId = innerTube.visitorData ?: return null
            try {
                BotGuardTokenGenerator.mintToken(videoId, sessionId)?.playerToken
            } catch (e: Exception) {
                Timber.w(e, "PoToken generation failed for $videoId via ${client.clientName} — proceeding without token")
                null
            }
        } else {
            null
        }

        // 4. Make InnerTube /player request
        val useLogin = client.loginSupported && innerTube.cookie != null
        val response = innerTube.player(
            client = client,
            videoId = videoId,
            playlistId = null,
            signatureTimestamp = signatureTimestamp,
            poToken = poToken,
            setLogin = useLogin
        )

        val body = response.body<PlayerResponse>()

        // 5. Check playability
        val status = body.playabilityStatus?.status
        if (status != "OK") {
            val reason = body.playabilityStatus?.reason
            Timber.d("${client.clientName}: status=$status reason=$reason for $videoId")
            return null
        }

        // 6. Find best audio-only adaptive format
        val streamingData = body.streamingData ?: run {
            Timber.w("${client.clientName}: streamingData null for $videoId")
            return null
        }
        val allFormats = (streamingData.adaptiveFormats ?: emptyList()) + (streamingData.formats ?: emptyList())
        val format = selectBestAudioFormat(allFormats) ?: run {
            Timber.w("${client.clientName}: no audio format found for $videoId")
            return null
        }

        // 7. Resolve URL — deciphers signatureCipher & deobfuscates throttle parameter via NewPipe
        val streamUrl = NewPipeUtils.getStreamUrl(format, videoId).getOrElse { e ->
            Timber.e(e, "${client.clientName}: failed to get stream URL for $videoId")
            return null
        }

        val expirySeconds = extractExpireSecondsFromUrl(streamUrl)

        return PlaybackData(
            streamUrl = streamUrl,
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
     * Fetch visitorData from YouTube Music home page.
     * This is a simpler and more reliable approach than parsing sw.js_data.
     */
    private suspend fun ensureVisitorData(videoId: String) {
        try {
            Timber.d("Fetching visitorData for $videoId")
            val response = innerTube.visitorDataRequest()
            val vd = response.body<String>()
                .let { body ->
                    // Extract from X-Goog-Visitor-Id header or from response body
                    // The response header is the most reliable source
                    response.headers["X-Goog-Visitor-Id"]
                        ?: extractVisitorDataFromBody(body)
                }
            if (!vd.isNullOrBlank()) {
                innerTube.visitorData = vd
                Timber.d("Fetched visitorData: $vd")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch visitorData — web PoToken clients may fail")
        }
    }

    private fun extractVisitorDataFromBody(body: String): String? {
        // visitorData strings start with "Cgt" or "Cgs" and are base64-encoded protobuf
        val regex = Regex("""["']?(Cg[ts][A-Za-z0-9_\-+=]{8,50})["']?""")
        return regex.find(body)?.groupValues?.getOrNull(1)
    }

    // ─── Format selection ─────────────────────────────────────────────────────

    /**
     * Select the best audio-only adaptive format.
     *
     * Priority (from ArchiveTune):
     *   1. Direct URL over signatureCipher (less fragile)
     *   2. Higher bitrate
     *   3. Codec: opus > mp4a > other
     *   4. Higher sample rate
     */
    private fun selectBestAudioFormat(formats: List<PlayerResponse.Format>): PlayerResponse.Format? {
        // 1. Try pure audio-only adaptive formats first
        val pureAudioFormats = formats.filter { f ->
            val mime = f.mimeType ?: return@filter false
            // Must be audio-only (no width/height) and have a resolvable URL
            f.width == null &&
            f.height == null &&
            (mime.startsWith("audio/") || f.audioQuality != null) &&
            (f.url != null || f.signatureCipher != null || f.cipher != null) &&
            (f.bitrate ?: 0) > 0
        }

        if (pureAudioFormats.isNotEmpty()) {
            return pureAudioFormats.sortedWith(
                compareByDescending<PlayerResponse.Format> { it.url != null } // direct URLs first
                    .thenByDescending { it.bitrate ?: 0 }
                    .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                    .thenByDescending { it.audioSampleRate?.toIntOrNull() ?: 0 }
            ).firstOrNull()
        }

        // 2. Fallback: Muxed formats containing an audio track (e.g., itag 18 MP4 with AAC)
        // ExoPlayer automatically extracts and plays the audio track from muxed containers
        val muxedAudioFormats = formats.filter { f ->
            val mime = f.mimeType ?: return@filter false
            (f.audioQuality != null || mime.contains("mp4a", ignoreCase = true) || mime.contains("opus", ignoreCase = true) || f.itag == 18) &&
            (f.url != null || f.signatureCipher != null || f.cipher != null) &&
            (f.bitrate ?: 0) > 0
        }

        return muxedAudioFormats.sortedWith(
            compareByDescending<PlayerResponse.Format> { it.url != null } // direct URLs first
                .thenByDescending { it.bitrate ?: 0 }
        ).firstOrNull()
    }

    private fun extractCodec(mimeType: String?): String? {
        val match = Regex("""codecs="([^"]+)"""").find(mimeType ?: return null) ?: return null
        return match.groupValues.getOrNull(1)?.split(",")?.firstOrNull()?.trim()
    }

    private fun codecRank(codec: String?): Int = when {
        codec.isNullOrBlank() -> 0
        codec.contains("opus", ignoreCase = true) -> 3
        codec.contains("mp4a", ignoreCase = true) -> 2
        else -> 1
    }

    // ─── Client ordering ─────────────────────────────────────────────────────

    /**
     * Build the client attempt order:
     * 1. Last successful client (LRU)
     * 2. VISIONOS (low bot-detection, direct Opus/AAC streams for standard tracks)
     * 3. MOBILE (Android native client - returns direct unthrottled streams when VISIONOS is bot-gated)
     * 4. ANDROID_VR (well-known no-auth fallback)
     * 5. IOS (reliable, no cipher)
     * 6. ANDROID_MUSIC, IOS_MUSIC, WEB_REMIX, WEB, TVHTML5, MWEB
     */
    private fun buildClientOrder(): List<YouTubeClient> {
        val ordered = mutableListOf<YouTubeClient>()

        lastSuccessfulClient?.let { ordered.add(it) }

        val fallbacks = listOf(
            YouTubeClient.VISIONOS,
            YouTubeClient.MOBILE,
            YouTubeClient.ANDROID_VR,
            YouTubeClient.IOS,
            YouTubeClient.ANDROID_MUSIC,
            YouTubeClient.IOS_MUSIC,
            YouTubeClient.WEB_REMIX,
            YouTubeClient.WEB,
            YouTubeClient.TVHTML5,
            YouTubeClient.MWEB,
        )

        for (client in fallbacks) {
            if (ordered.none { it.clientName == client.clientName && it.clientVersion == client.clientVersion }) {
                ordered.add(client)
            }
        }

        return ordered
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Extract stream expiry from URL's `expire=` Unix timestamp parameter.
     * Falls back to 300 s if not found.
     */
    private fun extractExpireSecondsFromUrl(url: String): Long {
        return try {
            val expireTimestamp = url.toHttpUrlOrNull()
                ?.queryParameter("expire")
                ?.toLongOrNull()
                ?: return DEFAULT_EXPIRE_SECONDS
            val remaining = expireTimestamp - (System.currentTimeMillis() / 1000L)
            remaining.coerceAtLeast(60L)
        } catch (e: Exception) {
            DEFAULT_EXPIRE_SECONDS
        }
    }

    private fun isBlockingError(e: Exception): Boolean {
        val message = e.message ?: return false
        return message.contains("403") || message.contains("416") ||
            message.contains("404") || message.contains("410")
    }

    companion object {
        private const val BLOCK_DURATION_MS = 10 * 60 * 1000L // 10 minutes
        private const val DEFAULT_EXPIRE_SECONDS = 300L
        /** Safety margin: treat stream as expired 60 s before it actually expires. */
        const val STREAM_URL_EXPIRY_SAFETY_MS = 60_000L
    }
}
