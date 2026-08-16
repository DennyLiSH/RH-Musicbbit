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
import com.rabbithole.musicbbit.domain.model.AlarmRingMode
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.navigation.AlarmEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    val resumePlayback: Boolean = true,
    val ringMode: AlarmRingMode = AlarmRingMode.Normal,
    val ignoreQuietMode: Boolean = true,
)

data class AlarmEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false,
    val saveFailedMessageResId: Int? = null,
    val isNewAlarm: Boolean = true,
    val errorMessageResId: Int? = null,
    val form: AlarmFormState = AlarmFormState(),
    val playlists: List<Playlist> = emptyList(),
    val volumeRampDurationSeconds: Int = 0,
    val dialogState: AlarmEditDialogState? = null,
    val hasUnsavedChanges: Boolean = false,
)

/**
 * Single-value representation of which (mutually exclusive) dialog the screen should render.
 *
 * Replaces the previous scheme of N independent `Boolean` flags in the screen plus a one-shot
 * event flow from the ViewModel. LWW semantics: a new [showXxx] call overwrites whatever dialog
 * was active — no priority queue, no stacking.
 */
sealed interface AlarmEditDialogState {
    data object TimePicker : AlarmEditDialogState
    data object Permission : AlarmEditDialogState
    data object FullScreenIntent : AlarmEditDialogState
    data object DndAccess : AlarmEditDialogState
    data class AutostartGuide(val intent: Intent?) : AlarmEditDialogState
    data object AutostartManualGuide : AlarmEditDialogState
    data object Discard : AlarmEditDialogState
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
    data class OnResumePlaybackChanged(val resume: Boolean) : AlarmEditAction
    data class OnRingModeChanged(val ringMode: AlarmRingMode) : AlarmEditAction
    data class OnIgnoreQuietModeChanged(val ignore: Boolean) : AlarmEditAction
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
                                resumePlayback = alarm.resumePlayback,
                                ringMode = alarm.ringMode,
                                ignoreQuietMode = alarm.ignoreQuietMode,
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
                        errorMessageResId = null,
                        hasUnsavedChanges = true,
                    )
                }
            }
            is AlarmEditAction.OnRepeatDaysChanged -> {
                Timber.d("Repeat days changed: %s", action.days)
                _uiState.update {
                    it.copy(form = it.form.copy(repeatDays = action.days), errorMessageResId = null, hasUnsavedChanges = true)
                }
            }
            is AlarmEditAction.OnExcludeHolidaysChanged -> {
                Timber.d("Exclude holidays changed: %s", action.exclude)
                _uiState.update {
                    it.copy(form = it.form.copy(excludeHolidays = action.exclude), errorMessageResId = null, hasUnsavedChanges = true)
                }
            }
            is AlarmEditAction.OnPlaylistSelected -> {
                Timber.d("Playlist selected: id=%d", action.playlistId)
                _uiState.update {
                    it.copy(form = it.form.copy(playlistId = action.playlistId), errorMessageResId = null, hasUnsavedChanges = true)
                }
            }
            is AlarmEditAction.OnLabelChanged -> {
                _uiState.update {
                    it.copy(form = it.form.copy(label = action.label), errorMessageResId = null, hasUnsavedChanges = true)
                }
            }
            is AlarmEditAction.OnAutoStopChanged -> {
                Timber.d("Auto-stop changed: %s", action.autoStop?.toString() ?: "null")
                _uiState.update {
                    it.copy(form = it.form.copy(autoStop = action.autoStop), errorMessageResId = null, hasUnsavedChanges = true)
                }
            }
            is AlarmEditAction.OnResumePlaybackChanged -> {
                Timber.d("Resume playback changed: %s", action.resume)
                _uiState.update {
                    it.copy(form = it.form.copy(resumePlayback = action.resume), errorMessageResId = null, hasUnsavedChanges = true)
                }
            }
            is AlarmEditAction.OnRingModeChanged -> {
                Timber.d("Ring mode changed: %s", action.ringMode)
                _uiState.update {
                    it.copy(form = it.form.copy(ringMode = action.ringMode), errorMessageResId = null, hasUnsavedChanges = true)
                }
            }
            is AlarmEditAction.OnIgnoreQuietModeChanged -> {
                Timber.d("Ignore quiet mode changed: %s", action.ignore)
                _uiState.update {
                    it.copy(form = it.form.copy(ignoreQuietMode = action.ignore), errorMessageResId = null, hasUnsavedChanges = true)
                }
            }
            is AlarmEditAction.OnSave -> saveAlarm()
        }
    }

    fun onAutostartGuideDismissed() {
        _uiState.update { it.copy(dialogState = null, saveCompleted = true) }
    }

    /**
     * Clears the inline form error message *and* the save-failed snackbar trigger.
     * Called by UI when user edits any form field after a validation error,
     * or after the save-failed snackbar finishes displaying.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessageResId = null, saveFailedMessageResId = null) }
    }

    /** UI-driven dialog show requests (user-initiated, not from saveAlarm flow). */
    fun showTimePicker() {
        _uiState.update { it.copy(dialogState = AlarmEditDialogState.TimePicker) }
    }

    fun showDiscardDialog() {
        _uiState.update { it.copy(dialogState = AlarmEditDialogState.Discard) }
    }

    /** Dismiss whatever dialog is currently shown. LWW: any new showXxx overwrites prior state. */
    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = null) }
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
            lastTriggeredAt = null,
            resumePlayback = form.resumePlayback,
            ringMode = form.ringMode,
            ignoreQuietMode = form.ignoreQuietMode,
        )

        _uiState.update { it.copy(isSaving = true, errorMessageResId = null) }

        viewModelScope.launch {
            when (val outcome = alarmSaveOrchestrator.save(alarm, form.playlistId)) {
                is AlarmSaveOrchestrator.SaveOutcome.MissingPlaylist -> {
                    _uiState.update { it.copy(isSaving = false, errorMessageResId = R.string.alarm_edit_error_select_playlist) }
                }
                is AlarmSaveOrchestrator.SaveOutcome.NeedsExactAlarmPermission -> {
                    _uiState.update { it.copy(isSaving = false, dialogState = AlarmEditDialogState.Permission) }
                }
                is AlarmSaveOrchestrator.SaveOutcome.NeedsFullScreenIntentPermission -> {
                    _uiState.update { it.copy(isSaving = false, dialogState = AlarmEditDialogState.FullScreenIntent) }
                }
                is AlarmSaveOrchestrator.SaveOutcome.NeedsDndAccessPermission -> {
                    _uiState.update { it.copy(isSaving = false, dialogState = AlarmEditDialogState.DndAccess) }
                }
                is AlarmSaveOrchestrator.SaveOutcome.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    when (val autostart = outcome.autostart) {
                        is AlarmSaveOrchestrator.AutostartOutcome.Resolved -> {
                            _uiState.update { it.copy(dialogState = AlarmEditDialogState.AutostartGuide(autostart.intent)) }
                        }
                        is AlarmSaveOrchestrator.AutostartOutcome.NeedsManualGuide -> {
                            _uiState.update { it.copy(dialogState = AlarmEditDialogState.AutostartManualGuide) }
                        }
                        is AlarmSaveOrchestrator.AutostartOutcome.NotApplicable -> {
                            _uiState.update { it.copy(saveCompleted = true) }
                        }
                    }
                }
                is AlarmSaveOrchestrator.SaveOutcome.Failure -> {
                    _uiState.update {
                        it.copy(isSaving = false, saveFailedMessageResId = R.string.alarm_save_failed)
                    }
                }
            }
        }
    }
}
