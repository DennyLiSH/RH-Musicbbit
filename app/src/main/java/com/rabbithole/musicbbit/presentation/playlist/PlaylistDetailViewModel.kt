package com.rabbithole.musicbbit.presentation.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.usecase.GetPlaylistWithSongsUseCase
import com.rabbithole.musicbbit.domain.usecase.RemoveSongFromPlaylistUseCase
import com.rabbithole.musicbbit.domain.usecase.ReorderPlaylistSongsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    data class Success(val playlistWithSongs: PlaylistWithSongs) : PlaylistDetailUiState
}

sealed interface PlaylistDetailAction {
    data class OnRemoveSong(val songId: Long) : PlaylistDetailAction
    data class OnPlayPlaylist(val startIndex: Int) : PlaylistDetailAction
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistWithSongsUseCase: GetPlaylistWithSongsUseCase,
    private val removeSongFromPlaylistUseCase: RemoveSongFromPlaylistUseCase,
    private val reorderPlaylistSongsUseCase: ReorderPlaylistSongsUseCase
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle["playlistId"]) { "playlistId required" }

    private val _uiState = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

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
            else -> { /* Play handled in UI via StateHolder */ }
        }
    }
}
