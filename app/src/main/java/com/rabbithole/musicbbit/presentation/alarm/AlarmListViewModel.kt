package com.rabbithole.musicbbit.presentation.alarm

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.presentation.permissions.PermissionStatus
import com.rabbithole.musicbbit.presentation.permissions.PermissionStatusMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import com.rabbithole.musicbbit.R
import javax.inject.Inject

/**
 * UI state for the alarm list screen.
 */
sealed interface AlarmListUiState {
    data object Loading : AlarmListUiState
    data class Error(val messageResId: Int) : AlarmListUiState
    data class Success(
        val alarms: List<AlarmItem>,
        val errorMessageResId: Int? = null
    ) : AlarmListUiState
}

/**
 * Presentation model that combines an alarm with its associated playlist name.
 */
data class AlarmItem(
    val alarm: Alarm,
    val playlistName: String
)

/**
 * User actions that can be performed on the alarm list screen.
 */
sealed interface AlarmListAction {
    data class OnToggleEnabled(val alarmId: Long, val enabled: Boolean) : AlarmListAction
    data class OnDeleteAlarm(val alarm: Alarm) : AlarmListAction
    data class OnAlarmClick(val alarmId: Long) : AlarmListAction
    data object OnCreateAlarm : AlarmListAction
}

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val holidayRepository: HolidayRepository,
    private val playlistRepository: PlaylistRepository,
    private val permissionMonitor: PermissionStatusMonitor,
) : ViewModel() {

    /** Single observable snapshot of every permission gate shown as a banner. */
    val permissionStatus: StateFlow<PermissionStatus> = permissionMonitor.status

    /** Re-read every permission flag from the system; call on ON_RESUME. */
    fun refreshPermissionStatus() = permissionMonitor.refresh()

    /** Intent that opens the system "ignore battery optimizations" dialog for this app. */
    fun createBatteryOptimizationIntent() = permissionMonitor.createBatteryOptimizationIntent()

    /** Intent that opens the system page granting Do Not Disturb access. */
    fun createDndAccessSettingsIntent() = permissionMonitor.createDndAccessSettingsIntent()

    /** Intent that opens the system page granting USE_FULL_SCREEN_INTENT. */
    fun createFullScreenIntentSettingsIntent() = permissionMonitor.createFullScreenIntentSettingsIntent()

    private val _uiState = MutableStateFlow<AlarmListUiState>(AlarmListUiState.Loading)
    val uiState: StateFlow<AlarmListUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /**
     * In-memory cache of playlist names to avoid repeated repository lookups.
     */
    private val playlistNameCache = mutableMapOf<Long, String>()

    init {
        loadData()

        // Refresh holiday data in the background (throttled to once per month)
        viewModelScope.launch {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            holidayRepository.maybeRefreshHolidays(currentYear).onFailure { error ->
                Timber.w(error, "Holiday refresh failed in AlarmListViewModel init; will retry next month")
            }
        }
    }

    /**
     * Subscribes to the alarms flow and updates UI state accordingly.
     * Cancels any previous subscription before re-subscribing.
     */
    private fun loadData() {
        loadJob?.cancel()
        _uiState.value = AlarmListUiState.Loading
        loadJob = alarmRepository.getAllAlarms()
            .onEach { alarms ->
                val alarmItems = alarms.map { alarm ->
                    val playlistName = resolvePlaylistName(alarm.playlistId)
                    AlarmItem(alarm = alarm, playlistName = playlistName)
                }
                _uiState.value = AlarmListUiState.Success(alarmItems, errorMessageResId = null)
            }
            .catch { e ->
                Timber.e(e, "Failed to load alarms")
                _uiState.value = AlarmListUiState.Error(R.string.error_load_failed)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Retries loading alarms after an error.
     */
    fun retry() = loadData()

    /**
     * Handles user actions from the UI layer.
     */
    fun onAction(action: AlarmListAction) {
        when (action) {
            is AlarmListAction.OnToggleEnabled -> {
                viewModelScope.launch {
                    alarmRepository.enableAlarm(action.alarmId, action.enabled)
                        .onFailure { e ->
                            Timber.w(e, "Failed to update alarm enable state")
                            val current = _uiState.value
                            if (current is AlarmListUiState.Success) {
                                _uiState.update {
                                    current.copy(errorMessageResId = R.string.alarm_error_enable_failed)
                                }
                            }
                        }
                }
            }

            is AlarmListAction.OnDeleteAlarm -> {
                viewModelScope.launch {
                    alarmRepository.deleteAlarm(action.alarm)
                        .onFailure { e ->
                            Timber.w(e, "Failed to delete alarm")
                            val current = _uiState.value
                            if (current is AlarmListUiState.Success) {
                                _uiState.update {
                                    current.copy(errorMessageResId = R.string.alarm_error_delete_failed)
                                }
                            }
                        }
                }
            }

            is AlarmListAction.OnAlarmClick -> {
                // Navigation is handled in the UI layer
            }

            is AlarmListAction.OnCreateAlarm -> {
                // Navigation is handled in the UI layer
            }
        }
    }

    /**
     * Resolves the playlist name for the given playlist ID, using an in-memory cache
     * to minimize repository calls.
     */
    private suspend fun resolvePlaylistName(playlistId: Long): String {
        playlistNameCache[playlistId]?.let { return it }
        val name = playlistRepository.getPlaylistById(playlistId)?.name ?: "Unknown Playlist"
        playlistNameCache[playlistId] = name
        return name
    }
}
