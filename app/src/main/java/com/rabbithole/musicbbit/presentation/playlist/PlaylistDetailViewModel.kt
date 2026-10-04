package com.rabbithole.musicbbit.presentation.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.navigation.PlaylistDetail
import com.rabbithole.musicbbit.presentation.components.ListUiState
import com.rabbithole.musicbbit.presentation.components.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface PlaylistDetailAction {
    data class OnRemoveSong(val songId: Long) : PlaylistDetailAction
    data class OnReorderSongs(val fromIndex: Int, val toIndex: Int) : PlaylistDetailAction
    data class OnAddSongs(val songIds: List<Long>) : PlaylistDetailAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    musicRepository: MusicRepository,
) : ViewModel() {

    val playlistId: Long = savedStateHandle.toRoute<PlaylistDetail>().playlistId

    val allSongs: StateFlow<List<Song>> = musicRepository.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val loadTrigger = MutableStateFlow(0)

    /** null = playlist does not exist (deleted) — screen renders "not found". */
    val uiState: StateFlow<ListUiState<PlaylistWithSongs?>> = loadTrigger
        .flatMapLatest {
            playlistRepository.getPlaylistWithSongs(playlistId)
                .map<PlaylistWithSongs?, ListUiState<PlaylistWithSongs?>> { ListUiState.Content(it) }
                .onStart { emit(ListUiState.Loading) }
                .catch { e ->
                    Timber.e(e, "Failed to load playlist with songs")
                    emit(ListUiState.Error(R.string.error_load_failed))
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListUiState.Loading)

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /**
     * Optimistic reorder preview: while non-null the UI shows this order instead of the
     * repository flow (which only updates after persist). Cleared on success/failure —
     * failure reverts to repository truth.
     */
    private val _reorderPreview = MutableStateFlow<PlaylistWithSongs?>(null)
    val reorderPreview: StateFlow<PlaylistWithSongs?> = _reorderPreview.asStateFlow()

    fun retry() {
        loadTrigger.update { it + 1 }
    }

    fun onAction(action: PlaylistDetailAction) {
        when (action) {
            is PlaylistDetailAction.OnRemoveSong -> {
                viewModelScope.launch {
                    playlistRepository.removeSongFromPlaylist(playlistId, action.songId)
                        .onFailure { e ->
                            Timber.w(e, "Failed to remove song from playlist")
                            val sent = _messages.trySend(UserMessage(R.string.playlist_error_remove_song_failed))
                            if (!sent.isSuccess) Timber.w("UserMessage dropped: channel full or closed")
                        }
                }
            }
            is PlaylistDetailAction.OnReorderSongs -> {
                val currentState = uiState.value as? ListUiState.Content
                    ?: return
                val playlistWithSongs = currentState.data ?: return
                val songs = playlistWithSongs.songs
                if (action.fromIndex !in songs.indices || action.toIndex !in songs.indices) return

                val reordered = songs.toMutableList().apply {
                    add(action.toIndex, removeAt(action.fromIndex))
                }
                _reorderPreview.value = playlistWithSongs.copy(songs = reordered)

                viewModelScope.launch {
                    playlistRepository.reorderPlaylistSongs(playlistId, reordered.map { it.id })
                        .onSuccess { _reorderPreview.value = null }
                        .onFailure { e ->
                            Timber.w(e, "Failed to reorder songs")
                            _reorderPreview.value = null
                            val sent = _messages.trySend(UserMessage(R.string.playlist_error_reorder_failed))
                            if (!sent.isSuccess) Timber.w("UserMessage dropped: channel full or closed")
                        }
                }
            }
            is PlaylistDetailAction.OnAddSongs -> {
                viewModelScope.launch {
                    playlistRepository.addSongsToPlaylist(playlistId, action.songIds)
                        .onFailure { e ->
                            Timber.w(e, "Failed to add songs to playlist $playlistId")
                            val sent = _messages.trySend(UserMessage(R.string.playlist_error_add_song_failed))
                            if (!sent.isSuccess) Timber.w("UserMessage dropped: channel full or closed")
                        }
                }
            }
        }
    }
}