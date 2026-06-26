package com.appplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.appplayer.music.data.api.models.Track
import com.appplayer.music.playback.PlayerConnection
import com.appplayer.music.ui.theme.DarkSurface2
import com.appplayer.music.ui.theme.NeonViolet
import com.appplayer.music.viewmodel.SearchTab
import com.appplayer.music.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    playerConnection: PlayerConnection,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToYTMusicDetail: (type: String, id: String) -> Unit,
    onNavigateToPlaylist: (id: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Input Header
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            placeholder = { Text("Search songs, artists, playlists...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonViolet,
                cursorColor = NeonViolet
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        ScrollableTabRow(
            selectedTabIndex = uiState.activeTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = NeonViolet,
            edgePadding = 0.dp,
            divider = {}
        ) {
            SearchTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.activeTab == tab,
                    onClick = { viewModel.onTabSelected(tab) },
                    text = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selectedContentColor = NeonViolet,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }


        val playlists by viewModel.playlists.collectAsState()
        var trackWithOptions by remember { mutableStateOf<Track?>(null) }

        if (trackWithOptions != null) {
            LaunchedEffect(trackWithOptions) {
                viewModel.loadUserPlaylists()
            }
            com.appplayer.music.ui.components.AddToQueueOrPlaylistDialog(
                track = trackWithOptions!!,
                playlists = playlists,
                onAddToQueue = { playerConnection.addToQueue(trackWithOptions!!) },
                onPlayNext = { playerConnection.playNext(trackWithOptions!!) },
                onAddToPlaylist = { playlistId ->
                    viewModel.addTrackToPlaylist(playlistId, trackWithOptions!!)
                },
                onDismiss = { trackWithOptions = null }
            )
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(color = NeonViolet)
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "An error occurred",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (uiState.results.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Start searching on Cantio",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(uiState.results) { result ->
                        when (uiState.activeTab) {
                            SearchTab.SONGS -> {
                                val track = Track(
                                    videoId = result.videoId ?: "",
                                    title = result.title ?: "",
                                    artist = result.artist ?: "Unknown Artist",
                                    thumbnail = result.thumbnail,
                                    duration = result.duration
                                )
                                SearchListItem(
                                    title = result.title ?: "",
                                    subtitle = result.artist ?: "Unknown Artist",
                                    thumbnail = result.thumbnail,
                                    isArtist = false,
                                    onClick = {
                                        val searchTracks = uiState.results.mapNotNull { res ->
                                            if (!res.videoId.isNullOrEmpty()) {
                                                Track(
                                                    videoId = res.videoId,
                                                    title = res.title ?: "Unknown Track",
                                                    artist = res.artist ?: "Unknown Artist",
                                                    thumbnail = res.thumbnail,
                                                    duration = res.duration
                                                )
                                            } else {
                                                null
                                            }
                                        }
                                        val clickIndex = searchTracks.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0)
                                        playerConnection.playQueue(searchTracks, clickIndex)
                                        onNavigateToNowPlaying()
                                    },
                                    onAddToQueueOrPlaylist = {
                                        trackWithOptions = track
                                    }
                                )
                            }
                            SearchTab.PLAYLISTS -> {
                                SearchListItem(
                                    title = result.title ?: "Unknown Playlist",
                                    subtitle = result.author ?: "Unknown Author",
                                    thumbnail = result.thumbnail,
                                    isArtist = false,
                                    onClick = {
                                        result.playlistId?.let { id ->
                                            if (result.type == "cantio_playlist") {
                                                onNavigateToPlaylist(id)
                                            } else {
                                                onNavigateToYTMusicDetail("playlist", id)
                                            }
                                        }
                                    }
                                )
                            }
                            SearchTab.ALBUMS -> {
                                SearchListItem(
                                    title = result.title ?: "Unknown Album",
                                    subtitle = result.artist ?: "Unknown Artist",
                                    thumbnail = result.thumbnail,
                                    isArtist = false,
                                    onClick = {
                                        result.browseId?.let { id ->
                                            onNavigateToYTMusicDetail("album", id)
                                        }
                                    }
                                )
                            }
                            SearchTab.ARTISTS -> {
                                SearchListItem(
                                    title = result.name ?: result.title ?: "Unknown Artist",
                                    subtitle = result.subscribers ?: "Artist",
                                    thumbnail = result.thumbnail,
                                    isArtist = true,
                                    onClick = {
                                        result.browseId?.let { id ->
                                            onNavigateToYTMusicDetail("artist", id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchListItem(
    title: String,
    subtitle: String,
    thumbnail: String?,
    isArtist: Boolean,
    onClick: () -> Unit,
    onAddToQueueOrPlaylist: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(if (isArtist) RoundedCornerShape(28.dp) else RoundedCornerShape(8.dp))
                .background(DarkSurface2),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (onAddToQueueOrPlaylist != null) {
                IconButton(onClick = onAddToQueueOrPlaylist) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add to Queue or Playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Open",
                tint = NeonViolet
            )
        }
    }
}
