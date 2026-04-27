package com.rabbithole.musicbbit.presentation.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.ScanDirectory
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.domain.repository.ScanDirectoryRepository
import com.rabbithole.musicbbit.domain.usecase.AddScanDirectoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@Immutable
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
        val errorMessageResId: Int? = null,
        val breathingEnabled: Boolean = true,
        val breathingPeriodMs: Long = 3500L
    ) : ScanDirectorySettingsUiState
}

sealed interface ScanDirectorySettingsAction {
    data class OnRemoveDirectory(val id: Long) : ScanDirectorySettingsAction
    data object OnBack : ScanDirectorySettingsAction
    data class OnScanDirectoryPreview(val path: String, val name: String) : ScanDirectorySettingsAction
    data object OnConfirmAddDirectory : ScanDirectorySettingsAction
    data object OnCancelDirectoryPreview : ScanDirectorySettingsAction
    data class OnBreathingEnabledChanged(val enabled: Boolean) : ScanDirectorySettingsAction
    data class OnBreathingPeriodChanged(val periodMs: Long) : ScanDirectorySettingsAction
}

@HiltViewModel
class ScanDirectorySettingsViewModel @Inject constructor(
    private val scanDirectoryRepository: ScanDirectoryRepository,
    private val addScanDirectoryUseCase: AddScanDirectoryUseCase,
    private val alarmRingSettingsRepository: AlarmRingSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanDirectorySettingsUiState>(ScanDirectorySettingsUiState.Loading)
    val uiState: StateFlow<ScanDirectorySettingsUiState> = _uiState.asStateFlow()

    init {
        observeDirectories()
        observeBreathingSettings()
    }

    private fun observeBreathingSettings() {
        combine(
            alarmRingSettingsRepository.isBreathingEnabled(),
            alarmRingSettingsRepository.getBreathingPeriodMs()
        ) { enabled, periodMs ->
            updateSuccess { currentState ->
                currentState.copy(breathingEnabled = enabled, breathingPeriodMs = periodMs)
            }
        }
            .catch { e ->
                Timber.e(e, "Breathing settings flow failed")
                updateSuccess { it.copy(errorMessageResId = R.string.error_load_failed) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeDirectories() {
        scanDirectoryRepository.getAll()
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
                updateSuccess { it.copy(errorMessageResId = R.string.error_load_failed) }
            }
            .launchIn(viewModelScope)
    }

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

            is ScanDirectorySettingsAction.OnBreathingEnabledChanged -> {
                viewModelScope.launch {
                    alarmRingSettingsRepository.setBreathingEnabled(action.enabled)
                        .onFailure { e ->
                            Timber.w(e, "Failed to set breathing enabled")
                            updateSuccess { it.copy(errorMessageResId = R.string.alarm_ring_error_breathing_settings_failed) }
                        }
                }
            }

            is ScanDirectorySettingsAction.OnBreathingPeriodChanged -> {
                viewModelScope.launch {
                    alarmRingSettingsRepository.setBreathingPeriodMs(action.periodMs)
                        .onFailure { e ->
                            Timber.w(e, "Failed to set breathing period")
                            updateSuccess { it.copy(errorMessageResId = R.string.alarm_ring_error_breathing_settings_failed) }
                        }
                }
            }
        }
    }

    private fun addDirectory(path: String, name: String) {
        viewModelScope.launch {
            val file = File(path)
            if (!file.exists() || !file.isDirectory) {
                updateSuccess {
                    it.copy(errorMessageResId = R.string.settings_error_invalid_path, pendingDirectory = null)
                }
                return@launch
            }

            val directory = ScanDirectory(
                id = 0,
                path = path,
                name = name,
                addedAt = System.currentTimeMillis()
            )

            addScanDirectoryUseCase(directory)
                .onSuccess {
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

    private inline fun updateSuccess(
        transform: (ScanDirectorySettingsUiState.Success) -> ScanDirectorySettingsUiState.Success
    ) {
        _uiState.update { current ->
            if (current is ScanDirectorySettingsUiState.Success) transform(current) else current
        }
    }
}
