package com.appplayer.music.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.appplayer.music.playback.PlayerConnection
import com.appplayer.music.ui.theme.DarkSurface2
import com.appplayer.music.ui.theme.GoldAccent
import com.appplayer.music.ui.theme.NeonViolet
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerConnection: PlayerConnection,
    onBack: () -> Unit
) {
    val currentTrack by playerConnection.currentTrack.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val duration by playerConnection.playbackDuration.collectAsState()
    val positionState by playerConnection.playbackPosition.collectAsState()

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Synchronize slider position with player position when not dragging
    LaunchedEffect(positionState, isDragging) {
        if (!isDragging) {
            sliderPosition = if (duration > 0) positionState.toFloat() / duration else 0f
        }
    }

    // Active polling to update progress when playing
    var localPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                if (!isDragging) {
                    sliderPosition = if (duration > 0) playerConnection.playbackPosition.value.toFloat() / duration else 0f
                }
                delay(500)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NeonViolet.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Cover Art
            AsyncImage(
                model = currentTrack?.artworkUri,
                contentDescription = currentTrack?.title,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkSurface2),
                contentScale = ContentScale.Crop
            )

            // Metadata & Controls block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Song details row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack?.title ?: "No track playing",
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = currentTrack?.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Progress Bar / Slider
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isDragging = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        playerConnection.seekTo((sliderPosition * duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = NeonViolet,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        thumbColor = NeonViolet
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Time labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentPosMs = (sliderPosition * duration).toLong()
                    Text(
                        text = formatTime(currentPosMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Primary Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { playerConnection.skipToPrevious() }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = { playerConnection.togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .background(NeonViolet, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(onClick = { playerConnection.skipToNext() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
