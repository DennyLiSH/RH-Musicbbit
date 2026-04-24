package com.rabbithole.musicbbit.presentation.playlist

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.presentation.music.components.SongListItem
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    navController: NavController,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when (val state = uiState) {
                        is PlaylistDetailUiState.Loading -> "Playlist"
                        is PlaylistDetailUiState.Success -> state.playlistWithSongs.playlist.name
                    }
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            when (val state = uiState) {
                is PlaylistDetailUiState.Loading -> {
                    LoadingContent()
                }

                is PlaylistDetailUiState.Success -> {
                    val playlistWithSongs = state.playlistWithSongs
                    if (playlistWithSongs.songs.isEmpty()) {
                        EmptyContent(playlistName = playlistWithSongs.playlist.name)
                    } else {
                        PlaylistDetailContent(
                            songs = playlistWithSongs.songs,
                            onPlayAll = {
                                viewModel.onAction(
                                    PlaylistDetailAction.OnPlayPlaylist(startIndex = 0)
                                )
                            },
                            onSongClick = { index ->
                                viewModel.onAction(
                                    PlaylistDetailAction.OnPlayPlaylist(startIndex = index)
                                )
                            },
                            onRemoveSong = { songId ->
                                viewModel.onAction(PlaylistDetailAction.OnRemoveSong(songId))
                            },
                            onReorderSongs = { fromIndex, toIndex ->
                                viewModel.onAction(
                                    PlaylistDetailAction.OnReorderSongs(fromIndex, toIndex)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(playlistName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = ""$playlistName" is empty",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add songs from the music library",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlaylistDetailContent(
    songs: List<Song>,
    onPlayAll: () -> Unit,
    onSongClick: (Int) -> Unit,
    onRemoveSong: (Long) -> Unit,
    onReorderSongs: (Int, Int) -> Unit
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
            Text("Play All")
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
                            scaleX = 1.02f
                            scaleY = 1.02f
                            shadowElevation = 8.dp.toPx()
                        }
                } else {
                    Modifier.animateItemPlacement()
                }

                SongListItemWithDelete(
                    song = song,
                    onClick = { onSongClick(index) },
                    onDelete = { onRemoveSong(song.id) },
                    onDragStart = { draggedIndex = index },
                    onDrag = { dragAmount ->
                        dragOffset += dragAmount
                        val itemHeight = 72f
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
                contentDescription = "Drag to reorder",
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
                contentDescription = "Remove from playlist",
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
