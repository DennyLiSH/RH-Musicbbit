package com.rabbithole.musicbbit.presentation.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AlarmWithPlaylistName
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import com.rabbithole.musicbbit.presentation.components.ListUiState
import com.rabbithole.musicbbit.presentation.components.UserMessage
import com.rabbithole.musicbbit.presentation.permissions.PermissionStatus
import com.rabbithole.musicbbit.presentation.permissions.PermissionStatusMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Presentation model that combines an alarm with its associated playlist name.
 * `playlistName` is nullable: a null name means the linked playlist has been deleted.
 */
data class AlarmItem(
    val alarm: Alarm,
    val playlistName: String?,
)

/**
 * User actions that can be performed on the alarm list screen.
 */
sealed interface AlarmListAction {
    data class OnToggleEnabled(val alarmId: Long, val enabled: Boolean) : AlarmListAction
    data class OnDeleteAlarm(val alarm: Alarm) : AlarmListAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val holidayRepository: HolidayRepository,
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

    private val loadTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ListUiState<List<AlarmItem>>> = loadTrigger
        .flatMapLatest {
            alarmRepository.getAlarmsWithPlaylistName()
                .map<List<AlarmWithPlaylistName>, ListUiState<List<AlarmItem>>> { items ->
                    ListUiState.Content(
                        items.map { item ->
                            AlarmItem(
                                alarm = item.alarm,
                                playlistName = item.playlistName,
                            )
                        }
                    )
                }
                .onStart { emit(ListUiState.Loading) }
                .catch { e ->
                    Timber.e(e, "Failed to load alarms")
                    emit(ListUiState.Error(R.string.error_load_failed))
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListUiState.Loading)

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        // Refresh holiday data in the background (throttled to once per month)
        viewModelScope.launch {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            holidayRepository.maybeRefreshHolidays(currentYear).onFailure { error ->
                Timber.w(error, "Holiday refresh failed in AlarmListViewModel init; will retry next month")
            }
        }
    }

    fun retry() {
        loadTrigger.update { it + 1 }
    }

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
                            val sent = _messages.trySend(UserMessage(R.string.alarm_error_enable_failed))
                            if (!sent.isSuccess) Timber.w("UserMessage dropped: channel full or closed")
                        }
                }
            }

            is AlarmListAction.OnDeleteAlarm -> {
                viewModelScope.launch {
                    alarmRepository.deleteAlarm(action.alarm)
                        .onFailure { e ->
                            Timber.w(e, "Failed to delete alarm")
                            val sent = _messages.trySend(UserMessage(R.string.alarm_error_delete_failed))
                            if (!sent.isSuccess) Timber.w("UserMessage dropped: channel full or closed")
                        }
                }
            }
        }
    }
}