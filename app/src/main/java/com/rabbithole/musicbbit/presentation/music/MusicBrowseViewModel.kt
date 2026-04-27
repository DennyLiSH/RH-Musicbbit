package com.rabbithole.musicbbit.presentation.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

sealed interface MusicUiState {
    data object Loading : MusicUiState
    data object NoScanDirectory : MusicUiState
    data object Empty : MusicUiState
    data class Success(
        val songs: List<Song>,
        val searchQuery: String = ""
    ) : MusicUiState
}

sealed interface MusicBrowseAction {
    data class OnSearchQueryChange(val query: String) : MusicBrowseAction
    data object OnNavigateToSettings : MusicBrowseAction
    data class OnSongClick(val song: Song) : MusicBrowseAction
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicBrowseViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val scanDirectoryRepository: ScanDirectoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MusicUiState>(MusicUiState.Loading)
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        observeData()
    }

    private fun observeData() {
        combine(
            scanDirectoryRepository.getAll(),
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) musicRepository.getAllSongs() else musicRepository.searchSongs(query)
                }
        ) { directories, songs ->
            when {
                directories.isEmpty() -> MusicUiState.NoScanDirectory
                songs.isEmpty() -> MusicUiState.Empty
                else -> MusicUiState.Success(
                    songs = songs,
                    searchQuery = _searchQuery.value
                )
            }
        }
            .onEach { state -> _uiState.value = state }
            .launchIn(viewModelScope)
    }

    fun onAction(action: MusicBrowseAction) {
        when (action) {
            is MusicBrowseAction.OnSearchQueryChange -> {
                _searchQuery.update { action.query }
            }

            else -> { /* Navigation handled in UI layer */ }
        }
    }
}
