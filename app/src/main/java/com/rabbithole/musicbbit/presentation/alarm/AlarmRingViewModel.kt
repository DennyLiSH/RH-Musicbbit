package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.service.AlarmActionReceiver
import com.rabbithole.musicbbit.service.AlarmScheduler
import com.rabbithole.musicbbit.service.MusicPlaybackService
import com.rabbithole.musicbbit.service.MusicPlayerStateHolder
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state for the full-screen alarm ring screen.
 */
data class AlarmRingUiState(
    val isPlaying: Boolean = false,
    val hasPlayback: Boolean = false,
    val currentSongTitle: String? = null,
    val currentSongArtist: String? = null,
    val alarmLabel: String = "",
    val breathingEnabled: Boolean = true,
    val breathingPeriodMs: Long = 3500L
)

/**
 * ViewModel for the alarm ring activity.
 *
 * Observes playback state from [MusicPlayerStateHolder] and provides
 * actions to pause, resume, stop, or snooze the alarm.
 */
@HiltViewModel
class AlarmRingViewModel @Inject constructor(
    private val stateHolder: MusicPlayerStateHolder,
    private val alarmScheduler: AlarmScheduler,
    private val alarmDao: AlarmDao,
    private val alarmRingSettingsRepository: AlarmRingSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmRingUiState())
    val uiState: StateFlow<AlarmRingUiState> = _uiState.asStateFlow()

    init {
        stateHolder.bindService()
        observePlaybackState()
        observeBreathingSettings()
    }

    private fun observeBreathingSettings() {
        combine(
            alarmRingSettingsRepository.isBreathingEnabled(),
            alarmRingSettingsRepository.getBreathingPeriodMs()
        ) { enabled, periodMs ->
            _uiState.update {
                it.copy(breathingEnabled = enabled, breathingPeriodMs = periodMs)
            }
        }
            .launchIn(viewModelScope)
    }

    private fun observePlaybackState() {
        stateHolder.playbackState
            .onEach { state ->
                _uiState.update {
                    it.copy(
                        isPlaying = state.isPlaying,
                        hasPlayback = state.currentSong != null,
                        currentSongTitle = state.currentSong?.title,
                        currentSongArtist = state.currentSong?.artist
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Load alarm label for display.
     */
    fun loadAlarmLabel(alarmId: Long) {
        viewModelScope.launch {
            val alarm = alarmDao.getById(alarmId)
            _uiState.update {
                it.copy(alarmLabel = alarm?.label ?: "")
            }
        }
    }

    /**
     * Pause playback.
     */
    fun pause(context: Context) {
        Timber.i("AlarmRing: pausing playback")
        val intent = MusicPlaybackService.createIntent(context).apply {
            action = AlarmActionReceiver.ACTION_SERVICE_PAUSE
        }
        context.startService(intent)
    }

    /**
     * Resume playback.
     */
    fun resume(context: Context) {
        Timber.i("AlarmRing: resuming playback")
        val intent = MusicPlaybackService.createIntent(context).apply {
            action = AlarmActionReceiver.ACTION_SERVICE_RESUME
        }
        context.startService(intent)
    }

    /**
     * Stop playback and dismiss the alarm.
     */
    fun stop(context: Context, alarmId: Long) {
        Timber.i("AlarmRing: stopping playback for alarmId=$alarmId")
        val intent = MusicPlaybackService.createIntent(context).apply {
            action = AlarmActionReceiver.ACTION_SERVICE_STOP
        }
        context.startService(intent)
    }

    /**
     * Snooze the alarm by 5 minutes.
     *
     * Stops current playback and reschedules the alarm to trigger after 5 minutes.
     */
    fun snooze(context: Context, alarmId: Long) {
        Timber.i("AlarmRing: snoozing alarmId=$alarmId for 5 minutes")

        // Stop current playback
        val stopIntent = MusicPlaybackService.createIntent(context).apply {
            action = AlarmActionReceiver.ACTION_SERVICE_STOP
        }
        context.startService(stopIntent)

        viewModelScope.launch {
            val alarm = alarmDao.getById(alarmId)
            if (alarm == null) {
                Timber.w("Cannot snooze: alarm $alarmId not found")
                return@launch
            }

            // Schedule a one-time snooze alarm 5 minutes from now
            val snoozeTime = System.currentTimeMillis() + 5 * 60 * 1000
            val snoozeAlarm = AlarmEntity(
                id = 0, // New ID to avoid overwriting the original alarm's schedule
                hour = alarm.hour,
                minute = alarm.minute,
                repeatDaysBitmask = 0, // One-time for snooze
                playlistId = alarm.playlistId,
                isEnabled = true,
                label = alarm.label,
                autoStopMinutes = alarm.autoStopMinutes,
                lastTriggeredAt = null
            )
            alarmScheduler.schedule(snoozeAlarm)
            Timber.i("Snoozed alarm $alarmId to trigger at $snoozeTime")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stateHolder.unbindService()
    }
}
