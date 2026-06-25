package com.appplayer.music.ui.navigation

object Screen {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val NOW_PLAYING = "now_playing"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val BLEND_DETAIL = "blend/{blendId}"
    const val LOGIN = "login"
    const val REGISTER = "register"

    fun playlistDetail(id: String) = "playlist/$id"
    fun blendDetail(id: String) = "blend/$id"
}

sealed class BottomNavItem(val route: String, val label: String, val iconRes: String) {
    object Home     : BottomNavItem(Screen.HOME,    "Home",    "home")
    object Search   : BottomNavItem(Screen.SEARCH,  "Search",  "search")
    object Library  : BottomNavItem(Screen.LIBRARY, "Library", "library")
}
