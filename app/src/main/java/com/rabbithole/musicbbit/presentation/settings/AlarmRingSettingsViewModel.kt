package com.rabbithole.musicbbit.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AlarmRingSettingsViewModel @Inject constructor(
    private val alarmRingSettingsRepository: AlarmRingSettingsRepository
) : ViewModel() {

    data class AlarmRingSettingsUiState(
        val volumeRampDurationSeconds: Int = 5
    )

    private val _uiState = MutableStateFlow(AlarmRingSettingsUiState())
    val uiState: StateFlow<AlarmRingSettingsUiState> = _uiState.asStateFlow()

    init {
        observeVolumeRampDuration()
    }

    private fun observeVolumeRampDuration() {
        alarmRingSettingsRepository.getVolumeRampDurationSeconds()
            .onEach { seconds ->
                _uiState.update { it.copy(volumeRampDurationSeconds = seconds) }
            }
            .catch { e ->
                Timber.e(e, "Failed to load volume ramp duration")
            }
            .launchIn(viewModelScope)
    }

    fun setVolumeRampDuration(seconds: Int) {
        viewModelScope.launch {
            alarmRingSettingsRepository.setVolumeRampDurationSeconds(seconds)
                .onFailure { e ->
                    Timber.w(e, "Failed to set volume ramp duration")
                }
        }
    }
}
