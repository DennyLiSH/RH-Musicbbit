package com.rabbithole.musicbbit.presentation.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.usecase.CreatePlaylistUseCase
import com.rabbithole.musicbbit.domain.usecase.DeletePlaylistUseCase
import com.rabbithole.musicbbit.domain.usecase.GetPlaylistsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlaylistListUiState {
    data object Loading : PlaylistListUiState
    data class Success(val playlists: List<Playlist>) : PlaylistListUiState
}

sealed interface PlaylistListAction {
    data class OnCreatePlaylist(val name: String) : PlaylistListAction
    data class OnDeletePlaylist(val playlist: Playlist) : PlaylistListAction
    data class OnPlaylistClick(val playlistId: Long) : PlaylistListAction
}

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaylistListUiState>(PlaylistListUiState.Loading)
    val uiState: StateFlow<PlaylistListUiState> = _uiState.asStateFlow()

    init {
        getPlaylistsUseCase()
            .onEach { playlists ->
                _uiState.value = PlaylistListUiState.Success(playlists)
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: PlaylistListAction) {
        when (action) {
            is PlaylistListAction.OnCreatePlaylist -> {
                viewModelScope.launch {
                    createPlaylistUseCase(action.name)
                }
            }
            is PlaylistListAction.OnDeletePlaylist -> {
                viewModelScope.launch {
                    deletePlaylistUseCase(action.playlist)
                }
            }
            else -> { /* Navigation handled in UI */ }
        }
    }
}
