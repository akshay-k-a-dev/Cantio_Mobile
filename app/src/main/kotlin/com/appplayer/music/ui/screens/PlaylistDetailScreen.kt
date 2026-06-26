package com.appplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.appplayer.music.data.api.models.Track
import com.appplayer.music.playback.PlayerConnection
import com.appplayer.music.ui.theme.DarkSurface2
import com.appplayer.music.ui.theme.GoldAccent
import com.appplayer.music.ui.theme.NeonViolet
import com.appplayer.music.viewmodel.PlaylistDetailViewModel
import android.content.Intent
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playerConnection: PlayerConnection,
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadPlaylist()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top app bar
        TopAppBar(
            title = { Text(uiState.playlist?.name ?: "Playlist") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Box(modifier = Modifier.weight(1f)) {
            if (uiState.isLoading && uiState.playlist == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonViolet)
                }
            } else if (uiState.error != null && uiState.playlist == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.error ?: "Failed to load playlist", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadPlaylist() }, colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                val playlist = uiState.playlist
                if (playlist != null) {
                    val tracks = playlist.tracks ?: emptyList()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // Header
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(NeonViolet.copy(alpha = 0.2f), MaterialTheme.colorScheme.background)
                                        )
                                    )
                                    .padding(20.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(NeonViolet),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = playlist.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.headlineLarge,
                                            color = Color.White
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.headlineMedium
                                        )
                                        playlist.description?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${tracks.size} tracks",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Play & Settings Actions
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (tracks.isNotEmpty()) {
                                    FloatingActionButton(
                                        onClick = {
                                            val queue = tracks.map {
                                                Track(it.trackId, it.title, it.artist, it.thumbnail, it.duration)
                                            }
                                            playerConnection.playQueue(queue, 0)
                                            onNavigateToNowPlaying()
                                        },
                                        containerColor = NeonViolet,
                                        contentColor = Color.White,
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            Text("Play All")
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.togglePublic(!playlist.isPublic) },
                                    enabled = !uiState.isUpdatingPublic
                                ) {
                                    Text(if (playlist.isPublic) "Public" else "Private", color = NeonViolet)
                                }

                                if (playlist.isPublic) {
                                    IconButton(
                                        onClick = {
                                            val shareUrl = "https://music-mu-lovat.vercel.app/public/playlist/${playlist.id}"
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "Listen to \"${playlist.name}\" on Cantio: $shareUrl")
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Playlist"))
                                        }
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = NeonViolet)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // Track List items
                        if (tracks.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("This playlist is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(16.dp))
                                        Button(
                                            onClick = onNavigateToSearch,
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                                        ) {
                                            Text("Search Songs")
                                        }
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(tracks) { index, track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val queue = tracks.map {
                                                Track(it.trackId, it.title, it.artist, it.thumbnail, it.duration)
                                            }
                                            playerConnection.playQueue(queue, index)
                                            onNavigateToNowPlaying()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp)
                                    )
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
                                    IconButton(
                                        onClick = { viewModel.removeTrack(track.trackId) },
                                        enabled = uiState.isRemoving != track.trackId
                                    ) {
                                        if (uiState.isRemoving == track.trackId) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error
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
