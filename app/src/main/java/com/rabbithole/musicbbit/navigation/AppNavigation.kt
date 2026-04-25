package com.rabbithole.musicbbit.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rabbithole.musicbbit.presentation.alarm.AlarmEditScreen
import com.rabbithole.musicbbit.presentation.alarm.AlarmListScreen
import com.rabbithole.musicbbit.presentation.components.BottomNavItem
import com.rabbithole.musicbbit.presentation.music.MusicBrowseScreen
import com.rabbithole.musicbbit.presentation.player.MiniPlayer
import com.rabbithole.musicbbit.presentation.player.PlayerScreen
import com.rabbithole.musicbbit.presentation.playlist.PlaylistDetailScreen
import com.rabbithole.musicbbit.presentation.playlist.PlaylistListScreen
import com.rabbithole.musicbbit.presentation.settings.PermissionDiagnosticsScreen
import com.rabbithole.musicbbit.presentation.settings.ScanDirectorySettingsScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(navController = navController)

                // Show bottom nav only on top-level destinations
                val isTopLevel = BottomNavItem.entries.any {
                    currentDestination?.hasRoute(it.screen::class) == true
                }
                if (isTopLevel) {
                    NavigationBar {
                        BottomNavItem.entries.forEach { item ->
                            NavigationBarItem(
                                selected = currentDestination?.hasRoute(item.screen::class) == true,
                                onClick = {
                                    navController.navigate(item.screen) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.labelResId)) }
                            )
                        }
                    }
                }
            }
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
                    AlarmListScreen(navController = navController)
                }
                composable<AlarmEdit> {
                    AlarmEditScreen(navController = navController)
                }
                composable<Player> {
                    PlayerScreen(navController = navController)
                }
                composable<ScanDirectorySettings> {
                    ScanDirectorySettingsScreen(navController = navController)
                }
                composable<PermissionDiagnostics> {
                    PermissionDiagnosticsScreen(navController = navController)
                }
            }
        }
    }
}
