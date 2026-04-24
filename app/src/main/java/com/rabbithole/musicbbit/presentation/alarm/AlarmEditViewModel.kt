package com.rabbithole.musicbbit.presentation.alarm

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.usecase.GetAlarmByIdUseCase
import com.rabbithole.musicbbit.domain.usecase.GetPlaylistsUseCase
import com.rabbithole.musicbbit.domain.usecase.SaveAlarmUseCase
import com.rabbithole.musicbbit.service.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek

/**
 * UI state for the alarm edit screen.
 */
data class AlarmEditUiState(
    val hour: Int = 7,
    val minute: Int = 30,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val playlistId: Long = 0,
    val label: String = "",
    val autoStopMinutes: Int? = null,
    val isEnabled: Boolean = true,
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false,
    val isNewAlarm: Boolean = true,
    val errorMessage: String? = null,
    val showPermissionDialog: Boolean = false
)

/**
 * Actions that can be triggered from the alarm edit UI.
 */
sealed interface AlarmEditAction {
    data class OnTimeChanged(val hour: Int, val minute: Int) : AlarmEditAction
    data class OnRepeatDaysChanged(val days: Set<DayOfWeek>) : AlarmEditAction
    data class OnPlaylistSelected(val playlistId: Long) : AlarmEditAction
    data class OnLabelChanged(val label: String) : AlarmEditAction
    data class OnAutoStopChanged(val minutes: Int?) : AlarmEditAction
    data object OnSave : AlarmEditAction
    data object OnPermissionDialogDismissed : AlarmEditAction
}

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAlarmByIdUseCase: GetAlarmByIdUseCase,
    private val saveAlarmUseCase: SaveAlarmUseCase,
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val alarmId: Long = savedStateHandle.get<Long>("alarmId") ?: 0L

    private val _uiState = MutableStateFlow(
        AlarmEditUiState(
            isLoading = alarmId != 0L,
            isNewAlarm = alarmId == 0L
        )
    )
    val uiState: StateFlow<AlarmEditUiState> = _uiState.asStateFlow()

    init {
        Timber.i("AlarmEditViewModel initialized, alarmId=%d", alarmId)
        observePlaylists()
        if (alarmId != 0L) {
            loadAlarm()
        }
    }

    /**
     * Collect playlists from the use case to populate the selector.
     */
    private fun observePlaylists() {
        getPlaylistsUseCase()
            .onEach { playlists ->
                Timber.d("Loaded %d playlists", playlists.size)
                _uiState.update { it.copy(playlists = playlists) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Load existing alarm data when editing.
     */
    private fun loadAlarm() {
        viewModelScope.launch {
            Timber.i("Loading alarm with id=%d", alarmId)
            val alarm = getAlarmByIdUseCase(alarmId)
            if (alarm != null) {
                Timber.i("Alarm loaded: hour=%d, minute=%d", alarm.hour, alarm.minute)
                _uiState.update {
                    it.copy(
                        hour = alarm.hour,
                        minute = alarm.minute,
                        repeatDays = alarm.repeatDays,
                        playlistId = alarm.playlistId,
                        label = alarm.label ?: "",
                        autoStopMinutes = alarm.autoStopMinutes,
                        isEnabled = alarm.isEnabled,
                        isLoading = false,
                        isNewAlarm = false
                    )
                }
            } else {
                Timber.w("Alarm with id=%d not found", alarmId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isNewAlarm = false,
                        errorMessage = "Alarm not found"
                    )
                }
            }
        }
    }

    /**
     * Handle user actions from the UI.
     */
    fun onAction(action: AlarmEditAction) {
        when (action) {
            is AlarmEditAction.OnTimeChanged -> {
                Timber.d("Time changed: %02d:%02d", action.hour, action.minute)
                _uiState.update {
                    it.copy(
                        hour = action.hour,
                        minute = action.minute,
                        errorMessage = null
                    )
                }
            }

            is AlarmEditAction.OnRepeatDaysChanged -> {
                Timber.d("Repeat days changed: %s", action.days)
                _uiState.update { it.copy(repeatDays = action.days, errorMessage = null) }
            }

            is AlarmEditAction.OnPlaylistSelected -> {
                Timber.d("Playlist selected: id=%d", action.playlistId)
                _uiState.update { it.copy(playlistId = action.playlistId, errorMessage = null) }
            }

            is AlarmEditAction.OnLabelChanged -> {
                _uiState.update { it.copy(label = action.label, errorMessage = null) }
            }

            is AlarmEditAction.OnAutoStopChanged -> {
                Timber.d("Auto-stop changed: %s minutes", action.minutes?.toString() ?: "null")
                _uiState.update { it.copy(autoStopMinutes = action.minutes, errorMessage = null) }
            }

            is AlarmEditAction.OnSave -> saveAlarm()

            is AlarmEditAction.OnPermissionDialogDismissed -> {
                _uiState.update { it.copy(showPermissionDialog = false) }
            }
        }
    }

    /**
     * Validate and save the alarm.
     */
    private fun saveAlarm() {
        val currentState = _uiState.value

        // Validate: playlist must be selected
        if (currentState.playlistId <= 0) {
            Timber.w("Save failed: no playlist selected")
            _uiState.update { it.copy(errorMessage = "Please select a playlist") }
            return
        }

        // Check exact alarm permission (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmScheduler.canScheduleExactAlarms()) {
            Timber.w("Save failed: exact alarm permission not granted")
            _uiState.update { it.copy(showPermissionDialog = true) }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        val alarm = Alarm(
            id = alarmId,
            hour = currentState.hour,
            minute = currentState.minute,
            repeatDays = currentState.repeatDays,
            playlistId = currentState.playlistId,
            isEnabled = currentState.isEnabled,
            label = currentState.label.takeIf { it.isNotBlank() },
            autoStopMinutes = currentState.autoStopMinutes,
            lastTriggeredAt = null
        )

        viewModelScope.launch {
            Timber.i("Saving alarm: id=%d, hour=%d, minute=%d, playlistId=%d", alarm.id, alarm.hour, alarm.minute, alarm.playlistId)
            val result = saveAlarmUseCase(alarm)
            result.fold(
                onSuccess = { savedId ->
                    Timber.i("Alarm saved successfully, id=%d", savedId)
                    _uiState.update { it.copy(isSaving = false, saveCompleted = true) }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to save alarm")
                    _uiState.update { it.copy(isSaving = false, errorMessage = "Failed to save alarm") }
                }
            )
        }
    }
}
