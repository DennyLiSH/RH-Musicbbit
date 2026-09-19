package com.rabbithole.musicbbit.presentation.music

import com.rabbithole.musicbbit.service.playback.UserPlaybackSession
import com.rabbithole.musicbbit.presentation.playback.LocalPlaybackSession
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.navigation.Player
import com.rabbithole.musicbbit.navigation.ScanDirectorySettings
import com.rabbithole.musicbbit.presentation.music.components.SongListItem
import com.rabbithole.musicbbit.presentation.components.EmptyState
import com.rabbithole.musicbbit.presentation.components.ErrorContent
import com.rabbithole.musicbbit.presentation.components.LoadingState
import com.rabbithole.musicbbit.presentation.components.SongSearchField
import com.rabbithole.musicbbit.ui.theme.MotionTokens
import com.rabbithole.musicbbit.presentation.player.components.AddToPlaylistBottomSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MusicBrowseScreen(
    navController: NavController,
    viewModel: MusicBrowseViewModel = hiltViewModel(),
    playerViewModel: UserPlaybackSession = LocalPlaybackSession.current
) {
    val context = LocalContext.current

    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    if (!hasPermission) {
        EmptyState(
            title = stringResource(R.string.music_browse_permission_required),
            icon = rememberVectorPainter(Icons.Default.MusicNote),
            actionLabel = stringResource(R.string.music_browse_grant_access),
            onAction = { permissionLauncher.launch(permission) }
        )
        return
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.music_browse_title)) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(
                targetState = uiState,
                animationSpec = tween(durationMillis = MotionTokens.DurationLong, easing = MotionTokens.EasingEmphasized),
                modifier = Modifier.fillMaxSize(),
                label = "MusicBrowseState"
            ) { state ->
                when (state) {
                    is MusicUiState.Loading -> {
                        LoadingState()
                    }

                    is MusicUiState.Error -> {
                        ErrorContent(
                            message = stringResource(state.messageResId),
                            icon = rememberVectorPainter(Icons.Filled.Error),
                            onRetry = viewModel::retry
                        )
                    }

                    is MusicUiState.NoScanDirectory -> {
                        EmptyState(
                            title = stringResource(R.string.music_browse_no_directory),
                            icon = rememberVectorPainter(Icons.Default.Folder),
                            actionLabel = stringResource(R.string.music_browse_go_to_settings),
                            onAction = { navController.navigate(ScanDirectorySettings) }
                        )
                    }

                    is MusicUiState.Empty -> {
                        EmptyState(
                            title = stringResource(R.string.music_browse_empty)
                        )
                    }

                    is MusicUiState.Success -> {
                        SuccessContent(
                            songs = state.songs,
                            searchQuery = state.searchQuery,
                            onSearchQueryChange = { viewModel.onAction(MusicBrowseAction.OnSearchQueryChange(it)) },
                            onSongClick = { song ->
                                viewModel.onAction(MusicBrowseAction.OnSongClick(song))
                                playerViewModel.play(song, playlistId = -1)
                                navController.navigate(Player)
                            },
                            onAddToPlaylist = { song ->
                                viewModel.onAction(MusicBrowseAction.OnSongClick(song))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessContent(
    songs: List<Song>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    var selectedSongForPlaylist by remember { mutableStateOf<Song?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        SongSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = stringResource(R.string.music_browse_search_placeholder),
            leadingIconContentDescription = stringResource(R.string.music_browse_search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = songs,
                key = { it.id }
            ) { song ->
                var showMenu by remember { mutableStateOf(false) }

                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onLongClick = { showMenu = true }
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.music_browse_add_to_playlist)) },
                        onClick = {
                            showMenu = false
                            selectedSongForPlaylist = song
                        }
                    )
                }
            }
        }
    }

    selectedSongForPlaylist?.let { song ->
        AddToPlaylistBottomSheet(
            songId = song.id,
            onDismiss = { selectedSongForPlaylist = null }
        )
    }
}
