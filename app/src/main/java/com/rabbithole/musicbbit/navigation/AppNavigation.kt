package com.rabbithole.musicbbit.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = MusicBrowse
    ) {
        composable<MusicBrowse> {
            Text("Music Browse - Placeholder")
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
    }
}
