package com.rabbithole.musicbbit.presentation.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.presentation.components.ListUiState
import com.rabbithole.musicbbit.presentation.components.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface PlaylistListAction {
    data class OnCreatePlaylist(val name: String) : PlaylistListAction
    data class OnDeletePlaylist(val playlist: Playlist) : PlaylistListAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val loadTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ListUiState<List<Playlist>>> = loadTrigger
        .flatMapLatest {
            playlistRepository.getAllPlaylists()
                .map<List<Playlist>, ListUiState<List<Playlist>>> { ListUiState.Content(it) }
                .onStart { emit(ListUiState.Loading) }
                .catch { e ->
                    Timber.e(e, "Failed to load playlists")
                    emit(ListUiState.Error(R.string.error_load_failed))
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListUiState.Loading)

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun retry() {
        loadTrigger.update { it + 1 }
    }

    fun onAction(action: PlaylistListAction) {
        when (action) {
            is PlaylistListAction.OnCreatePlaylist -> {
                if (action.name.isBlank()) {
                    val sent = _messages.trySend(UserMessage(R.string.playlist_error_add_song_failed))
                    if (!sent.isSuccess) Timber.w("UserMessage dropped: channel full or closed")
                    return
                }
                viewModelScope.launch {
                    playlistRepository.createPlaylist(action.name)
                        .onFailure { e ->
                            Timber.w(e, "Failed to create playlist")
                            val sent = _messages.trySend(UserMessage(R.string.playlist_error_add_song_failed))
                            if (!sent.isSuccess) Timber.w("UserMessage dropped: channel full or closed")
                        }
                }
            }
            is PlaylistListAction.OnDeletePlaylist -> {
                viewModelScope.launch {
                    playlistRepository.deletePlaylist(action.playlist)
                        .onFailure { e ->
                            Timber.w(e, "Failed to delete playlist")
                            val sent = _messages.trySend(UserMessage(R.string.playlist_error_delete_failed))
                            if (!sent.isSuccess) Timber.w("UserMessage dropped: channel full or closed")
                        }
                }
            }
        }
    }
}