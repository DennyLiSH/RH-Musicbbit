package com.rabbithole.musicbbit.presentation.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.repository.MusicRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import com.rabbithole.musicbbit.domain.validation.ScanDirectoryValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
data class PendingDirectory(
    val path: String,
    val name: String
)

sealed interface ScanDirectorySettingsUiState {
    data object Loading : ScanDirectorySettingsUiState
    data class Error(val messageResId: Int) : ScanDirectorySettingsUiState
    data class Success(
        val directories: List<ScanDirectory>,
        val directoryCount: Int = 0,
        val lastScanTime: String? = null,
        val pendingDirectory: PendingDirectory? = null,
        val errorMessageResId: Int? = null,
        val refreshingDirectoryIds: Set<Long> = emptySet()
    ) : ScanDirectorySettingsUiState
}

sealed interface ScanDirectorySettingsAction {
    data class OnRemoveDirectory(val id: Long) : ScanDirectorySettingsAction
    data object OnBack : ScanDirectorySettingsAction
    data class OnScanDirectoryPreview(val path: String, val name: String) : ScanDirectorySettingsAction
    data object OnConfirmAddDirectory : ScanDirectorySettingsAction
    data object OnCancelDirectoryPreview : ScanDirectorySettingsAction
    data class OnRefreshDirectory(val directoryId: Long) : ScanDirectorySettingsAction
}

@HiltViewModel
class ScanDirectorySettingsViewModel @Inject constructor(
    private val scanDirectoryRepository: ScanDirectoryRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanDirectorySettingsUiState>(ScanDirectorySettingsUiState.Loading)
    val uiState: StateFlow<ScanDirectorySettingsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        observeDirectories()
    }

    private fun observeDirectories() {
        loadJob?.cancel()
        _uiState.value = ScanDirectorySettingsUiState.Loading
        loadJob = scanDirectoryRepository.getAll()
            .onEach { directories ->
                updateSuccess { currentState ->
                    currentState.copy(directories = directories)
                }
                val currentState = _uiState.value
                if (currentState !is ScanDirectorySettingsUiState.Success) {
                    _uiState.value = ScanDirectorySettingsUiState.Success(
                        directories = directories,
                        directoryCount = directories.size
                    )
                }
            }
            .catch { e ->
                Timber.e(e, "Failed to load scan directories")
                if (_uiState.value !is ScanDirectorySettingsUiState.Success) {
                    _uiState.value = ScanDirectorySettingsUiState.Error(R.string.error_load_failed)
                }
            }
            .launchIn(viewModelScope)
    }

    fun retry() = observeDirectories()

    fun onAction(action: ScanDirectorySettingsAction) {
        when (action) {
            is ScanDirectorySettingsAction.OnRemoveDirectory -> {
                viewModelScope.launch {
                    scanDirectoryRepository.remove(action.id)
                }
            }

            is ScanDirectorySettingsAction.OnBack -> {
                // Navigation is handled in the UI layer
            }

            is ScanDirectorySettingsAction.OnScanDirectoryPreview -> {
                updateSuccess {
                    it.copy(pendingDirectory = PendingDirectory(action.path, action.name), errorMessageResId = null)
                }
            }

            is ScanDirectorySettingsAction.OnConfirmAddDirectory -> {
                val currentState = _uiState.value
                if (currentState is ScanDirectorySettingsUiState.Success) {
                    currentState.pendingDirectory?.let { pending ->
                        addDirectory(pending.path, pending.name)
                    } ?: run {
                        updateSuccess { it.copy(errorMessageResId = R.string.settings_error_no_directory) }
                    }
                }
            }

            is ScanDirectorySettingsAction.OnCancelDirectoryPreview -> {
                updateSuccess {
                    it.copy(pendingDirectory = null, errorMessageResId = null)
                }
            }

            is ScanDirectorySettingsAction.OnRefreshDirectory -> {
                refreshDirectory(action.directoryId)
            }
        }
    }

    private fun addDirectory(path: String, name: String) {
        viewModelScope.launch {
            val existingPaths = (_uiState.value as? ScanDirectorySettingsUiState.Success)
                ?.directories?.map { it.path } ?: emptyList()

            when (val validation = ScanDirectoryValidator.validate(path, existingPaths)) {
                is ScanDirectoryValidator.ValidationResult.Failure -> {
                    val errorResId = when (validation.error) {
                        is ScanDirectoryValidator.Error.InvalidPath -> R.string.settings_error_invalid_path
                        is ScanDirectoryValidator.Error.AlreadyExists -> R.string.settings_error_add_failed
                    }
                    updateSuccess {
                        it.copy(errorMessageResId = errorResId, pendingDirectory = null)
                    }
                    return@launch
                }
                is ScanDirectoryValidator.ValidationResult.Success -> {
                    // proceed
                }
            }

            val directory = ScanDirectory(
                id = 0,
                path = path,
                name = name,
                addedAt = System.currentTimeMillis()
            )

            scanDirectoryRepository.add(directory)
                .onSuccess {
                    musicRepository.refreshSongs()
                    updateSuccess {
                        it.copy(pendingDirectory = null, errorMessageResId = null)
                    }
                }
                .onFailure { e ->
                    Timber.w(e, "Failed to add scan directory: $path")
                    updateSuccess {
                        it.copy(errorMessageResId = R.string.settings_error_add_failed, pendingDirectory = null)
                    }
                }
        }
    }

    private fun refreshDirectory(directoryId: Long) {
        val directory = (_uiState.value as? ScanDirectorySettingsUiState.Success)
            ?.directories?.find { it.id == directoryId } ?: return

        val currentState = _uiState.value as? ScanDirectorySettingsUiState.Success
        if (currentState != null && directoryId in currentState.refreshingDirectoryIds) {
            Timber.w("Directory $directoryId is already refreshing, skipping")
            return
        }

        viewModelScope.launch {
            updateSuccess { it.copy(refreshingDirectoryIds = it.refreshingDirectoryIds + directoryId) }

            val result = musicRepository.refreshDirectory(directory.path)

            updateSuccess { it.copy(refreshingDirectoryIds = it.refreshingDirectoryIds - directoryId) }

            if (result.isFailure) {
                Timber.e(result.exceptionOrNull(), "Failed to refresh directory: ${directory.path}")
            }
        }
    }

    private inline fun updateSuccess(
        transform: (ScanDirectorySettingsUiState.Success) -> ScanDirectorySettingsUiState.Success
    ) {
        _uiState.update { current ->
            if (current is ScanDirectorySettingsUiState.Success) transform(current) else current
        }
    }
}
