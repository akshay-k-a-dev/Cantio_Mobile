package com.appplayer.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.appplayer.music.playback.PlayerConnection
import com.appplayer.music.ui.components.MiniPlayer
import com.appplayer.music.ui.navigation.Screen
import com.appplayer.music.ui.screens.HomeScreen
import com.appplayer.music.ui.screens.LibraryScreen
import com.appplayer.music.ui.screens.LoginScreen
import com.appplayer.music.ui.screens.NowPlayingScreen
import com.appplayer.music.ui.screens.SearchScreen
import com.appplayer.music.ui.screens.PlaylistDetailScreen
import com.appplayer.music.ui.screens.BlendDetailScreen
import com.appplayer.music.ui.screens.YTMusicDetailScreen
import com.appplayer.music.ui.screens.SettingsScreen
import com.appplayer.music.ui.screens.OnboardingScreen
import com.appplayer.music.ui.theme.AppPlayerTheme
import com.appplayer.music.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerConnection: PlayerConnection
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var ytPlayerUtils: com.appplayer.music.utils.YTPlayerUtils
    @Inject lateinit var cacheManager: com.appplayer.music.data.cache.CacheManager
    @Inject lateinit var musicRepository: com.appplayer.music.data.repository.MusicRepository

    val deepLinkFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playerConnection.connect()

        intent?.data?.toString()?.let { deepLinkFlow.tryEmit(it) }

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            cacheManager.evictOldCacheFiles()
        }

        setContent {
            AppPlayerTheme {
                AppContent(
                    playerConnection = playerConnection,
                    isLoggedIn = tokenManager.isLoggedIn()
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let { deepLinkFlow.tryEmit(it) }
    }

    override fun onDestroy() {
        playerConnection.disconnect()
        super.onDestroy()
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(
    playerConnection: PlayerConnection,
    isLoggedIn: Boolean
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        (context as? MainActivity)?.deepLinkFlow?.collect { urlString ->
            try {
                val uri = android.net.Uri.parse(urlString)
                val pathSegments = uri.pathSegments
                if (pathSegments.isNotEmpty()) {
                    val type = pathSegments[0]
                    when (type) {
                        "p" -> {
                            if (pathSegments.size >= 2) {
                                val id = pathSegments[1]
                                navController.navigate(Screen.playlistDetail(id))
                            }
                        }
                        "public" -> {
                            if (pathSegments.size >= 3 && pathSegments[1] == "playlist") {
                                val id = pathSegments[2]
                                navController.navigate(Screen.playlistDetail(id))
                            }
                        }
                        "playlist" -> {
                            if (pathSegments.size >= 2) {
                                val id = pathSegments[1]
                                navController.navigate(Screen.playlistDetail(id))
                            }
                        }
                        "blend", "blends" -> {
                            if (pathSegments.size >= 2) {
                                val id = pathSegments[1]
                                navController.navigate(Screen.blendDetail(id))
                            }
                        }
                        "track" -> {
                            if (pathSegments.size >= 2) {
                                val id = pathSegments[1]
                                val repo = (context as? MainActivity)?.musicRepository
                                if (repo != null) {
                                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                                        when (val res = repo.getTrackMetadata(id)) {
                                            is com.appplayer.music.data.repository.ApiResult.Success -> {
                                                val trackMetadata = res.data
                                                val track = com.appplayer.music.data.api.models.Track(
                                                    videoId = trackMetadata.videoId,
                                                    title = trackMetadata.title,
                                                    artist = trackMetadata.author?.name ?: "Unknown Artist",
                                                    thumbnail = trackMetadata.thumbnail?.url,
                                                    duration = trackMetadata.duration?.seconds
                                                )
                                                playerConnection.playQueue(listOf(track), 0)
                                                navController.navigate(Screen.NOW_PLAYING)
                                            }
                                            else -> {
                                                val track = com.appplayer.music.data.api.models.Track(
                                                    videoId = id,
                                                    title = "Track",
                                                    artist = "Unknown Artist",
                                                    thumbnail = null,
                                                    duration = null
                                                )
                                                playerConnection.playQueue(listOf(track), 0)
                                                navController.navigate(Screen.NOW_PLAYING)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    val currentDestination = navBackStackEntry?.destination

    val currentTrack by playerConnection.currentTrack.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val bottomNavItems = listOf(
        BottomNavItem(Screen.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(Screen.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
        BottomNavItem(Screen.LIBRARY, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
    )

    val showBottomBar = currentDestination?.route in listOf(Screen.HOME, Screen.SEARCH, Screen.LIBRARY)
    val showMiniPlayer = currentTrack != null && currentDestination?.route != Screen.NOW_PLAYING

    val startDestination = if (isLoggedIn) Screen.HOME else Screen.LOGIN

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                // Mini player above bottom nav
                AnimatedVisibility(
                    visible = showMiniPlayer && showBottomBar,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    MiniPlayer(
                        track = currentTrack!!,
                        isPlaying = isPlaying,
                        onPlayPause = { playerConnection.togglePlayPause() },
                        onClick = { navController.navigate(Screen.NOW_PLAYING) }
                    )
                }

                // Bottom navigation bar
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.LOGIN) {
                LoginScreen(
                    onLoginSuccess = { needsOnboarding ->
                        if (needsOnboarding) {
                            navController.navigate(Screen.ONBOARDING) {
                                popUpTo(Screen.LOGIN) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.HOME) {
                                popUpTo(Screen.LOGIN) { inclusive = true }
                            }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.REGISTER)
                    }
                )
            }

            composable(Screen.REGISTER) {
                LoginScreen( // Reuse with register flag
                    isRegisterMode = true,
                    onLoginSuccess = { needsOnboarding ->
                        if (needsOnboarding) {
                            navController.navigate(Screen.ONBOARDING) {
                                popUpTo(Screen.REGISTER) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.HOME) {
                                popUpTo(Screen.REGISTER) { inclusive = true }
                            }
                        }
                    },
                    onNavigateToRegister = { navController.popBackStack() }
                )
            }

            composable(Screen.ONBOARDING) {
                OnboardingScreen(
                    onOnboardingComplete = {
                        navController.navigate(Screen.HOME) {
                            popUpTo(Screen.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.HOME) {
                HomeScreen(
                    playerConnection = playerConnection,
                    onNavigateToNowPlaying = { navController.navigate(Screen.NOW_PLAYING) },
                    onNavigateToSettings = { navController.navigate(Screen.SETTINGS) }
                )
            }

            composable(Screen.SEARCH) {
                SearchScreen(
                    playerConnection = playerConnection,
                    onNavigateToNowPlaying = { navController.navigate(Screen.NOW_PLAYING) },
                    onNavigateToYTMusicDetail = { type, id -> navController.navigate(Screen.ytmusicDetail(type, id)) },
                    onNavigateToPlaylist = { id -> navController.navigate(Screen.playlistDetail(id)) }
                )
            }

            composable(Screen.LIBRARY) {
                LibraryScreen(
                    playerConnection = playerConnection,
                    onNavigateToPlaylist = { id -> navController.navigate(Screen.playlistDetail(id)) },
                    onNavigateToBlend = { id -> navController.navigate(Screen.blendDetail(id)) },
                    onNavigateToNowPlaying = { navController.navigate(Screen.NOW_PLAYING) }
                )
            }

            composable(Screen.NOW_PLAYING) {
                NowPlayingScreen(
                    playerConnection = playerConnection,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.PLAYLIST_DETAIL) {
                PlaylistDetailScreen(
                    playerConnection = playerConnection,
                    onBack = { navController.popBackStack() },
                    onNavigateToSearch = { navController.navigate(Screen.SEARCH) },
                    onNavigateToNowPlaying = { navController.navigate(Screen.NOW_PLAYING) }
                )
            }

            composable(Screen.BLEND_DETAIL) {
                BlendDetailScreen(
                    playerConnection = playerConnection,
                    onBack = { navController.popBackStack() },
                    onNavigateToNowPlaying = { navController.navigate(Screen.NOW_PLAYING) }
                )
            }

            composable(Screen.YTMUSIC_DETAIL) {
                YTMusicDetailScreen(
                    playerConnection = playerConnection,
                    onBack = { navController.popBackStack() },
                    onNavigateToNowPlaying = { navController.navigate(Screen.NOW_PLAYING) }
                )
            }

            composable(Screen.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        navController.navigate(Screen.LOGIN) {
                            popUpTo(Screen.HOME) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
