package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.navigation.AlarmEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * UI state for the alarm edit screen.
 */
data class AlarmFormState(
    val hour: Int = 7,
    val minute: Int = 30,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val excludeHolidays: Boolean = false,
    val playlistId: Long = 0,
    val label: String = "",
    val autoStop: AutoStop? = null,
    val isEnabled: Boolean = true,
)

data class AlarmEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false,
    val isNewAlarm: Boolean = true,
    val errorMessageResId: Int? = null,
    val form: AlarmFormState = AlarmFormState(),
    val playlists: List<Playlist> = emptyList(),
    val volumeRampDurationSeconds: Int = 0,
)

/**
 * One-time events emitted by [AlarmEditViewModel] for ephemeral UI actions
 * (dialogs, navigation, toasts) that should not survive configuration change.
 */
sealed interface AlarmEditEvent {
    data object ShowPermissionDialog : AlarmEditEvent
    data object ShowFullScreenIntentDialog : AlarmEditEvent
    data class ShowAutostartGuideDialog(val intent: Intent?) : AlarmEditEvent
    data object ShowAutostartManualGuideDialog : AlarmEditEvent
}

/**
 * Actions that can be triggered from the alarm edit UI.
 */
sealed interface AlarmEditAction {
    data class OnTimeChanged(val hour: Int, val minute: Int) : AlarmEditAction
    data class OnRepeatDaysChanged(val days: Set<DayOfWeek>) : AlarmEditAction
    data class OnExcludeHolidaysChanged(val exclude: Boolean) : AlarmEditAction
    data class OnPlaylistSelected(val playlistId: Long) : AlarmEditAction
    data class OnLabelChanged(val label: String) : AlarmEditAction
    data class OnAutoStopChanged(val autoStop: AutoStop?) : AlarmEditAction
    data object OnSave : AlarmEditAction
}

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val alarmRepository: AlarmRepository,
    private val playlistRepository: PlaylistRepository,
    private val alarmRingSettingsRepository: AlarmRingSettingsRepository,
    private val permissionOrchestrator: AlarmEditPermissionOrchestrator,
) : ViewModel() {

    private val alarmSaveOrchestrator = AlarmSaveOrchestrator(alarmRepository, permissionOrchestrator)

    private val alarmId: Long = savedStateHandle.toRoute<AlarmEdit>().alarmId

    private val _uiState = MutableStateFlow(
        AlarmEditUiState(
            isLoading = alarmId != 0L,
            isNewAlarm = alarmId == 0L
        )
    )
    val uiState: StateFlow<AlarmEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<AlarmEditEvent>()
    val events: Flow<AlarmEditEvent> = _events.receiveAsFlow()

    init {
        Timber.i("AlarmEditViewModel initialized, alarmId=%d", alarmId)
        observePlaylists()
        observeVolumeRampDuration()
        if (alarmId != 0L) {
            loadAlarm()
        }
    }

    private fun observePlaylists() {
        playlistRepository.getAllPlaylists()
            .onEach { playlists ->
                Timber.d("Loaded %d playlists", playlists.size)
                _uiState.update { it.copy(playlists = playlists) }
            }
            .catch { e ->
                Timber.e(e, "Failed to load playlists")
                _uiState.update { it.copy(isLoading = false, errorMessageResId = R.string.error_load_failed) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeVolumeRampDuration() {
        alarmRingSettingsRepository.getVolumeRampDurationSeconds()
            .onEach { seconds ->
                Timber.d("Volume ramp duration: %ds", seconds)
                _uiState.update { it.copy(volumeRampDurationSeconds = seconds) }
            }
            .catch { e ->
                Timber.e(e, "Failed to load volume ramp duration")
                _uiState.update { it.copy(volumeRampDurationSeconds = 0) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadAlarm() {
        viewModelScope.launch {
            try {
                Timber.i("Loading alarm with id=%d", alarmId)
                val alarm = alarmRepository.getAlarmById(alarmId)
                if (alarm != null) {
                    Timber.i("Alarm loaded: hour=%d, minute=%d", alarm.hour, alarm.minute)
                    _uiState.update {
                        it.copy(
                            form = AlarmFormState(
                                hour = alarm.hour,
                                minute = alarm.minute,
                                repeatDays = alarm.repeatDays,
                                excludeHolidays = alarm.excludeHolidays,
                                playlistId = alarm.playlistId,
                                label = alarm.label ?: "",
                                autoStop = alarm.autoStop,
                                isEnabled = alarm.isEnabled,
                            ),
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
                            errorMessageResId = R.string.alarm_edit_error_not_found
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load alarm with id=%d", alarmId)
                _uiState.update {
                    it.copy(isLoading = false, errorMessageResId = R.string.error_load_failed)
                }
            }
        }
    }

    fun onAction(action: AlarmEditAction) {
        when (action) {
            is AlarmEditAction.OnTimeChanged -> {
                Timber.d("Time changed: %02d:%02d", action.hour, action.minute)
                _uiState.update {
                    it.copy(
                        form = it.form.copy(hour = action.hour, minute = action.minute),
                        errorMessageResId = null
                    )
                }
            }
            is AlarmEditAction.OnRepeatDaysChanged -> {
                Timber.d("Repeat days changed: %s", action.days)
                _uiState.update {
                    it.copy(form = it.form.copy(repeatDays = action.days), errorMessageResId = null)
                }
            }
            is AlarmEditAction.OnExcludeHolidaysChanged -> {
                Timber.d("Exclude holidays changed: %s", action.exclude)
                _uiState.update {
                    it.copy(form = it.form.copy(excludeHolidays = action.exclude), errorMessageResId = null)
                }
            }
            is AlarmEditAction.OnPlaylistSelected -> {
                Timber.d("Playlist selected: id=%d", action.playlistId)
                _uiState.update {
                    it.copy(form = it.form.copy(playlistId = action.playlistId), errorMessageResId = null)
                }
            }
            is AlarmEditAction.OnLabelChanged -> {
                _uiState.update {
                    it.copy(form = it.form.copy(label = action.label), errorMessageResId = null)
                }
            }
            is AlarmEditAction.OnAutoStopChanged -> {
                Timber.d("Auto-stop changed: %s", action.autoStop?.toString() ?: "null")
                _uiState.update {
                    it.copy(form = it.form.copy(autoStop = action.autoStop), errorMessageResId = null)
                }
            }
            is AlarmEditAction.OnSave -> saveAlarm()
        }
    }

    fun onAutostartGuideDismissed() {
        _uiState.update { it.copy(saveCompleted = true) }
    }

    private fun saveAlarm() {
        val form = _uiState.value.form

        val alarm = Alarm(
            id = alarmId,
            hour = form.hour,
            minute = form.minute,
            repeatDays = form.repeatDays,
            excludeHolidays = form.excludeHolidays,
            playlistId = form.playlistId,
            isEnabled = form.isEnabled,
            label = form.label.takeIf { it.isNotBlank() },
            autoStop = form.autoStop,
            lastTriggeredAt = null
        )

        _uiState.update { it.copy(isSaving = true, errorMessageResId = null) }

        viewModelScope.launch {
            when (val outcome = alarmSaveOrchestrator.save(alarm, form.playlistId)) {
                is AlarmSaveOrchestrator.SaveOutcome.MissingPlaylist -> {
                    _uiState.update { it.copy(isSaving = false, errorMessageResId = R.string.alarm_edit_error_select_playlist) }
                }
                is AlarmSaveOrchestrator.SaveOutcome.NeedsExactAlarmPermission -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(AlarmEditEvent.ShowPermissionDialog)
                }
                is AlarmSaveOrchestrator.SaveOutcome.NeedsFullScreenIntentPermission -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(AlarmEditEvent.ShowFullScreenIntentDialog)
                }
                is AlarmSaveOrchestrator.SaveOutcome.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    when (val autostart = outcome.autostart) {
                        is AlarmSaveOrchestrator.AutostartOutcome.Resolved -> {
                            _events.send(AlarmEditEvent.ShowAutostartGuideDialog(autostart.intent))
                        }
                        is AlarmSaveOrchestrator.AutostartOutcome.NeedsManualGuide -> {
                            _events.send(AlarmEditEvent.ShowAutostartManualGuideDialog)
                        }
                        is AlarmSaveOrchestrator.AutostartOutcome.NotApplicable -> {
                            _uiState.update { it.copy(saveCompleted = true) }
                        }
                    }
                }
                is AlarmSaveOrchestrator.SaveOutcome.Failure -> {
                    _uiState.update { it.copy(isSaving = false, errorMessageResId = outcome.errorResId) }
                }
            }
        }
    }
}
