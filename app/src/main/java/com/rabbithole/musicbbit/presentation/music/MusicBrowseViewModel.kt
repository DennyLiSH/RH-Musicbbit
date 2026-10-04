package com.rabbithole.musicbbit.presentation.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import com.rabbithole.musicbbit.presentation.components.ListUiState
import com.rabbithole.musicbbit.presentation.permissions.PermissionStatus
import com.rabbithole.musicbbit.presentation.permissions.PermissionStatusMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber

/** Content-level variants of the music browse screen (loaded via [ListUiState]). */
sealed interface MusicBrowseData {
    data object NoScanDirectory : MusicBrowseData
    data object Empty : MusicBrowseData
    data class Songs(val songs: List<Song>, val searchQuery: String) : MusicBrowseData
}

sealed interface MusicBrowseAction {
    data class OnSearchQueryChange(val query: String) : MusicBrowseAction
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicBrowseViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val scanDirectoryRepository: ScanDirectoryRepository,
    private val permissionStatusMonitor: PermissionStatusMonitor,
) : ViewModel() {

    val permissionStatus: StateFlow<PermissionStatus> = permissionStatusMonitor.status

    fun refreshPermissionStatus() = permissionStatusMonitor.refresh()

    private val _searchQuery = MutableStateFlow("")
    private val loadTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ListUiState<MusicBrowseData>> = loadTrigger
        .flatMapLatest {
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
                    directories.isEmpty() -> MusicBrowseData.NoScanDirectory
                    songs.isEmpty() -> MusicBrowseData.Empty
                    else -> MusicBrowseData.Songs(songs = songs, searchQuery = _searchQuery.value)
                }
            }
                .map<MusicBrowseData, ListUiState<MusicBrowseData>> { ListUiState.Content(it) }
                .onStart { emit(ListUiState.Loading) }
                .catch { e ->
                    Timber.e(e, "Flow collection failed: MusicBrowse data")
                    emit(ListUiState.Error(R.string.error_load_failed))
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListUiState.Loading)

    fun retry() {
        loadTrigger.update { it + 1 }
    }

    fun onAction(action: MusicBrowseAction) {
        when (action) {
            is MusicBrowseAction.OnSearchQueryChange -> _searchQuery.update { action.query }
        }
    }
}