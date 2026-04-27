package com.rabbithole.musicbbit.presentation.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.service.alarm.AlarmFireSession
import com.rabbithole.musicbbit.service.alarm.AlarmFireState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import com.rabbithole.musicbbit.R
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
    val breathingPeriodMs: Long = 3500L,
    val errorMessageResId: Int? = null
)

/**
 * ViewModel for the alarm ring activity.
 *
 * Observes the active [AlarmFireSession] state directly; pause / resume / stop
 * dispatch to the session in-process rather than round-tripping through a service
 * intent. The Activity no longer needs to hand a [android.content.Context] to the
 * ViewModel for these actions.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlarmRingViewModel @Inject constructor(
    private val alarmFireSession: AlarmFireSession,
    private val alarmRepository: AlarmRepository,
    private val alarmRingSettingsRepository: AlarmRingSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmRingUiState())
    val uiState: StateFlow<AlarmRingUiState> = _uiState.asStateFlow()

    init {
        observeAlarmFireState()
        observeBreathingSettings()
        observeAlarmLabel()
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
            .catch { e ->
                Timber.e(e, "Breathing settings flow failed")
                _uiState.update { it.copy(errorMessageResId = R.string.error_load_failed) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeAlarmFireState() {
        alarmFireSession.state
            .onEach { state ->
                val song = when (state) {
                    is AlarmFireState.Playing -> state.currentSong
                    is AlarmFireState.Paused -> state.currentSong
                    else -> null
                }
                _uiState.update {
                    it.copy(
                        isPlaying = state is AlarmFireState.Playing,
                        hasPlayback = song != null,
                        currentSongTitle = song?.title,
                        currentSongArtist = song?.artist
                    )
                }
            }
            .catch { e ->
                Timber.e(e, "Alarm fire state flow failed")
                _uiState.update { it.copy(errorMessageResId = R.string.error_load_failed) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeAlarmLabel() {
        alarmFireSession.state
            .map { it.alarmIdOrNull }
            .distinctUntilChanged()
            .filterNotNull()
            .flatMapLatest { id ->
                flow {
                    val label = runCatching {
                        alarmRepository.getAlarmById(id)?.label
                    }.getOrElse { e ->
                        Timber.e(e, "Failed to load alarm label for id=$id")
                        null
                    }
                    emit(label ?: "")
                }
            }
            .onEach { label ->
                _uiState.update { it.copy(alarmLabel = label) }
            }
            .launchIn(viewModelScope)
    }

    /** Pause playback through the active alarm session. */
    fun pause() {
        Timber.i("AlarmRing: pausing playback")
        alarmFireSession.pause()
    }

    /** Resume playback through the active alarm session. */
    fun resume() {
        Timber.i("AlarmRing: resuming playback")
        alarmFireSession.resume()
    }

    /** Stop playback and dismiss the alarm. */
    fun stop() {
        Timber.i("AlarmRing: stopping playback")
        alarmFireSession.stop()
    }
}
