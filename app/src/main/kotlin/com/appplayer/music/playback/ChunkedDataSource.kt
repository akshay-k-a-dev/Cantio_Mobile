package com.appplayer.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.EOFException

/**
 * A DataSource wrapper that fetches audio in fixed-size chunks (default 8 MB).
 *
 * googlevideo.com throttles large single-range requests. Fetching in chunks
 * avoids this and also avoids parser issues with mp4/webm containers that
 * need open-ended reads.
 *
 * Adapted from ArchiveTune (GPL-3.0).
 */
internal class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkLength: Long,
) : DataSource {
    private var request: DataSpec? = null
    private var bytesRead = 0L
    private var chunkBytesRemaining = C.LENGTH_UNSET.toLong()
    private var upstreamOpen = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(request == null)
        request = dataSpec
        bytesRead = 0L
        val resolvedLength = openNextChunk(dataSpec)
        return if (dataSpec.length == C.LENGTH_UNSET.toLong()) resolvedLength else dataSpec.length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val dataSpec = checkNotNull(request)
        if (dataSpec.length != C.LENGTH_UNSET.toLong() && bytesRead == dataSpec.length) {
            return C.RESULT_END_OF_INPUT
        }
        if (chunkBytesRemaining == 0L) {
            closeUpstream()
            openNextChunk(dataSpec.subrange(bytesRead))
        }
        val readLength =
            if (chunkBytesRemaining == C.LENGTH_UNSET.toLong()) {
                length
            } else {
                minOf(length.toLong(), chunkBytesRemaining).toInt()
            }
        val count = upstream.read(buffer, offset, readLength)
        if (count == C.RESULT_END_OF_INPUT) {
            if (chunkBytesRemaining > 0L) {
                throw EOFException("Audio stream ended before the requested range was complete")
            }
            return count
        }
        bytesRead += count
        if (chunkBytesRemaining != C.LENGTH_UNSET.toLong()) {
            chunkBytesRemaining -= count
        }
        return count
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        request = null
        bytesRead = 0L
        chunkBytesRemaining = C.LENGTH_UNSET.toLong()
        closeUpstream()
    }

    private fun openNextChunk(dataSpec: DataSpec): Long {
        val length =
            if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                null
            } else {
                resolveStreamChunkLength(
                    requestedLength = dataSpec.length,
                    position = dataSpec.position,
                    knownContentLength = null,
                    chunkLength = chunkLength,
                    mimeType = null,
                )
            }
        val chunkSpec = length?.let { dataSpec.subrange(0L, it) } ?: dataSpec
        chunkBytesRemaining = chunkSpec.length
        upstreamOpen = true
        return upstream.open(chunkSpec)
    }

    private fun closeUpstream() {
        if (!upstreamOpen) return
        upstreamOpen = false
        upstream.close()
    }

    companion object {
        /** 8 MB chunks — mirrors ArchiveTune's CHUNK_LENGTH. */
        const val CHUNK_LENGTH = 8L * 1024L * 1024L
    }
}

/**
 * Resolves the length of the next Range chunk.
 *
 * For mp4/webm containers that need open-ended reads, returns null
 * (meaning: fetch until end of stream).
 */
internal fun resolveStreamChunkLength(
    requestedLength: Long,
    position: Long,
    knownContentLength: Long?,
    chunkLength: Long,
    mimeType: String? = null,
): Long? {
    if (chunkLength <= 0L || position < 0L) return null
    if (knownContentLength != null && position >= knownContentLength) return null
    if (requestedLength <= 0L && mimeType.requiresOpenEndedRead()) return null

    val remainingLength = knownContentLength?.minus(position)?.takeIf { it > 0L }
    val resolvedLength =
        listOfNotNull(
            chunkLength,
            requestedLength.takeIf { it > 0L },
            remainingLength,
        ).minOrNull()

    return resolvedLength?.takeIf { it > 0L }
}

private fun String?.requiresOpenEndedRead(): Boolean {
    val normalizedMimeType =
        this
            ?.substringBefore(";")
            ?.trim()
            ?.lowercase()
            .orEmpty()
    return normalizedMimeType == "audio/mp4" ||
        normalizedMimeType == "video/mp4" ||
        normalizedMimeType == "application/mp4" ||
        normalizedMimeType == "audio/x-m4a" ||
        normalizedMimeType == "audio/webm" ||
        normalizedMimeType == "video/webm" ||
        normalizedMimeType == "application/webm" ||
        normalizedMimeType == "audio/ogg" ||
        normalizedMimeType == "application/ogg"
}
