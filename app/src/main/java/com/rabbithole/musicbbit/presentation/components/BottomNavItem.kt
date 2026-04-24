package com.rabbithole.musicbbit.presentation.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.graphics.vector.ImageVector
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.navigation.Alarm
import com.rabbithole.musicbbit.navigation.MusicBrowse
import com.rabbithole.musicbbit.navigation.PlaylistList
import kotlinx.serialization.Serializable

enum class BottomNavItem(
    val screen: @Serializable Any,
    @StringRes val labelResId: Int,
    val icon: ImageVector
) {
    Music(
        screen = MusicBrowse,
        labelResId = R.string.tab_music,
        icon = Icons.Default.MusicNote
    ),
    Playlist(
        screen = PlaylistList,
        labelResId = R.string.tab_playlist,
        icon = Icons.AutoMirrored.Filled.PlaylistPlay
    ),
    Alarms(
        screen = Alarm,
        labelResId = R.string.tab_alarm,
        icon = Icons.Default.Alarm
    );

    companion object {
        val topLevelRoutes = entries.map { it.screen::class.qualifiedName }
    }
}
