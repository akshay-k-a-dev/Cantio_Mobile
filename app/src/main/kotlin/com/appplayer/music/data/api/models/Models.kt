package com.appplayer.music.data.api.models

import com.google.gson.annotations.SerializedName

// ─── Auth ────────────────────────────────────────────────────────────────────

data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String? = null,
    val name: String? = null,
    val otp: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class SendOtpRequest(
    val email: String,
    val purpose: String = "register" // "register" | "reset"
)

data class AuthResponse(
    val user: UserProfile,
    val token: String
)

data class UserProfile(
    val id: String,
    val email: String,
    val username: String,
    val name: String?,
    val avatar: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("_count") val count: UserCount? = null
)

data class UserCount(
    @SerializedName("likedTracks") val likedTracks: Int = 0,
    val playlists: Int = 0,
    @SerializedName("playHistory") val playHistory: Int = 0
)

// ─── Track ───────────────────────────────────────────────────────────────────

data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val duration: Int? = null // seconds
)

data class TrackMetadata(
    val videoId: String,
    val title: String,
    val author: Author?,
    val thumbnail: Thumbnail?,
    val duration: Duration?
)

data class Author(val name: String)
data class Thumbnail(val url: String)
data class Duration(val seconds: Int)

// ─── Likes ───────────────────────────────────────────────────────────────────

data class LikedTrack(
    val id: String,
    val userId: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val duration: Int?,
    @SerializedName("likedAt") val likedAt: String
)

data class LikeRequest(
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnail: String? = null,
    val duration: Int? = null
)

data class LikedTracksResponse(val likedTracks: List<LikedTrack>)
data class IsLikedResponse(val isLiked: Boolean)

// ─── Playlists ────────────────────────────────────────────────────────────────

data class Playlist(
    val id: String,
    val userId: String,
    val name: String,
    val description: String?,
    val thumbnail: String?,
    val isPublic: Boolean,
    val shareSlug: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("_count") val count: PlaylistCount? = null,
    val tracks: List<PlaylistTrack>? = null,
    val user: UserProfile? = null
)

data class PlaylistCount(val tracks: Int = 0)

data class PlaylistTrack(
    val id: String,
    val playlistId: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val duration: Int?,
    val position: Int
)

data class CreatePlaylistRequest(
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = false
)

data class AddToPlaylistRequest(
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnail: String? = null,
    val duration: Int? = null
)

data class PlaylistsResponse(val playlists: List<Playlist>)
data class PlaylistResponse(val playlist: Playlist)

// ─── Blends ───────────────────────────────────────────────────────────────────

data class Blend(
    val id: String,
    val name: String,
    val user1Id: String,
    val user2Id: String,
    val user1: UserProfile?,
    val user2: UserProfile?,
    @SerializedName("_count") val count: BlendCount? = null,
    val tracks: List<BlendTrack>? = null,
    @SerializedName("createdAt") val createdAt: String
)

data class BlendCount(val tracks: Int = 0)

data class BlendTrack(
    val id: String,
    val blendId: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val duration: Int?,
    val sourceUserId: String,
    val position: Int
)

data class BlendInvite(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val status: String,
    val sender: UserProfile?,
    val receiver: UserProfile?,
    @SerializedName("createdAt") val createdAt: String
)

data class BlendsResponse(val blends: List<Blend>)
data class BlendResponse(val blend: Blend)
data class BlendInvitesResponse(val invites: List<BlendInvite>)
data class BlendInviteRequest(val email: String)

// ─── Recommendations ─────────────────────────────────────────────────────────

data class TopArtist(
    val name: String,
    val playCount: Int,
    val tracks: List<Track>
)

data class Recommendations(
    val recentlyPlayed: List<Track>,
    val mostPlayed: List<Track>,
    val topArtists: List<TopArtist>,
    val forYou: List<Track>
)

data class RecommendationsResponse(val recommendations: Recommendations)

// ─── Search ───────────────────────────────────────────────────────────────────

data class SearchResult(
    val videoId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null,

    val type: String? = null,
    val playlistId: String? = null,
    val browseId: String? = null,
    val author: String? = null,
    val name: String? = null,
    val trackCount: Int? = null,
    val year: String? = null,
    val subscribers: String? = null
)

data class SearchResponse(val results: List<SearchResult>)

data class RelatedTracksResponse(val tracks: List<Track>)

// ─── YTMusic ──────────────────────────────────────────────────────────────────

data class YTMusicPlaylistResponse(val tracks: List<Track>)
data class YTMusicAlbumResponse(
    val tracks: List<Track>? = null,
    val title: String? = null,
    val artist: String? = null,
    val thumbnail: String? = null
)
data class YTMusicArtistResponse(
    val tracks: List<Track>? = null,
    val name: String? = null,
    val thumbnail: String? = null,
    val subscribers: String? = null
)

// ─── Generic ─────────────────────────────────────────────────────────────────

data class SuccessResponse(val success: Boolean, val message: String? = null)
data class ErrorResponse(val error: String, val message: String? = null)
data class MeResponse(val user: UserProfile)

data class RecordPlayRequest(
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val duration: Int? = null
)

data class LyricsResponseItem(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

data class PopularTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val duration: Int? = null
)

data class PopularTracksResponse(val tracks: List<PopularTrack>)

data class ResetPasswordRequest(
    val email: String,
    val otp: String,
    val newPassword: String
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class UserPreferences(
    val favoriteLanguages: List<String> = emptyList(),
    val favoriteArtists: List<String> = emptyList(),
    val favoriteGenres: List<String> = emptyList(),
    val onboardingDone: Boolean = false
)

data class PreferencesResponse(
    val preferences: UserPreferences
)

data class NeedsOnboardingResponse(
    val needsOnboarding: Boolean
)

data class SeedResponse(
    val seeded: Boolean,
    val count: Int? = null,
    val reason: String? = null
)
