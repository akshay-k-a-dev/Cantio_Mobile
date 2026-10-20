package com.appplayer.music.utils

private const val PlayerArtworkHighResPx = 1080
private const val YtimgFourThreeAspectRatio = 4f / 3f

enum class YTThumbQuality(val value: String) {
    MAXRES("maxresdefault"),
    HQ720("hq720"),
    HQ("hqdefault"),
    MQ("mqdefault"),
    DEFAULT("default"),
}

enum class YtimgResizePolicy {
    PreserveOriginal,
    MatchSourceAspect,
    AllowAnyAspect,
}

object ThumbnailUtils {

    private val wHPathRegex = Regex("w\\d+-h\\d+")
    private val wHParamRegex = Regex("=w(\\d+)-h(\\d+)")
    private val sParamRegex = Regex("=s(\\d+)")
    private val brokenSAppendRegex = Regex("-s\\d+")
    private val videoIdRegex = Regex("/vi/([a-zA-Z0-9_-]{11})")

    private val DEFAULT_SIZE_BUCKETS = listOf(48, 72, 96, 120, 200, 320, 480, 544, 720, 1080)

    fun buildYTThumbnailUrl(
        videoId: String,
        quality: YTThumbQuality = YTThumbQuality.MAXRES,
    ): String = "https://i.ytimg.com/vi/$videoId/${quality.value}.jpg"

    private fun getBucketSize(size: Int, buckets: List<Int>): Int {
        val sortedBuckets = buckets.sorted()
        for (bucket in sortedBuckets) {
            if (size <= bucket) return bucket
        }
        return sortedBuckets.lastOrNull() ?: 1080
    }

    private fun chooseFourThreeQuality(size: Int): YTThumbQuality =
        if (size <= 120) YTThumbQuality.DEFAULT else YTThumbQuality.HQ

    private fun chooseLandscapeQuality(size: Int, maxresAllowed: Boolean): YTThumbQuality =
        when {
            size <= 320 -> YTThumbQuality.MQ
            size <= 720 || !maxresAllowed -> YTThumbQuality.HQ720
            else -> YTThumbQuality.MAXRES
        }

    private fun chooseAnyAspectQuality(size: Int, maxresAllowed: Boolean): YTThumbQuality =
        when {
            size <= 120 -> YTThumbQuality.DEFAULT
            size <= 320 -> YTThumbQuality.MQ
            size <= 480 -> YTThumbQuality.HQ
            size <= 720 || !maxresAllowed -> YTThumbQuality.HQ720
            else -> YTThumbQuality.MAXRES
        }

    /**
     * Sizing image URL for display bounds.
     * Google CDN URLs (googleusercontent.com, ggpht.com) are updated to size parameters.
     */
    fun resize(
        url: String,
        width: Int? = null,
        height: Int? = null,
        maxresAllowed: Boolean = true,
        sizeBuckets: List<Int>? = null,
        ytimgResizePolicy: YtimgResizePolicy = YtimgResizePolicy.AllowAnyAspect,
        sourceAspectRatio: Float? = null,
    ): String {
        if (width == null && height == null) return url

        val isGoogleCdn = url.contains("googleusercontent.com") || url.contains("ggpht.com")
        val isYtimg = url.contains("i.ytimg.com")

        if (isGoogleCdn) {
            val rawW = width ?: height!!
            val rawH = height ?: width!!
            val buckets = sizeBuckets ?: DEFAULT_SIZE_BUCKETS
            val w = getBucketSize(rawW, buckets)
            val h = getBucketSize(rawH, buckets)

            wHParamRegex.find(url)?.let {
                return "${url.split("=w")[0]}=w$w-h$h-p-l90-rj"
            }

            if (wHPathRegex.containsMatchIn(url)) {
                return url.replace(wHPathRegex, "w$w-h$h")
            }

            sParamRegex.find(url)?.let { match ->
                val before = url.substring(0, match.range.first)
                val after = url.substring(match.range.last + 1)
                return "$before=s${maxOf(w, h)}${after.replace(brokenSAppendRegex, "")}"
            }

            return url
        }

        if (isYtimg) {
            val videoId = videoIdRegex.find(url)?.groupValues?.get(1) ?: return url
            val size = maxOf(width ?: 0, height ?: 0)
            val quality = when (ytimgResizePolicy) {
                YtimgResizePolicy.PreserveOriginal -> return url
                YtimgResizePolicy.MatchSourceAspect -> {
                    val sourceRatio = sourceAspectRatio
                        ?.takeIf { it.isFinite() && it > 0f }
                        ?: return url
                    if (sourceRatio <= YtimgFourThreeAspectRatio) {
                        chooseFourThreeQuality(size)
                    } else {
                        chooseLandscapeQuality(size, maxresAllowed)
                    }
                }
                YtimgResizePolicy.AllowAnyAspect -> {
                    chooseAnyAspectQuality(size, maxresAllowed)
                }
            }
            return buildYTThumbnailUrl(videoId, quality)
        }

        return url
    }

    /**
     * Resolves the highest quality artwork URL for player display.
     * Google CDN images scale to 1080x1080; YouTube images request maxresdefault.
     */
    fun getHighResArtworkUrl(url: String?, videoId: String?): String? {
        if (url.isNullOrBlank()) {
            return videoId?.let { buildYTThumbnailUrl(it, YTThumbQuality.MAXRES) }
        }

        if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            return resize(
                url = url,
                width = PlayerArtworkHighResPx,
                height = PlayerArtworkHighResPx,
                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal
            )
        }

        if (url.contains("i.ytimg.com")) {
            val vid = videoIdRegex.find(url)?.groupValues?.get(1) ?: videoId
            if (vid != null) {
                return buildYTThumbnailUrl(vid, YTThumbQuality.MAXRES)
            }
        }

        return url
    }
}
