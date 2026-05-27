package com.rabbithole.musicbbit.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.rabbithole.musicbbit.presentation.player.PlayerViewModel

@Composable
fun rememberActivityScopedPlayerViewModel(): PlayerViewModel {
    val activity = LocalContext.current as ComponentActivity
    return hiltViewModel(viewModelStoreOwner = activity)
}
