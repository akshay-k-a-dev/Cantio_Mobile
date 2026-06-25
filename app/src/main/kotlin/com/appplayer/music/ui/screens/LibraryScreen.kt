package com.appplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.appplayer.music.data.api.models.Track
import com.appplayer.music.playback.PlayerConnection
import com.appplayer.music.ui.theme.DarkSurface2
import com.appplayer.music.ui.theme.GoldAccent
import com.appplayer.music.ui.theme.NeonViolet
import com.appplayer.music.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    playerConnection: PlayerConnection,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToBlend: (String) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Liked Songs", "Playlists", "Blends")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = NeonViolet
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonViolet)
                }
            } else {
                when (selectedTab) {
                    0 -> { // Liked Songs
                        if (uiState.likedTracks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No liked songs yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(uiState.likedTracks) { track ->
                                    val domainTrack = Track(
                                        videoId = track.trackId,
                                        title = track.title,
                                        artist = track.artist,
                                        thumbnail = track.thumbnail,
                                        duration = track.duration
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val queue = uiState.likedTracks.map {
                                                    Track(it.trackId, it.title, it.artist, it.thumbnail, it.duration)
                                                }
                                                playerConnection.playQueue(queue, queue.indexOf(domainTrack))
                                                onNavigateToNowPlaying()
                                            }
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
                                        IconButton(onClick = { viewModel.unlikeTrack(track.trackId) }) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "Unlike",
                                                tint = GoldAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // Playlists
                        Column(modifier = Modifier.fillMaxSize()) {
                            Button(
                                onClick = { showCreatePlaylistDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create Playlist")
                            }

                            if (uiState.playlists.isEmpty()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("No playlists yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(uiState.playlists) { playlist ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onNavigateToPlaylist(playlist.id) }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.List,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(DarkSurface2)
                                                    .padding(12.dp),
                                                tint = NeonViolet
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playlist.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = playlist.description ?: "No description",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> { // Blends
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Invites section if any
                            if (uiState.blendInvites.isNotEmpty()) {
                                Text(
                                    text = "Pending Invites",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(16.dp),
                                    color = NeonViolet
                                )
                                uiState.blendInvites.forEach { invite ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${invite.sender?.name ?: invite.sender?.username} invited you",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Button(
                                            onClick = { viewModel.acceptBlendInvite(invite.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                                        ) {
                                            Text("Accept")
                                        }
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            }

                            if (uiState.blends.isEmpty()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("No active blends.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(uiState.blends) { blend ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onNavigateToBlend(blend.id) }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.People,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(DarkSurface2)
                                                    .padding(12.dp),
                                                tint = NeonViolet
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = blend.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Blend between you and ${blend.user2?.name ?: blend.user2?.username ?: "friend"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonViolet,
                        cursorColor = NeonViolet
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            viewModel.createPlaylist(playlistName)
                            playlistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = NeonViolet)
                }
            }
        )
    }
}
