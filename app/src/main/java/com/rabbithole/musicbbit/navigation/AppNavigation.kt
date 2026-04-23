package com.rabbithole.musicbbit.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rabbithole.musicbbit.presentation.music.MusicBrowseScreen
import com.rabbithole.musicbbit.presentation.player.MiniPlayer
import com.rabbithole.musicbbit.presentation.player.PlayerScreen
import com.rabbithole.musicbbit.presentation.playlist.PlaylistDetailScreen
import com.rabbithole.musicbbit.presentation.playlist.PlaylistListScreen
import com.rabbithole.musicbbit.presentation.settings.ScanDirectorySettingsScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        bottomBar = {
            MiniPlayer(navController = navController)
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = MusicBrowse
            ) {
                composable<MusicBrowse> {
                    MusicBrowseScreen(navController = navController)
                }
                composable<PlaylistList> {
                    PlaylistListScreen(navController = navController)
                }
                composable<PlaylistDetail> {
                    PlaylistDetailScreen(navController = navController)
                }
                composable<Alarm> {
                    Text("Alarm - Placeholder")
                }
                composable<Player> {
                    PlayerScreen(navController = navController)
                }
                composable<ScanDirectorySettings> {
                    ScanDirectorySettingsScreen(navController = navController)
                }
            }
        }
    }
}
