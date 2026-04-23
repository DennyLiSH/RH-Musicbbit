package com.rabbithole.musicbbit.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rabbithole.musicbbit.presentation.music.MusicBrowseScreen
import com.rabbithole.musicbbit.presentation.settings.ScanDirectorySettingsScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = MusicBrowse
    ) {
        composable<MusicBrowse> {
            MusicBrowseScreen(navController = navController)
        }
        composable<Playlist> {
            Text("Playlist - Placeholder")
        }
        composable<Alarm> {
            Text("Alarm - Placeholder")
        }
        composable<Player> {
            Text("Player - Placeholder")
        }
        composable<ScanDirectorySettings> {
            ScanDirectorySettingsScreen(navController = navController)
        }
    }
}
