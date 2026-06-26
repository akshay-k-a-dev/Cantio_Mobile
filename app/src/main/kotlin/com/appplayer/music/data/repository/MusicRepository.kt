package com.appplayer.music.data.repository

import com.appplayer.music.data.api.CantioApiService
import com.appplayer.music.data.api.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val api: CantioApiService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val discoveredPrefs = context.getSharedPreferences("discovered_tracks_prefs", android.content.Context.MODE_PRIVATE)

    private val _discoveredTracks = MutableStateFlow<List<Track>>(emptyList())
    val discoveredTracks: StateFlow<List<Track>> = _discoveredTracks.asStateFlow()

    init {
        _discoveredTracks.value = loadDiscoveredTracksLocal()
    }

    private fun loadDiscoveredTracksLocal(): List<Track> {
        val json = discoveredPrefs.getString("discovered_json", null) ?: return emptyList()
        return try {
            val jsonArray = org.json.JSONArray(json)
            val list = mutableListOf<Track>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Track(
                        videoId = obj.getString("videoId"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        thumbnail = obj.optString("thumbnail", "").takeIf { it.isNotEmpty() },
                        duration = if (obj.has("duration")) obj.getInt("duration") else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveDiscoveredTracksLocal(list: List<Track>) {
        val jsonArray = org.json.JSONArray()
        list.forEach { track ->
            val obj = org.json.JSONObject().apply {
                put("videoId", track.videoId)
                put("title", track.title)
                put("artist", track.artist)
                put("thumbnail", track.thumbnail)
                track.duration?.let { put("duration", it) }
            }
            jsonArray.put(obj)
        }
        discoveredPrefs.edit().putString("discovered_json", jsonArray.toString()).apply()
    }

    fun addDiscoveredTracks(tracks: List<Track>) {
        val current = _discoveredTracks.value.toMutableList()
        val existingIds = current.map { it.videoId }.toSet()
        val newTracks = tracks.filter { it.videoId !in existingIds }
        if (newTracks.isNotEmpty()) {
            val merged = (newTracks + current).take(50)
            _discoveredTracks.value = merged
            saveDiscoveredTracksLocal(merged)
        }
    }

    fun removeDiscoveredTrack(videoId: String) {
        val updated = _discoveredTracks.value.filter { it.videoId != videoId }
        _discoveredTracks.value = updated
        saveDiscoveredTracksLocal(updated)
    }

    private val _likedTracks = MutableStateFlow<List<LikedTrack>>(emptyList())
    val likedTracks: StateFlow<List<LikedTrack>> = _likedTracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

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

    suspend fun getPopularTracks(): ApiResult<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getPopularTracks()
                if (response.isSuccessful && response.body() != null) {
                    val tracks = response.body()!!.tracks.map {
                        Track(
                            videoId = it.trackId,
                            title = it.title,
                            artist = it.artist,
                            thumbnail = it.thumbnail,
                            duration = it.duration
                        )
                    }
                    ApiResult.Success(tracks)
                } else {
                    ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch popular tracks", response.code())
                }
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    // ─── Likes ───────────────────────────────────────────────────────────────

    suspend fun getLikedTracks(): ApiResult<List<LikedTrack>> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<List<LikedTrack>>> {
                val response = api.getLikedTracks()
                if (response.isSuccessful) {
                    val list = response.body()?.likedTracks ?: emptyList()
                    _likedTracks.value = list
                    ApiResult.Success(list)
                } else {
                    ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch likes", response.code())
                }
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun likeTrack(
        videoId: String, title: String, artist: String,
        thumbnail: String?, duration: Int?
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.likeTrack(LikeRequest(videoId, title, artist, thumbnail, duration))
            if (response.isSuccessful) {
                // Refresh from server to get the authoritative state
                getLikedTracks()
                ApiResult.Success(Unit)
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                ApiResult.Error(errBody.ifEmpty { "Failed to like track" }, response.code())
            }
        }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
    }

    suspend fun unlikeTrack(videoId: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.unlikeTrack(videoId)
                if (response.isSuccessful) {
                    // Optimistically remove locally, then confirm with server
                    _likedTracks.update { list -> list.filter { it.trackId != videoId } }
                    getLikedTracks() // Re-sync from backend
                    ApiResult.Success(Unit)
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    ApiResult.Error(errBody.ifEmpty { "Failed to unlike track" }, response.code())
                }
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun recordPlay(
        trackId: String, title: String, artist: String,
        thumbnail: String?, duration: Int?
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.recordPlay(RecordPlayRequest(trackId, title, artist, thumbnail, duration))
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error(response.errorBody()?.string() ?: "Failed to record play", response.code())
            }
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
                if (response.isSuccessful) {
                    val list = response.body()?.playlists ?: emptyList()
                    _playlists.value = list
                    ApiResult.Success(list)
                } else {
                    ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch playlists", response.code())
                }
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

    suspend fun getPublicPlaylist(identifier: String): ApiResult<Playlist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getPublicPlaylist(identifier)
                if (response.isSuccessful && response.body() != null) ApiResult.Success(response.body()!!.playlist)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch public playlist", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun createPlaylist(name: String, description: String? = null, isPublic: Boolean = false): ApiResult<Playlist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.createPlaylist(CreatePlaylistRequest(name, description, isPublic))
                if (response.isSuccessful && response.body() != null) {
                    val newPlaylist = response.body()!!.playlist
                    getPlaylists()
                    ApiResult.Success(newPlaylist)
                } else {
                    ApiResult.Error(response.errorBody()?.string() ?: "Failed to create playlist", response.code())
                }
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun deletePlaylist(id: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.deletePlaylist(id)
                if (response.isSuccessful) {
                    getPlaylists()
                    ApiResult.Success(Unit)
                } else {
                    ApiResult.Error(response.errorBody()?.string() ?: "Failed to delete playlist", response.code())
                }
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun updatePlaylist(id: String, name: String? = null, description: String? = null, isPublic: Boolean? = null): ApiResult<Playlist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = mutableMapOf<String, Any>()
                name?.let { body["name"] = it }
                description?.let { body["description"] = it }
                isPublic?.let { body["isPublic"] = it }
                val response = api.updatePlaylist(id, body)
                if (response.isSuccessful && response.body() != null) {
                    val updatedPlaylist = response.body()!!.playlist
                    getPlaylists()
                    ApiResult.Success(updatedPlaylist)
                } else {
                    ApiResult.Error(response.errorBody()?.string() ?: "Failed to update playlist", response.code())
                }
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun addTrackToPlaylist(
        playlistId: String, videoId: String, title: String,
        artist: String, thumbnail: String?, duration: Int?
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.addTrackToPlaylist(
                playlistId,
                AddToPlaylistRequest(videoId, title, artist, thumbnail, duration)
            )
            if (response.isSuccessful) {
                getPlaylists() // Re-sync playlists from backend
                ApiResult.Success(Unit)
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                ApiResult.Error(errBody.ifEmpty { "Failed to add track to playlist" }, response.code())
            }
        }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.removeTrackFromPlaylist(playlistId, trackId)
                if (response.isSuccessful) {
                    getPlaylists()
                    ApiResult.Success(Unit)
                } else {
                    ApiResult.Error(response.errorBody()?.string() ?: "Failed to remove track", response.code())
                }
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

    suspend fun rejectBlendInvite(inviteId: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.rejectBlendInvite(inviteId)
                if (response.isSuccessful) ApiResult.Success(Unit)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to reject invite", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun regenerateBlend(id: String): ApiResult<Blend> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.regenerateBlend(id)
                if (response.isSuccessful && response.body() != null) ApiResult.Success(response.body()!!.blend)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to regenerate blend", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun leaveBlend(id: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.leaveBlend(id)
                if (response.isSuccessful) ApiResult.Success(Unit)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to leave blend", response.code())
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

    suspend fun getYTMusicArtist(id: String): ApiResult<YTMusicArtistResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getYTMusicArtist(id)
                if (response.isSuccessful && response.body() != null) ApiResult.Success(response.body()!!)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to load artist", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun searchMusic(query: String, type: String, limit: Int = 20): ApiResult<List<SearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<List<SearchResult>>> {
                val response = api.searchMusic(query, type, limit)
                if (response.isSuccessful) ApiResult.Success(response.body()?.results ?: emptyList())
                else ApiResult.Error(response.errorBody()?.string() ?: "Search failed", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun getPublicPlaylists(query: String, limit: Int = 20): ApiResult<List<Playlist>> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<List<Playlist>>> {
                val response = api.getPublicPlaylists(query, limit)
                if (response.isSuccessful) ApiResult.Success(response.body()?.playlists ?: emptyList())
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch public playlists", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun getLyrics(trackName: String, artistName: String, durationSecs: Int): ApiResult<LyricsResponseItem?> =
        withContext(Dispatchers.IO) {
            runCatching<ApiResult<LyricsResponseItem?>> {
                val combos = extractTitleCombinations(trackName)
                for (combo in combos) {
                    val artist = if (combo.artist.isNotEmpty()) combo.artist else artistName
                    val response = api.getLyrics(combo.track, artist)
                    if (response.isSuccessful && response.body() != null) {
                        val results = response.body()!!
                        if (results.isNotEmpty()) {
                            val bestMatch = results.find { r ->
                                r.duration != null && Math.abs(r.duration - durationSecs) <= 20
                            } ?: results.first()
                            
                            if (bestMatch.syncedLyrics != null || bestMatch.plainLyrics != null) {
                                return@withContext ApiResult.Success(bestMatch)
                            }
                        }
                    }
                }
                
                val response = api.getLyrics(trackName, artistName)
                if (response.isSuccessful && response.body() != null) {
                    val results = response.body()!!
                    if (results.isNotEmpty()) {
                        val bestMatch = results.find { r ->
                            r.duration != null && Math.abs(r.duration - durationSecs) <= 20
                        } ?: results.first()
                        if (bestMatch.syncedLyrics != null || bestMatch.plainLyrics != null) {
                            return@withContext ApiResult.Success(bestMatch)
                        }
                    }
                }
                
                ApiResult.Success(null)
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    private fun extractTitleCombinations(title: String): List<TitleCombo> {
        val combinations = mutableListOf<TitleCombo>()
        val cleanTitle = title
            .replace(Regex("\\(Official.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Audio.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Lyric.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Music.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(HD.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(HQ.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(4K.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Video.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[Official.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[Audio.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[Lyric.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[HD.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("Official Video", RegexOption.IGNORE_CASE), "")
            .replace(Regex("Official Audio", RegexOption.IGNORE_CASE), "")
            .replace(Regex("Lyrics", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+HD\\s*", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+HQ\\s*", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+4K\\s*", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s*ft\\.?\\s+[^-|]+$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*feat\\.?\\s+[^-|]+$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleanTitle.contains(" - ")) {
            val parts = cleanTitle.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val cleanTrack = { s: String ->
                    s.replace(Regex("\\s*ft\\.?\\s+.*$", RegexOption.IGNORE_CASE), "")
                     .replace(Regex("\\s*feat\\.?\\s+.*$", RegexOption.IGNORE_CASE), "")
                     .trim()
                }
                combinations.add(TitleCombo(cleanTrack(parts[1]), parts[0]))
                combinations.add(TitleCombo(cleanTrack(parts[0]), parts[1]))
            }
        }

        if (cleanTitle.contains(" | ")) {
            val parts = cleanTitle.split(" | ").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                combinations.add(TitleCombo(parts[0], parts[1]))
                combinations.add(TitleCombo(parts[1], parts[0]))
            }
        }

        combinations.add(TitleCombo(cleanTitle, ""))
        return combinations
    }

    data class TitleCombo(val track: String, val artist: String)
}
