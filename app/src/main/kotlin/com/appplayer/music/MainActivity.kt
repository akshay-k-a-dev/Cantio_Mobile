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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playerConnection.connect()

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                android.util.Log.d("YTPlayerTest", "TEST: Resolving playback stream for VMmLBjcEXrM...")
                val result = ytPlayerUtils.playerResponseForPlayback("VMmLBjcEXrM")
                if (result.isSuccess) {
                    android.util.Log.d("YTPlayerTest", "TEST SUCCESS: client=${result.getOrNull()?.streamClient} streamUrl=${result.getOrNull()?.streamUrl}")
                } else {
                    android.util.Log.e("YTPlayerTest", "TEST FAILED: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                android.util.Log.e("YTPlayerTest", "TEST EXCEPTION: ${e.message}", e)
            }
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
                    onLoginSuccess = {
                        navController.navigate(Screen.HOME) {
                            popUpTo(Screen.LOGIN) { inclusive = true }
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
                    onLoginSuccess = {
                        navController.navigate(Screen.HOME) {
                            popUpTo(Screen.REGISTER) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = { navController.popBackStack() }
                )
            }

            composable(Screen.HOME) {
                HomeScreen(
                    playerConnection = playerConnection,
                    onNavigateToNowPlaying = { navController.navigate(Screen.NOW_PLAYING) }
                )
            }

            composable(Screen.SEARCH) {
                SearchScreen(
                    playerConnection = playerConnection,
                    onNavigateToNowPlaying = { navController.navigate(Screen.NOW_PLAYING) }
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
        }
    }
}
