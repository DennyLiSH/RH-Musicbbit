package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for the alarm list screen.
 */
sealed interface AlarmListUiState {
    data object Loading : AlarmListUiState
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
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(checkBatteryOptimizationStatus())
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    private val _isFullScreenIntentGranted = MutableStateFlow(checkFullScreenIntentStatus())
    val isFullScreenIntentGranted: StateFlow<Boolean> = _isFullScreenIntentGranted.asStateFlow()

    private val _uiState = MutableStateFlow<AlarmListUiState>(AlarmListUiState.Loading)
    val uiState: StateFlow<AlarmListUiState> = _uiState.asStateFlow()

    /**
     * In-memory cache of playlist names to avoid repeated repository lookups.
     */
    private val playlistNameCache = mutableMapOf<Long, String>()

    init {
        alarmRepository.getAllAlarms()
            .onEach { alarms ->
                val alarmItems = alarms.map { alarm ->
                    val playlistName = resolvePlaylistName(alarm.playlistId)
                    AlarmItem(alarm = alarm, playlistName = playlistName)
                }
                _uiState.value = AlarmListUiState.Success(alarmItems, errorMessageResId = null)
            }
            .launchIn(viewModelScope)

        // Refresh holiday data in the background
        viewModelScope.launch {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            runCatching { holidayRepository.refreshHolidays(currentYear) }
                .onSuccess {
                    Timber.i("Holiday data refreshed for year $currentYear")
                }
                .onFailure { error ->
                    Timber.w(error, "Failed to refresh holiday data for year $currentYear")
                }
        }
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
     * Check whether the app is ignoring battery optimizations.
     */
    private fun checkBatteryOptimizationStatus(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Refresh the battery optimization status.
     */
    fun refreshBatteryOptimizationStatus() {
        _isIgnoringBatteryOptimizations.value = checkBatteryOptimizationStatus()
    }

    /**
     * Check whether the app can use full-screen intents.
     */
    private fun checkFullScreenIntentStatus(): Boolean {
        return FullScreenIntentPermissionHelper.isGranted(context)
    }

    /**
     * Refresh the full-screen intent permission status.
     */
    fun refreshFullScreenIntentStatus() {
        _isFullScreenIntentGranted.value = checkFullScreenIntentStatus()
    }

    /**
     * Create an intent to request the user to ignore battery optimizations for this app.
     */
    fun createBatteryOptimizationIntent(): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
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
