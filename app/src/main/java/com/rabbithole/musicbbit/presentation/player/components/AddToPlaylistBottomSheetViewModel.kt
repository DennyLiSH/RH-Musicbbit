package com.rabbithole.musicbbit.presentation.player.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.domain.usecase.AddSongToPlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AddToPlaylistUiState {
    data object Loading : AddToPlaylistUiState
    data class Success(val playlists: List<Playlist>) : AddToPlaylistUiState
}

@HiltViewModel
class AddToPlaylistBottomSheetViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val addSongToPlaylistUseCase: AddSongToPlaylistUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddToPlaylistUiState>(AddToPlaylistUiState.Loading)
    val uiState: StateFlow<AddToPlaylistUiState> = _uiState.asStateFlow()

    init {
        playlistRepository.getAllPlaylists()
            .onEach { playlists ->
                _uiState.value = AddToPlaylistUiState.Success(playlists)
            }
            .launchIn(viewModelScope)
    }

    fun onPlaylistSelected(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            addSongToPlaylistUseCase(playlistId, songId)
        }
    }
}
