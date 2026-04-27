package com.rabbithole.musicbbit.presentation.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.domain.usecase.CreatePlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlaylistListUiState {
    data object Loading : PlaylistListUiState
    data class Success(
        val playlists: List<Playlist>,
        val errorMessageResId: Int? = null
    ) : PlaylistListUiState
}

sealed interface PlaylistListAction {
    data class OnCreatePlaylist(val name: String) : PlaylistListAction
    data class OnDeletePlaylist(val playlist: Playlist) : PlaylistListAction
    data class OnPlaylistClick(val playlistId: Long) : PlaylistListAction
}

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val createPlaylistUseCase: CreatePlaylistUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaylistListUiState>(PlaylistListUiState.Loading)
    val uiState: StateFlow<PlaylistListUiState> = _uiState.asStateFlow()

    init {
        playlistRepository.getAllPlaylists()
            .onEach { playlists ->
                _uiState.update { PlaylistListUiState.Success(playlists) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: PlaylistListAction) {
        when (action) {
            is PlaylistListAction.OnCreatePlaylist -> {
                viewModelScope.launch {
                    createPlaylistUseCase(action.name)
                        .onFailure { e ->
                            Timber.w(e, "Failed to create playlist")
                            _uiState.update {
                                (it as? PlaylistListUiState.Success)?.copy(
                                    errorMessageResId = R.string.playlist_error_add_song_failed
                                ) ?: it
                            }
                        }
                }
            }
            is PlaylistListAction.OnDeletePlaylist -> {
                viewModelScope.launch {
                    playlistRepository.deletePlaylist(action.playlist)
                        .onFailure { e ->
                            Timber.w(e, "Failed to delete playlist")
                            _uiState.update {
                                (it as? PlaylistListUiState.Success)?.copy(
                                    errorMessageResId = R.string.playlist_error_delete_failed
                                ) ?: it
                            }
                        }
                }
            }
            else -> { /* Navigation handled in UI */ }
        }
    }
}
