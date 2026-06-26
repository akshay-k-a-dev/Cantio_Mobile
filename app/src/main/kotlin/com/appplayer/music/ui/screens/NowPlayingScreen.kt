package com.appplayer.music.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.appplayer.music.playback.PlayerConnection
import com.appplayer.music.ui.theme.DarkSurface2
import com.appplayer.music.ui.theme.NeonViolet
import com.appplayer.music.viewmodel.LyricsViewModel
import com.appplayer.music.viewmodel.NowPlayingViewModel
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay

enum class PlayerTab { ARTWORK, LYRICS, QUEUE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerConnection: PlayerConnection,
    onBack: () -> Unit,
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
    nowPlayingViewModel: NowPlayingViewModel = hiltViewModel()
) {
    val currentTrack by playerConnection.currentTrack.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val duration by playerConnection.playbackDuration.collectAsState()
    val positionState by playerConnection.playbackPosition.collectAsState()

    val lyricsState by lyricsViewModel.uiState.collectAsState()
    var currentTab by remember { mutableStateOf(PlayerTab.ARTWORK) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    val sleepTimerTimeLeft by playerConnection.sleepTimerTimeLeft.collectAsState()

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Synchronize slider position with player position when not dragging
    LaunchedEffect(positionState, isDragging) {
        if (!isDragging) {
            sliderPosition = if (duration > 0) positionState.toFloat() / duration else 0f
        }
    }

    // Active polling to update progress when playing
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
                Row {
                    IconButton(onClick = {
                        currentTab = if (currentTab == PlayerTab.LYRICS) PlayerTab.ARTWORK else PlayerTab.LYRICS
                    }) {
                        Icon(
                            imageVector = Icons.Default.FormatAlignLeft,
                            contentDescription = "Lyrics",
                            tint = if (currentTab == PlayerTab.LYRICS) NeonViolet else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        currentTab = if (currentTab == PlayerTab.QUEUE) PlayerTab.ARTWORK else PlayerTab.QUEUE
                    }) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Queue",
                            tint = if (currentTab == PlayerTab.QUEUE) NeonViolet else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Central Area: Cover Art OR Lyrics Panel OR Queue Panel
            if (currentTab == PlayerTab.ARTWORK) {
                AsyncImage(
                    model = currentTrack?.artworkUri,
                    contentDescription = currentTrack?.title,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DarkSurface2)
                        .clickable { currentTab = PlayerTab.LYRICS },
                    contentScale = ContentScale.Crop
                )
            } else if (currentTab == PlayerTab.LYRICS) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DarkSurface2.copy(alpha = 0.5f))
                        .clickable { currentTab = PlayerTab.ARTWORK }
                ) {
                    if (lyricsState.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NeonViolet)
                        }
                    } else if (lyricsState.isInstrumental) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "🎵 Instrumental 🎵",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (lyricsState.error != null && lyricsState.syncedLines.isEmpty() && lyricsState.plainLyrics == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = lyricsState.error ?: "No lyrics found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else if (lyricsState.syncedLines.isNotEmpty()) {
                        val listState = rememberLazyListState()
                        val activeIndex = lyricsState.activeLineIndex

                        LaunchedEffect(activeIndex) {
                            if (activeIndex >= 0) {
                                listState.animateScrollToItem(maxOf(0, activeIndex - 2))
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 120.dp, horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(lyricsState.syncedLines) { index, line ->
                                val isActive = index == activeIndex
                                Text(
                                    text = line.text,
                                    style = if (isActive) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
                                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playerConnection.seekTo(line.timeMs)
                                        }
                                        .padding(vertical = 4.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else if (lyricsState.plainLyrics != null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Text(
                                    text = lyricsState.plainLyrics ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            } else if (currentTab == PlayerTab.QUEUE) {
                val queue by playerConnection.upcomingQueue.collectAsState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DarkSurface2.copy(alpha = 0.5f))
                ) {
                    if (queue.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No upcoming songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val listState = rememberLazyListState()
                        var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
                        var dragOffsetY by remember { mutableStateOf(0f) }
                        val density = LocalDensity.current
                        val itemHeightPx = with(density) { 72.dp.toPx() }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Queue Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "UPCOMING SONGS (${queue.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { playerConnection.clearQueue() }) {
                                    Text("CLEAR ALL", color = NeonViolet, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(queue) { index, track ->
                                    val isCurrent = currentTrack?.videoId == track.videoId
                                    val isDraggingThis = draggedItemIndex == index
                                    val graphicsModifier = if (isDraggingThis) {
                                        Modifier
                                            .zIndex(5f)
                                            .graphicsLayer {
                                                translationY = dragOffsetY
                                                scaleX = 1.04f
                                                scaleY = 1.04f
                                                shadowElevation = 8.dp.toPx()
                                            }
                                    } else {
                                        Modifier
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(graphicsModifier)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isCurrent) NeonViolet.copy(alpha = 0.15f) else Color.Transparent)
                                            .clickable { playerConnection.skipToUpcomingQueueItem(index) }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Drag Handle",
                                            tint = Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .pointerInput(index) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            draggedItemIndex = index
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragEnd = {
                                                            draggedItemIndex = null
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggedItemIndex = null
                                                            dragOffsetY = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetY += dragAmount.y
                                                            
                                                            val currentIndex = draggedItemIndex ?: return@detectDragGestures
                                                            if (dragOffsetY > itemHeightPx && currentIndex < queue.size - 1) {
                                                                playerConnection.moveUpcomingTrack(currentIndex, currentIndex + 1)
                                                                draggedItemIndex = currentIndex + 1
                                                                dragOffsetY -= itemHeightPx
                                                            } else if (dragOffsetY < -itemHeightPx && currentIndex > 0) {
                                                                playerConnection.moveUpcomingTrack(currentIndex, currentIndex - 1)
                                                                draggedItemIndex = currentIndex - 1
                                                                dragOffsetY += itemHeightPx
                                                            }
                                                        }
                                                    )
                                                }
                                        )
                                        AsyncImage(
                                            model = track.thumbnail,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DarkSurface2),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isCurrent) NeonViolet else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = track.artist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (index > 0) {
                                                IconButton(
                                                    onClick = { playerConnection.moveUpcomingTrack(index, index - 1) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowUpward,
                                                        contentDescription = "Move Up",
                                                        tint = Color.White.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            if (index < queue.size - 1) {
                                                IconButton(
                                                    onClick = { playerConnection.moveUpcomingTrack(index, index + 1) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDownward,
                                                        contentDescription = "Move Down",
                                                        tint = Color.White.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { playerConnection.removeUpcomingTrackAt(index) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(16.dp)
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

                    val isLiked by nowPlayingViewModel.isCurrentTrackLiked.collectAsState()

                    // Add to Playlist button
                    IconButton(onClick = {
                        nowPlayingViewModel.loadPlaylists()
                        showAddToPlaylistDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "Add to Playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Like button
                    IconButton(onClick = { nowPlayingViewModel.toggleLike() }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) "Unlike" else "Like",
                            tint = if (isLiked) NeonViolet else MaterialTheme.colorScheme.onSurfaceVariant
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

                Spacer(Modifier.height(16.dp))

                // Sleep Timer & Share buttons
                val context = androidx.compose.ui.platform.LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sleep Timer control
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sleepTimerTimeLeft > 0) NeonViolet.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { showSleepTimerDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerTimeLeft > 0) NeonViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        if (sleepTimerTimeLeft > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = formatTimerTime(sleepTimerTimeLeft),
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonViolet
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Media Output Switcher Button
                        IconButton(onClick = {
                            try {
                                val intent = android.content.Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
                                    putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                                } catch (e2: Exception) {
                                    // ignore
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Output Devices",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Share button
                        IconButton(onClick = {
                            val shareUrl = "https://music.akshayka.dev/track/${currentTrack?.videoId ?: ""}"
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, "Listen to \"${currentTrack?.title ?: ""}\" on Cantio: $shareUrl")
                                type = "text/plain"
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Primary Playback Controls
                val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
                val repeatMode by playerConnection.repeatMode.collectAsState()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { playerConnection.toggleShuffleMode() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffleEnabled) NeonViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

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

                    IconButton(onClick = { playerConnection.toggleRepeatMode() }) {
                        val repeatIcon = when (repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        }
                        val repeatTint = when (repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> NeonViolet
                        }
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = "Repeat",
                            tint = repeatTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    // ─── Add to Playlist Dialog ───────────────────────────────────────────────
    if (showAddToPlaylistDialog && currentTrack != null) {
        val playlists by nowPlayingViewModel.playlists.collectAsState()
        com.appplayer.music.ui.components.AddToQueueOrPlaylistDialog(
            track = com.appplayer.music.data.api.models.Track(
                videoId = currentTrack!!.videoId,
                title = currentTrack!!.title,
                artist = currentTrack!!.artist,
                thumbnail = currentTrack!!.artworkUri,
                duration = (playerConnection.playbackDuration.value / 1000).toInt().takeIf { it > 0 }
            ),
            playlists = playlists,
            onAddToQueue = { /* already in queue */ },
            onPlayNext = { /* already playing */ },
            onAddToPlaylist = { playlistId ->
                nowPlayingViewModel.addCurrentTrackToPlaylist(playlistId)
            },
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column {
                    val options = listOf(
                        "Off" to 0,
                        "15 Minutes" to 15,
                        "30 Minutes" to 30,
                        "45 Minutes" to 45,
                        "60 Minutes" to 60
                    )
                    options.forEach { (label, durationMinutes) ->
                        TextButton(
                            onClick = {
                                if (durationMinutes == 0) {
                                    playerConnection.cancelSleepTimer()
                                } else {
                                    playerConnection.startSleepTimer(durationMinutes)
                                }
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if ((durationMinutes == 0 && sleepTimerTimeLeft == 0L) ||
                                    (durationMinutes > 0 && sleepTimerTimeLeft > 0L && Math.abs(sleepTimerTimeLeft - durationMinutes * 60 * 1000L) < 10000L)) {
                                    NeonViolet
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Close", color = NeonViolet)
                }
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

private fun formatTimerTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
