package com.appplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.appplayer.music.data.api.models.Track
import com.appplayer.music.playback.PlayerConnection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.appplayer.music.ui.theme.DarkSurface2
import com.appplayer.music.ui.theme.NeonViolet
import com.appplayer.music.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    playerConnection: PlayerConnection,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(NeonViolet.copy(alpha = 0.3f), MaterialTheme.colorScheme.background)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Cantio 🎵",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "What's on today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (uiState.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonViolet)
                }
            }
        } else if (uiState.error != null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        } else {
            val recs = uiState.recommendations

            // 1. For You
            if (recs?.forYou?.isNotEmpty() == true) {
                item {
                    SectionHeader("For You")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recs.forYou.take(10)) { track ->
                            TrackCard(track = track, onClick = {
                                playerConnection.playWithRecommendations(track, viewModel.musicRepository)
                                onNavigateToNowPlaying()
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // 2. Discover Something New
            if (uiState.discoveredTracks.isNotEmpty()) {
                item {
                    SectionHeader("Discover Something New")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.discoveredTracks.take(10)) { track ->
                            TrackCard(track = track, onClick = {
                                playerConnection.playWithRecommendations(track, viewModel.musicRepository)
                                onNavigateToNowPlaying()
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // 3. What Others Love
            if (uiState.popularTracks.isNotEmpty()) {
                item {
                    SectionHeader("What Others Love")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.popularTracks.take(10)) { track ->
                            TrackCard(track = track, onClick = {
                                playerConnection.playWithRecommendations(track, viewModel.musicRepository)
                                onNavigateToNowPlaying()
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // 4. Continue Listening (Recently Played)
            if (recs?.recentlyPlayed?.isNotEmpty() == true) {
                item { SectionHeader("Continue Listening") }
                items(recs.recentlyPlayed.take(5)) { track ->
                    TrackListItem(track = track, onClick = {
                        playerConnection.playWithRecommendations(track, viewModel.musicRepository)
                        onNavigateToNowPlaying()
                    })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // 5. Your Favorites (Most Played)
            if (recs?.mostPlayed?.isNotEmpty() == true) {
                item {
                    SectionHeader("Your Favorites")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recs.mostPlayed.take(8)) { track ->
                            TrackCard(track = track, onClick = {
                                playerConnection.playWithRecommendations(track, viewModel.musicRepository)
                                onNavigateToNowPlaying()
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // 6. Top Artists
            if (recs?.topArtists?.isNotEmpty() == true) {
                item { SectionHeader("Top Artists") }
                items(recs.topArtists) { artist ->
                    if (artist.tracks.isNotEmpty()) {
                        ArtistSection(
                            name = artist.name,
                            tracks = artist.tracks,
                            onTrackClick = { track ->
                                playerConnection.playQueue(artist.tracks, artist.tracks.indexOf(track))
                                onNavigateToNowPlaying()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
fun TrackCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.Start
    ) {
        AsyncImage(
            model = track.thumbnail,
            contentDescription = track.title,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface2),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TrackListItem(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = track.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface2),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ArtistSection(
    name: String,
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = NeonViolet,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        tracks.take(3).forEach { track ->
            TrackListItem(track = track, onClick = { onTrackClick(track) })
        }
    }
}
