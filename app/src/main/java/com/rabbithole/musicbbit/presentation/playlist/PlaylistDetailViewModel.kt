package com.rabbithole.musicbbit.presentation.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.usecase.AddSongToPlaylistUseCase
import com.rabbithole.musicbbit.domain.usecase.GetPlaylistWithSongsUseCase
import com.rabbithole.musicbbit.domain.usecase.GetSongsUseCase
import com.rabbithole.musicbbit.domain.usecase.RemoveSongFromPlaylistUseCase
import com.rabbithole.musicbbit.domain.usecase.ReorderPlaylistSongsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    data class Success(val playlistWithSongs: PlaylistWithSongs) : PlaylistDetailUiState
}

sealed interface PlaylistDetailAction {
    data class OnRemoveSong(val songId: Long) : PlaylistDetailAction
    data class OnPlayPlaylist(val startIndex: Int) : PlaylistDetailAction
    data class OnReorderSongs(val fromIndex: Int, val toIndex: Int) : PlaylistDetailAction
    data class OnAddSongs(val songIds: List<Long>) : PlaylistDetailAction
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistWithSongsUseCase: GetPlaylistWithSongsUseCase,
    private val removeSongFromPlaylistUseCase: RemoveSongFromPlaylistUseCase,
    private val reorderPlaylistSongsUseCase: ReorderPlaylistSongsUseCase,
    private val getSongsUseCase: GetSongsUseCase,
    private val addSongToPlaylistUseCase: AddSongToPlaylistUseCase
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle["playlistId"]) { "playlistId required" }

    private val _uiState = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    val allSongs: StateFlow<List<Song>> = getSongsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        getPlaylistWithSongsUseCase(playlistId)
            .onEach { playlistWithSongs ->
                if (playlistWithSongs != null) {
                    _uiState.value = PlaylistDetailUiState.Success(playlistWithSongs)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: PlaylistDetailAction) {
        when (action) {
            is PlaylistDetailAction.OnRemoveSong -> {
                viewModelScope.launch {
                    removeSongFromPlaylistUseCase(playlistId, action.songId)
                }
            }
            is PlaylistDetailAction.OnReorderSongs -> {
                val currentState = _uiState.value as? PlaylistDetailUiState.Success
                    ?: return
                val songs = currentState.playlistWithSongs.songs
                val fromIndex = action.fromIndex
                val toIndex = action.toIndex
                if (fromIndex !in songs.indices || toIndex !in songs.indices) return

                val reordered = songs.toMutableList()
                val movedSong = reordered.removeAt(fromIndex)
                reordered.add(toIndex, movedSong)

                _uiState.value = PlaylistDetailUiState.Success(
                    currentState.playlistWithSongs.copy(songs = reordered)
                )

                viewModelScope.launch {
                    val songIds = reordered.map { it.id }
                    val result = reorderPlaylistSongsUseCase(playlistId, songIds)
                    if (result.isFailure) {
                        _uiState.value = PlaylistDetailUiState.Success(
                            currentState.playlistWithSongs.copy(songs = songs)
                        )
                    }
                }
            }
            is PlaylistDetailAction.OnAddSongs -> {
                viewModelScope.launch {
                    val result = addSongToPlaylistUseCase(playlistId, action.songIds)
                    if (result.isFailure) {
                        Timber.w(result.exceptionOrNull(), "Failed to add songs to playlist $playlistId")
                    }
                }
            }
            else -> { /* Play handled in UI via StateHolder */ }
        }
    }
}
