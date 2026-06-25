package com.appplayer.music.data.repository

import com.appplayer.music.data.api.CantioApiService
import com.appplayer.music.data.api.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val api: CantioApiService
) {

    // ─── Search ──────────────────────────────────────────────────────────────

    suspend fun search(query: String, limit: Int = 20): ApiResult<List<SearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<List<SearchResult>>> {
                val response = api.search(query, limit)
                if (response.isSuccessful) ApiResult.Success(response.body()?.results ?: emptyList())
                else ApiResult.Error(response.errorBody()?.string() ?: "Search failed", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    // ─── Track ───────────────────────────────────────────────────────────────

    suspend fun getTrackMetadata(videoId: String): ApiResult<TrackMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getTrackMetadata(videoId)
                if (response.isSuccessful && response.body() != null) ApiResult.Success(response.body()!!)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch track", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun getRelatedTracks(videoId: String, limit: Int = 20): ApiResult<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<List<Track>>> {
                val response = api.getRelatedTracks(videoId, limit)
                if (response.isSuccessful) ApiResult.Success(response.body()?.tracks ?: emptyList())
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch related", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    // ─── Recommendations ─────────────────────────────────────────────────────

    suspend fun getRecommendations(): ApiResult<Recommendations> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getRecommendations()
                if (response.isSuccessful && response.body() != null) {
                    ApiResult.Success(response.body()!!.recommendations)
                } else {
                    ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch recommendations", response.code())
                }
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    // ─── Likes ───────────────────────────────────────────────────────────────

    suspend fun getLikedTracks(): ApiResult<List<LikedTrack>> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<List<LikedTrack>>> {
                val response = api.getLikedTracks()
                if (response.isSuccessful) ApiResult.Success(response.body()?.likedTracks ?: emptyList())
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch likes", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun likeTrack(
        videoId: String, title: String, artist: String,
        thumbnail: String?, duration: Int?
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.likeTrack(LikeRequest(videoId, title, artist, thumbnail, duration))
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(response.errorBody()?.string() ?: "Failed to like track", response.code())
        }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
    }

    suspend fun unlikeTrack(videoId: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.unlikeTrack(videoId)
                if (response.isSuccessful) ApiResult.Success(Unit)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to unlike track", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun isTrackLiked(videoId: String): ApiResult<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.isTrackLiked(videoId)
                if (response.isSuccessful) ApiResult.Success(response.body()?.isLiked ?: false)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to check like status", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    // ─── Playlists ────────────────────────────────────────────────────────────

    suspend fun getPlaylists(): ApiResult<List<Playlist>> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<List<Playlist>>> {
                val response = api.getPlaylists()
                if (response.isSuccessful) ApiResult.Success(response.body()?.playlists ?: emptyList())
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch playlists", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun getPlaylist(id: String): ApiResult<Playlist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getPlaylist(id)
                if (response.isSuccessful && response.body() != null) ApiResult.Success(response.body()!!.playlist)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch playlist", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun createPlaylist(name: String, description: String? = null, isPublic: Boolean = false): ApiResult<Playlist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.createPlaylist(CreatePlaylistRequest(name, description, isPublic))
                if (response.isSuccessful && response.body() != null) ApiResult.Success(response.body()!!.playlist)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to create playlist", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun deletePlaylist(id: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.deletePlaylist(id)
                if (response.isSuccessful) ApiResult.Success(Unit)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to delete playlist", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun addTrackToPlaylist(
        playlistId: String, videoId: String, title: String,
        artist: String, thumbnail: String?, duration: Int?
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.addTrackToPlaylist(playlistId, AddToPlaylistRequest(videoId, title, artist, thumbnail, duration))
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(response.errorBody()?.string() ?: "Failed to add track", response.code())
        }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.removeTrackFromPlaylist(playlistId, trackId)
                if (response.isSuccessful) ApiResult.Success(Unit)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to remove track", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    // ─── Blends ───────────────────────────────────────────────────────────────

    suspend fun getBlends(): ApiResult<List<Blend>> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<List<Blend>>> {
                val response = api.getBlends()
                if (response.isSuccessful) ApiResult.Success(response.body()?.blends ?: emptyList())
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch blends", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun getBlend(id: String): ApiResult<Blend> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getBlend(id)
                if (response.isSuccessful && response.body() != null) ApiResult.Success(response.body()!!.blend)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch blend", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun getBlendInvites(): ApiResult<List<BlendInvite>> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<List<BlendInvite>>> {
                val response = api.getBlendInvites()
                if (response.isSuccessful) ApiResult.Success(response.body()?.invites ?: emptyList())
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch invites", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun sendBlendInvite(email: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.sendBlendInvite(BlendInviteRequest(email))
                if (response.isSuccessful) ApiResult.Success(Unit)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to send invite", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun acceptBlendInvite(inviteId: String): ApiResult<Blend> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.acceptBlendInvite(inviteId)
                if (response.isSuccessful && response.body() != null) ApiResult.Success(response.body()!!.blend)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to accept invite", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    // ─── YTMusic ─────────────────────────────────────────────────────────────

    suspend fun getYTMusicPlaylist(id: String): ApiResult<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<List<Track>>> {
                val response = api.getYTMusicPlaylist(id)
                if (response.isSuccessful) ApiResult.Success(response.body()?.tracks ?: emptyList())
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to load YTM playlist", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun getYTMusicAlbum(id: String): ApiResult<YTMusicAlbumResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getYTMusicAlbum(id)
                if (response.isSuccessful && response.body() != null) ApiResult.Success(response.body()!!)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to load album", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }
}
