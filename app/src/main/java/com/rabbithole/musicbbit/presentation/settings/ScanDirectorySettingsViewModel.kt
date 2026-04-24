package com.rabbithole.musicbbit.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.usecase.AddScanDirectoryUseCase
import com.rabbithole.musicbbit.domain.usecase.GetScanDirectoriesUseCase
import com.rabbithole.musicbbit.domain.usecase.RemoveScanDirectoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PendingDirectory(
    val path: String,
    val name: String
)

sealed interface ScanDirectorySettingsUiState {
    data object Loading : ScanDirectorySettingsUiState
    data class Success(
        val directories: List<ScanDirectory>,
        val directoryCount: Int = 0,
        val lastScanTime: String? = null,
        val pendingDirectory: PendingDirectory? = null,
        val addError: String? = null
    ) : ScanDirectorySettingsUiState
}

sealed interface ScanDirectorySettingsAction {
    data object OnAddDirectory : ScanDirectorySettingsAction
    data class OnRemoveDirectory(val id: Long) : ScanDirectorySettingsAction
    data object OnBack : ScanDirectorySettingsAction
    data class OnScanDirectoryPreview(val path: String, val name: String) : ScanDirectorySettingsAction
    data object OnConfirmAddDirectory : ScanDirectorySettingsAction
    data object OnCancelDirectoryPreview : ScanDirectorySettingsAction
}

@HiltViewModel
class ScanDirectorySettingsViewModel @Inject constructor(
    private val getScanDirectoriesUseCase: GetScanDirectoriesUseCase,
    private val addScanDirectoryUseCase: AddScanDirectoryUseCase,
    private val removeScanDirectoryUseCase: RemoveScanDirectoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanDirectorySettingsUiState>(ScanDirectorySettingsUiState.Loading)
    val uiState: StateFlow<ScanDirectorySettingsUiState> = _uiState.asStateFlow()

    init {
        observeDirectories()
    }

    private fun observeDirectories() {
        getScanDirectoriesUseCase()
            .onEach { directories ->
                _uiState.update { currentState ->
                    if (currentState is ScanDirectorySettingsUiState.Success) {
                        currentState.copy(directories = directories)
                    } else {
                        ScanDirectorySettingsUiState.Success(
                            directories = directories,
                            directoryCount = directories.size
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ScanDirectorySettingsAction) {
        when (action) {
            is ScanDirectorySettingsAction.OnAddDirectory -> {
                // No-op: UI layer launches the folder picker directly
            }

            is ScanDirectorySettingsAction.OnRemoveDirectory -> {
                viewModelScope.launch {
                    removeScanDirectoryUseCase(action.id)
                }
            }

            is ScanDirectorySettingsAction.OnBack -> {
                // Navigation is handled in the UI layer
            }

            is ScanDirectorySettingsAction.OnScanDirectoryPreview -> {
                _uiState.update {
                    if (it is ScanDirectorySettingsUiState.Success) {
                        it.copy(pendingDirectory = PendingDirectory(action.path, action.name), addError = null)
                    } else {
                        it
                    }
                }
            }

            is ScanDirectorySettingsAction.OnConfirmAddDirectory -> {
                val currentState = _uiState.value
                if (currentState is ScanDirectorySettingsUiState.Success) {
                    currentState.pendingDirectory?.let { pending ->
                        addDirectory(pending.path, pending.name)
                    }
                }
            }

            is ScanDirectorySettingsAction.OnCancelDirectoryPreview -> {
                _uiState.update {
                    if (it is ScanDirectorySettingsUiState.Success) {
                        it.copy(pendingDirectory = null, addError = null)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun addDirectory(path: String, name: String) {
        viewModelScope.launch {
            val file = File(path)
            if (!file.exists() || !file.isDirectory) {
                _uiState.update {
                    if (it is ScanDirectorySettingsUiState.Success) {
                        it.copy(addError = "Invalid directory path", pendingDirectory = null)
                    } else {
                        it
                    }
                }
                return@launch
            }

            val directory = ScanDirectory(
                id = 0,
                path = path,
                name = name,
                addedAt = System.currentTimeMillis()
            )

            val result = addScanDirectoryUseCase(directory)
            if (result.isSuccess) {
                _uiState.update {
                    if (it is ScanDirectorySettingsUiState.Success) {
                        it.copy(pendingDirectory = null, addError = null)
                    } else {
                        it
                    }
                }
            } else {
                _uiState.update {
                    if (it is ScanDirectorySettingsUiState.Success) {
                        it.copy(addError = "Failed to add directory", pendingDirectory = null)
                    } else {
                        it
                    }
                }
            }
        }
    }
}
