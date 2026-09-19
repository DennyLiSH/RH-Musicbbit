package com.rabbithole.musicbbit.presentation.playlist

import com.rabbithole.musicbbit.service.playback.UserPlaybackSession
import com.rabbithole.musicbbit.presentation.playback.LocalPlaybackSession
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.navigation.Player
import com.rabbithole.musicbbit.presentation.components.EmptyState
import com.rabbithole.musicbbit.presentation.components.ErrorContent
import com.rabbithole.musicbbit.presentation.components.LoadingState
import com.rabbithole.musicbbit.presentation.music.components.SongListItem
import com.rabbithole.musicbbit.presentation.playlist.components.AddSongsBottomSheet
import com.rabbithole.musicbbit.ui.theme.MotionTokens
import kotlin.math.roundToInt

/**
 * Drag-reorder tuning values. [RowHeightPx] is an estimated row height in
 * pixels used for index math during drag (pre-density heuristic, unchanged
 * from the original implementation).
 */
private object DragTokens {
    const val RowHeightPx = 72f
    const val LiftedScale = 1.02f
    val LiftedShadowElevation = 8.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    navController: NavController,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: UserPlaybackSession = LocalPlaybackSession.current
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    var showAddSongsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when (val state = uiState) {
                        is PlaylistDetailUiState.Loading -> stringResource(R.string.playlist_detail_title_loading)
                        is PlaylistDetailUiState.Error -> stringResource(state.messageResId)
                        is PlaylistDetailUiState.Success -> state.playlistWithSongs.playlist.name
                    }
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
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
                label = "PlaylistDetailState"
            ) { state ->
                when (state) {
                    is PlaylistDetailUiState.Loading -> {
                        LoadingState()
                    }

                    is PlaylistDetailUiState.Error -> {
                        ErrorContent(message = stringResource(state.messageResId), icon = rememberVectorPainter(Icons.Filled.Error), onRetry = viewModel::retry)
                    }

                    is PlaylistDetailUiState.Success -> {
                        val playlistWithSongs = state.playlistWithSongs
                        val existingSongIds = remember(playlistWithSongs.songs) {
                            playlistWithSongs.songs.map { it.id }.toSet()
                        }
                        val availableSongs = remember(allSongs, existingSongIds) {
                            allSongs.filter { it.id !in existingSongIds }
                        }

                        if (playlistWithSongs.songs.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.playlist_detail_empty, playlistWithSongs.playlist.name),
                                icon = rememberVectorPainter(Icons.Default.MusicNote),
                                actionLabel = stringResource(R.string.add_songs_title),
                                onAction = { showAddSongsSheet = true }
                            )
                        } else {
                            PlaylistDetailContent(
                                songs = playlistWithSongs.songs,
                                playlistId = playlistWithSongs.playlist.id,
                                playerViewModel = playerViewModel,
                                navController = navController,
                                onPlayAll = {
                                    playerViewModel.playQueue(
                                        playlistWithSongs.songs,
                                        startIndex = 0,
                                        playlistId = playlistWithSongs.playlist.id
                                    )
                                    navController.navigate(Player)
                                },
                                onSongClick = { index ->
                                    playerViewModel.playQueue(
                                        playlistWithSongs.songs,
                                        startIndex = index,
                                        playlistId = playlistWithSongs.playlist.id
                                    )
                                    navController.navigate(Player)
                                },
                                onRemoveSong = { songId ->
                                    viewModel.onAction(PlaylistDetailAction.OnRemoveSong(songId))
                                },
                                onReorderSongs = { fromIndex, toIndex ->
                                    viewModel.onAction(
                                        PlaylistDetailAction.OnReorderSongs(fromIndex, toIndex)
                                    )
                                },
                                onAddSongsClick = { showAddSongsSheet = true }
                            )
                        }

                        if (showAddSongsSheet) {
                            AddSongsBottomSheet(
                                availableSongs = availableSongs,
                                onSongsSelected = { songIds ->
                                    viewModel.onAction(PlaylistDetailAction.OnAddSongs(songIds))
                                },
                                onDismiss = { showAddSongsSheet = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    songs: List<Song>,
    playlistId: Long,
    playerViewModel: UserPlaybackSession,
    navController: NavController,
    onPlayAll: () -> Unit,
    onSongClick: (Int) -> Unit,
    onRemoveSong: (Long) -> Unit,
    onReorderSongs: (Int, Int) -> Unit,
    onAddSongsClick: () -> Unit
) {
    var reorderedSongs by remember { mutableStateOf(songs) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(songs) {
        if (draggedIndex == -1) {
            reorderedSongs = songs
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilledTonalButton(
            onClick = onPlayAll,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(stringResource(R.string.play_all))
        }

        OutlinedButton(
            onClick = onAddSongsClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        ) {
            Text(stringResource(R.string.add_songs_title))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(
                items = reorderedSongs,
                key = { _, song -> song.id }
            ) { index, song ->
                val isDragging = index == draggedIndex
                val modifier = if (isDragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationY = dragOffset
                            scaleX = DragTokens.LiftedScale
                            scaleY = DragTokens.LiftedScale
                            shadowElevation = DragTokens.LiftedShadowElevation.toPx()
                        }
                } else {
                    Modifier
                }

                SongListItemWithDelete(
                    song = song,
                    onClick = { onSongClick(index) },
                    onDelete = { onRemoveSong(song.id) },
                    onDragStart = { draggedIndex = index },
                    onDrag = { dragAmount ->
                        dragOffset += dragAmount
                        val itemHeight = DragTokens.RowHeightPx
                        val currentOffset = index * itemHeight + dragOffset
                        val targetIndex = (currentOffset / itemHeight)
                            .roundToInt()
                            .coerceIn(0, reorderedSongs.size - 1)
                        if (targetIndex != draggedIndex && draggedIndex != -1) {
                            val newList = reorderedSongs.toMutableList()
                            newList.move(draggedIndex, targetIndex)
                            reorderedSongs = newList
                            draggedIndex = targetIndex
                            dragOffset = currentOffset - draggedIndex * itemHeight
                        }
                    },
                    onDragEnd = {
                        val originalIndex = songs.indexOfFirst { it.id == reorderedSongs[draggedIndex].id }
                        if (originalIndex != draggedIndex && draggedIndex != -1) {
                            onReorderSongs(originalIndex, draggedIndex)
                        }
                        draggedIndex = -1
                        dragOffset = 0f
                    },
                    isDragging = isDragging,
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun SongListItemWithDelete(
    song: Song,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { },
            modifier = Modifier.pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = {
                        onDragEnd()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                )
            }
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.playlist_detail_drag_to_reorder),
                tint = if (isDragging) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        SongListItem(
            song = song,
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.playlist_detail_remove_from_playlist),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    val item = removeAt(fromIndex)
    add(toIndex, item)
}
