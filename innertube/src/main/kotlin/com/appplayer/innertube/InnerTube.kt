package com.appplayer.innertube

import com.appplayer.innertube.models.YouTubeClient
import com.appplayer.innertube.models.body.Context
import com.appplayer.innertube.models.body.PlayerBody
import com.appplayer.innertube.models.body.SearchBody
import com.appplayer.innertube.models.body.BrowseBody
import com.appplayer.innertube.models.body.NextBody
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import io.ktor.http.HttpHeaders

/**
 * Low-level InnerTube HTTP client.
 *
 * Mirrors Metrolist's InnerTube.kt.
 * Responsible only for making HTTP requests — not parsing responses.
 */
class InnerTube {

    @OptIn(ExperimentalSerializationApi::class)
    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
                isLenient = true
            })
        }

        install(ContentEncoding) {
            gzip(0.9F)
            deflate(0.8F)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }

        engine {
            config {
                connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
                protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                retryOnConnectionFailure(true)
            }
        }

        defaultRequest {
            url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
            header("Accept", "application/json")
            header("Accept-Language", "en-US,en;q=0.9")
        }
    }

    var visitorData: String? = null
    var cookie: String? = null

    // ─── Auth header builder ──────────────────────────────────────────────────

    private fun HttpRequestBuilder.ytClient(client: YouTubeClient, setLogin: Boolean = false) {
        contentType(ContentType.Application.Json)

        // Dynamically adjust host and prefix based on the client to avoid 400 Bad Request
        val isMusicClient = client.clientName.contains("MUSIC") || client.clientName.contains("REMIX")
        val baseUrl = if (isMusicClient) YouTubeClient.API_URL_YOUTUBE_MUSIC else YouTubeClient.API_URL
        val parsedUrl = io.ktor.http.Url(baseUrl)
        url.protocol = parsedUrl.protocol
        url.host = parsedUrl.host
        val currentEndpoint = url.pathSegments.lastOrNull { it.isNotEmpty() } ?: ""
        if (currentEndpoint.isNotEmpty()) {
            url.pathSegments = parsedUrl.pathSegments.filter { it.isNotEmpty() } + currentEndpoint
        }

        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            client.origin?.let { append("X-Origin", it) }
            client.referer?.let { append("Referer", it) }
            visitorData?.let { append("X-Goog-Visitor-Id", it) }
            if (setLogin) {
                cookie?.let { append("cookie", it) }
            }
        }
        header(HttpHeaders.UserAgent, client.userAgent)
        parameter("prettyPrint", false)
        parameter("key", client.apiKey)
    }

    // ─── Retry helper ─────────────────────────────────────────────────────────

    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var delay = 500L
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    Timber.w(e, "InnerTube request failed (attempt ${attempt + 1}), retrying in ${delay}ms")
                    kotlinx.coroutines.delay(delay)
                    delay = (delay * 2).coerceAtMost(5000L)
                }
            }
        }
        throw lastException!!
    }

    // ─── Endpoints ───────────────────────────────────────────────────────────

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String? = null,
        signatureTimestamp: Int? = null,
        poToken: String? = null
    ): HttpResponse = withRetry {
        httpClient.post("player") {
            ytClient(client, setLogin = true)
            setBody(
                PlayerBody(
                    context = Context.fromClient(client, visitorData),
                    videoId = videoId,
                    playlistId = playlistId,
                    playbackContext = if (client.useSignatureTimestamp && signatureTimestamp != null) {
                        PlayerBody.PlaybackContext(
                            PlayerBody.PlaybackContext.ContentPlaybackContext(signatureTimestamp)
                        )
                    } else null,
                    serviceIntegrityDimensions = if (client.useWebPoTokens && poToken != null) {
                        PlayerBody.ServiceIntegrityDimensions(poToken)
                    } else null
                )
            )
        }
    }

    suspend fun search(
        client: YouTubeClient,
        query: String,
        params: String? = null,
        continuation: String? = null
    ): HttpResponse = withRetry {
        httpClient.post("search") {
            ytClient(client)
            setBody(SearchBody(context = Context.fromClient(client, visitorData), query = query, params = params))
            parameter("continuation", continuation)
        }
    }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null
    ): HttpResponse = withRetry {
        httpClient.post("browse") {
            ytClient(client, setLogin = true)
            setBody(BrowseBody(context = Context.fromClient(client, visitorData), browseId = browseId, params = params, continuation = continuation))
        }
    }

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String? = null,
        params: String? = null,
        continuation: String? = null
    ): HttpResponse = withRetry {
        httpClient.post("next") {
            ytClient(client, setLogin = true)
            setBody(NextBody(context = Context.fromClient(client, visitorData), videoId = videoId, playlistId = playlistId, params = params, continuation = continuation))
        }
    }

    suspend fun getSwJsData(): HttpResponse = withRetry {
        httpClient.get("https://music.youtube.com/sw.js_data")
    }
}
