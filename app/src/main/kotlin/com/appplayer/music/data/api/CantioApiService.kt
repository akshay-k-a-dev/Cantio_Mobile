package com.appplayer.music.data.api

import com.appplayer.music.data.api.models.*
import retrofit2.Response
import retrofit2.http.*

interface CantioApiService {

    // ─── Auth ────────────────────────────────────────────────────────────────

    @POST("api/auth/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<SuccessResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/auth/me")
    suspend fun getMe(): Response<MeResponse>

    // ─── Search ──────────────────────────────────────────────────────────────

    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): Response<SearchResponse>

    @GET("api/search/music")
    suspend fun searchMusic(
        @Query("q") query: String,
        @Query("type") type: String = "playlists",
        @Query("limit") limit: Int = 20
    ): Response<SearchResponse>

    // ─── Track Metadata ───────────────────────────────────────────────────────

    @GET("api/track/{id}")
    suspend fun getTrackMetadata(@Path("id") videoId: String): Response<TrackMetadata>

    @GET("api/track/{id}/related")
    suspend fun getRelatedTracks(
        @Path("id") videoId: String,
        @Query("limit") limit: Int = 20
    ): Response<RelatedTracksResponse>

    // ─── Likes ───────────────────────────────────────────────────────────────

    @GET("api/likes")
    suspend fun getLikedTracks(): Response<LikedTracksResponse>

    @POST("api/likes")
    suspend fun likeTrack(@Body request: LikeRequest): Response<Any>

    @DELETE("api/likes/{trackId}")
    suspend fun unlikeTrack(@Path("trackId") trackId: String): Response<SuccessResponse>

    @GET("api/likes/{trackId}")
    suspend fun isTrackLiked(@Path("trackId") trackId: String): Response<IsLikedResponse>

    // ─── Playlists ────────────────────────────────────────────────────────────

    @GET("api/playlists")
    suspend fun getPlaylists(): Response<PlaylistsResponse>

    @POST("api/playlists")
    suspend fun createPlaylist(@Body request: CreatePlaylistRequest): Response<PlaylistResponse>

    @GET("api/playlists/{id}")
    suspend fun getPlaylist(@Path("id") id: String): Response<PlaylistResponse>

    @PATCH("api/playlists/{id}")
    suspend fun updatePlaylist(
        @Path("id") id: String,
        @Body request: Map<String, Any>
    ): Response<PlaylistResponse>

    @DELETE("api/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: String): Response<SuccessResponse>

    @POST("api/playlists/{id}/tracks")
    suspend fun addTrackToPlaylist(
        @Path("id") playlistId: String,
        @Body request: AddToPlaylistRequest
    ): Response<Any>

    @DELETE("api/playlists/{id}/tracks/{trackId}")
    suspend fun removeTrackFromPlaylist(
        @Path("id") playlistId: String,
        @Path("trackId") trackId: String
    ): Response<SuccessResponse>

    @POST("api/playlists/{id}/share")
    suspend fun sharePlaylist(@Path("id") id: String): Response<Map<String, String>>

    @GET("api/playlists/public/{identifier}")
    suspend fun getPublicPlaylist(@Path("identifier") identifier: String): Response<PlaylistResponse>

    // ─── Recommendations ─────────────────────────────────────────────────────

    @GET("api/recommendations")
    suspend fun getRecommendations(): Response<RecommendationsResponse>

    // ─── Blends ───────────────────────────────────────────────────────────────

    @GET("api/blends")
    suspend fun getBlends(): Response<BlendsResponse>

    @GET("api/blends/{id}")
    suspend fun getBlend(@Path("id") id: String): Response<BlendResponse>

    @POST("api/blends/invite")
    suspend fun sendBlendInvite(@Body request: BlendInviteRequest): Response<Any>

    @GET("api/blends/invites")
    suspend fun getBlendInvites(): Response<BlendInvitesResponse>

    @POST("api/blends/invites/{id}/accept")
    suspend fun acceptBlendInvite(@Path("id") id: String): Response<BlendResponse>

    @POST("api/blends/invites/{id}/reject")
    suspend fun rejectBlendInvite(@Path("id") id: String): Response<SuccessResponse>

    @POST("api/blends/{id}/regenerate")
    suspend fun regenerateBlend(@Path("id") id: String): Response<BlendResponse>

    @DELETE("api/blends/{id}/leave")
    suspend fun leaveBlend(@Path("id") id: String): Response<SuccessResponse>

    // ─── YTMusic ─────────────────────────────────────────────────────────────

    @GET("api/ytmusic/playlist/{id}")
    suspend fun getYTMusicPlaylist(@Path("id") id: String): Response<YTMusicPlaylistResponse>

    @GET("api/ytmusic/album/{id}")
    suspend fun getYTMusicAlbum(@Path("id") id: String): Response<YTMusicAlbumResponse>

    @GET("api/ytmusic/artist/{id}")
    suspend fun getYTMusicArtist(@Path("id") id: String): Response<YTMusicArtistResponse>

    // ─── Lyrics ───────────────────────────────────────────────────────────────

    @GET("api/lyrics")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String
    ): Response<List<Any>> // LRCLIB returns array of results
}
