package com.appplayer.music

import com.appplayer.innertube.InnerTube
import com.appplayer.innertube.models.YouTubeClient
import com.appplayer.innertube.models.response.PlayerResponse
import com.appplayer.music.utils.StreamClientUtils
import com.appplayer.music.utils.YTPlayerUtils
import io.ktor.client.call.body
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test

class PlaybackTest {
    @Test
    fun testStreamResolutionAndFetch() {
        timber.log.Timber.uprootAll()
        timber.log.Timber.plant(object : timber.log.Timber.DebugTree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                println("[$tag] $message")
                t?.printStackTrace()
            }
        })
        runBlocking {
            val innerTube = InnerTube()
            val ytPlayerUtils = YTPlayerUtils(innerTube)

            val testVideos = listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk", "fJ9rUzIMcZQ")

            val mediaOkHttpClient = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeHost = host.endsWith("googlevideo.com") ||
                        host.endsWith("googleusercontent.com") ||
                        host.endsWith("youtube.com")

                    if (!isYouTubeHost) {
                        chain.proceed(request)
                    } else {
                        val profile = StreamClientUtils.resolveRequestProfile(request.url)
                        val builder = request.newBuilder()
                        StreamClientUtils.applyRequestProfile(builder, profile)
                        chain.proceed(builder.build())
                    }
                }
                .build()

            for (videoId in testVideos) {
                println("--- Resolving stream for $videoId ---")
                val result = ytPlayerUtils.playerResponseForPlayback(videoId)
                println("Result isSuccess: ${result.isSuccess}")
                if (!result.isSuccess) {
                    val ex = result.exceptionOrNull()
                    println("Error: $ex")
                    ex?.printStackTrace()
                }
                assert(result.isSuccess) { "Stream resolution failed for $videoId" }

                val data = result.getOrThrow()
                println("Stream URL: ${data.streamUrl.take(80)}...")
                println("Client: ${data.streamClient}")
                println("Format: ${data.format}")

                println("Testing HTTP GET for stream URL...")
                val req = Request.Builder()
                    .url(data.streamUrl)
                    .header("Range", "bytes=0-1024")
                    .build()

                val resp = mediaOkHttpClient.newCall(req).execute()
                println("HTTP Response Code: ${resp.code}")
                println("HTTP Response Message: ${resp.message}")
                println("Content-Type: ${resp.header("Content-Type")}")
                println("Content-Range: ${resp.header("Content-Range")}")
                val bodyBytes = resp.body?.bytes()
                println("Read ${bodyBytes?.size} bytes successfully!")
                resp.close()

                assert(resp.code == 206 || resp.code == 200) { "Expected 206 or 200, got ${resp.code}" }
                assert((bodyBytes?.size ?: 0) > 0) { "Expected non-empty body" }
            }
        }
    }
}
