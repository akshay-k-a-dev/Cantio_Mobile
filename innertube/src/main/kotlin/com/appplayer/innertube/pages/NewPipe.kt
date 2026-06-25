package com.appplayer.innertube.pages

import com.appplayer.innertube.models.YouTubeClient
import com.appplayer.innertube.models.response.PlayerResponse
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.Proxy

private class NewPipeDownloaderImpl(
    proxy: Proxy?,
    proxyAuth: String?,
) : Downloader() {
    private fun normalizeResponseBody(
        url: String,
        body: String?,
    ): String? {
        if (!url.contains("returnyoutubedislikeapi.com", ignoreCase = true)) {
            return body
        }

        val trimmed = body?.trimStart().orEmpty()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return body
        }

        return "{\"likes\":0,\"dislikes\":0,\"viewCount\":0}"
    }

    private val client =
        OkHttpClient
            .Builder()
            .proxy(proxy)
            .proxyAuthenticator { _, response ->
                proxyAuth?.let { auth ->
                    response.request
                        .newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }.build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder =
            okhttp3.Request
                .Builder()
                .method(httpMethod, dataToSend?.toRequestBody())
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")

        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()

            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val latestUrl = response.request.url.toString()
        val responseBodyToReturn = normalizeResponseBody(latestUrl, response.body?.string())
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyToReturn,
            responseBodyToReturn?.toByteArray(),
            latestUrl,
        )
    }

    override fun executeAsync(request: Request, callback: AsyncCallback?): CancellableCall {
        TODO("Placeholder")
    }
}

object NewPipeUtils {
    init {
        NewPipe.init(NewPipeDownloaderImpl(null, null))
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> = runCatching {
        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
    }

    fun getStreamUrl(format: PlayerResponse.Format, videoId: String): Result<String> =
        runCatching {
            val url =
                format.url ?: (format.signatureCipher ?: format.cipher)?.let { signatureCipher ->
                    val params = parseQueryString(signatureCipher)
                    val obfuscatedSignature =
                        params["s"]
                            ?: throw ParsingException("Could not parse cipher signature")
                    val signatureParam =
                        params["sp"]
                            ?: throw ParsingException("Could not parse cipher signature parameter")
                    val urlBuilder =
                        params["url"]?.let { URLBuilder(it) }
                            ?: throw ParsingException("Could not parse cipher url")
                    urlBuilder.parameters[signatureParam] =
                        YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                            videoId,
                            obfuscatedSignature,
                        )
                    urlBuilder.buildString()
                } ?: throw ParsingException("Could not find format url")

            return@runCatching YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId,
                url,
            )
        }
}
