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
     * Stops current playback and schedules a snooze via [AlarmScheduler].
     */
    fun snooze(context: Context, alarmId: Long) {
        Timber.i("AlarmRing: snoozing alarmId=$alarmId for 5 minutes")

        // Stop current playback
        val stopIntent = MusicPlaybackService.createIntent(context).apply {
            action = AlarmActionReceiver.ACTION_SERVICE_STOP
        }
        context.startService(stopIntent)

        alarmScheduler.scheduleSnooze(alarmId, 5)
        Timber.i("Snoozed alarm $alarmId via AlarmManager")
    }

    override fun onCleared() {
        super.onCleared()
        stateHolder.unbindService()
    }
}
