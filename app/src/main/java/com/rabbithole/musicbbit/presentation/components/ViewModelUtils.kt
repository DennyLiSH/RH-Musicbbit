package com.rabbithole.musicbbit.presentation.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.rabbithole.musicbbit.presentation.player.PlayerViewModel

@Composable
fun rememberActivityScopedPlayerViewModel(): PlayerViewModel {
    val activity = checkNotNull(LocalActivity.current) { "No Activity available" } as ComponentActivity
    return hiltViewModel(viewModelStoreOwner = activity)
}
