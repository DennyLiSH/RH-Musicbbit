package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.di.MainDispatcher
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.alarm.ports.NotificationPort
import com.rabbithole.musicbbit.service.alarm.ports.VolumeRampPort
import com.rabbithole.musicbbit.service.alarm.ports.WakeLockPort
import com.rabbithole.musicbbit.service.playback.PlaybackSession
import com.rabbithole.musicbbit.service.playback.PlaybackTransition
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Owns the lifecycle of one alarm-fire — from receiver hand-off to playback stopped.
 *
 * The session is the single home for every rule that used to be sprinkled across
 * [com.rabbithole.musicbbit.service.AlarmReceiver],
 * [com.rabbithole.musicbbit.service.MusicPlaybackService],
 * [com.rabbithole.musicbbit.service.AlarmActionReceiver], and
 * [com.rabbithole.musicbbit.presentation.alarm.AlarmRingViewModel]:
 *
 *  - Loading the alarm + playlist (delegated to [AlarmPlaybackResolver])
 *  - Acquiring the wake lock and scheduling the auto-stop timer (delegated to [AutoStopController])
 *  - Driving playback through [PlaybackSession]
 *  - Showing / cancelling the alarm notification
 *  - Exposing observable [AlarmFireState] for UI
 *
 * State machine: `Idle → Loading → Playing → (Paused) → Stopped`. Errors transition to
 * [AlarmFireState.Error]; the session waits for the next [fire] call before progressing.
 */
@Singleton
class AlarmFireSession @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmPlaybackResolver: AlarmPlaybackResolver,
    private val wakeLockPort: WakeLockPort,
    private val notificationPort: NotificationPort,
    private val volumeRampPort: VolumeRampPort,
    private val playbackController: PlaybackSession,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val sessionJob = SupervisorJob()
    private val sessionScope = CoroutineScope(sessionJob + mainDispatcher)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<AlarmFireState>(AlarmFireState.Idle)
    val state: StateFlow<AlarmFireState> = _state.asStateFlow()

    private val autoStopController = AutoStopController(sessionScope)

    init {
        // Subscribe to playback transitions for auto-stop, extend-to-end, and external stop
        sessionScope.launch {
            playbackController.playbackTransitions.collect { transition ->
                when (transition) {
                    is PlaybackTransition.SongCompleted -> {
                        if (_state.value is AlarmFireState.Playing) {
                            val shouldStop = autoStopController.onSongCompleted() ||
                                    autoStopController.isExtendToEnd()
                            if (shouldStop) {
                                playbackController.stop()
                            }
                        }
                    }
                    is PlaybackTransition.QueueEnded -> {
                        if (_state.value is AlarmFireState.Playing) {
                            if (autoStopController.onQueueEnded()) {
                                playbackController.stop()
                            }
                        }
                    }
                    is PlaybackTransition.PlaybackStopped -> {
                        if (_state.value is AlarmFireState.Playing) {
                            onPlaybackStopped()
                        }
                    }
                }
            }
        }
    }

    /** Whether playback should stop after the current song completes. */
    fun isExtendToEnd(): Boolean = autoStopController.isExtendToEnd()

    /** Toggle extend-to-end mode. The controller queries [isExtendToEnd] on natural transitions. */
    fun setExtendToEnd(enabled: Boolean) {
        autoStopController.setExtendToEnd(enabled)
    }

    /**
     * Pause the active alarm playback. No-op if state is not [AlarmFireState.Playing].
     * Transitions state to [AlarmFireState.Paused] and shows the paused notification.
     */
    fun pause() {
        val current = _state.value
        if (current !is AlarmFireState.Playing) {
            Timber.d("AlarmFireSession.pause: state is not Playing ($current); ignoring")
            return
        }
        Timber.i("AlarmFireSession.pause: alarmId=${current.alarmId}")
        volumeRampPort.restoreVolume()
        playbackController.pause()
        notificationPort.showAlarmPaused(current.alarmId)
        _state.value = AlarmFireState.Paused(
            alarmId = current.alarmId,
            currentSong = current.currentSong,
            positionMs = current.positionMs,
        )
    }

    /**
     * Resume an alarm session previously paused via [pause]. No-op if state is not
     * [AlarmFireState.Paused]. Transitions state back to [AlarmFireState.Playing].
     */
    fun resume() {
        val current = _state.value
        if (current !is AlarmFireState.Paused) {
            Timber.d("AlarmFireSession.resume: state is not Paused ($current); ignoring")
            return
        }
        Timber.i("AlarmFireSession.resume: alarmId=${current.alarmId}")
        playbackController.resume()
        _state.value = AlarmFireState.Playing(
            alarmId = current.alarmId,
            currentSong = current.currentSong,
            positionMs = current.positionMs,
        )
    }

    /**
     * Stop the active alarm session. Delegates to the controller, which will then trigger
     * [onPlaybackStopped] via the playbackState collector once playback has actually ended.
     */
    fun stop() {
        val alarmId = _state.value.alarmIdOrNull
        Timber.i("AlarmFireSession.stop: alarmId=$alarmId")
        volumeRampPort.restoreVolume()
        playbackController.stop()
    }

    /**
     * Fire an alarm. Delegates loading to [AlarmPlaybackResolver], then drives playback,
     * schedules auto-stop, and shows the alarm notification.
     *
     * Precondition: if this is an alarm trigger (isAlarmTrigger=true), the caller must have
     * already acquired the wake lock (e.g. [MusicPlaybackService.onStartCommand]).
     */
    fun fire(alarmId: Long, isAlarmTrigger: Boolean) {
        if (alarmId == -1L) {
            Timber.w("AlarmFireSession.fire: invalid alarmId")
            return
        }

        sessionScope.launch(ioDispatcher) {
            mutex.withLock {
                _state.value = AlarmFireState.Loading(alarmId)

                when (val result = alarmPlaybackResolver.resolve(alarmId)) {
                    is AlarmPlaybackResolver.Result.Success -> {
                        startPlayback(result, isAlarmTrigger)
                        bookkeepAlarmTrigger(alarmId)
                    }
                    is AlarmPlaybackResolver.Result.Error -> {
                        notificationPort.showError(
                            notificationId = alarmId.toInt(),
                            title = result.notificationTitle ?: "Music Alarm",
                            message = result.notificationMessage,
                        )
                        transitionToError(alarmId, result.reason)
                    }
                }
            }
        }
    }

    private suspend fun startPlayback(
        result: AlarmPlaybackResolver.Result.Success,
        isAlarmTrigger: Boolean
    ) = withContext(mainDispatcher) {
        val alarm = result.alarm
        val songs = result.songs
        val startIndex = result.startIndex
        val startSong = result.startSong

        if (isAlarmTrigger && songs.isNotEmpty()) {
            playbackController.preloadFirstSong(songs.first().path)
        }

        playbackController.playAlarmQueue(songs, startIndex, alarm.playlistId, alarm.id, alarm.label)

        if (isAlarmTrigger) {
            volumeRampPort.startVolumeRamp(sessionScope)
            Timber.i("Started volume ramp for alarm playback")
        }

        autoStopController.start(alarm.autoStop) {
            playbackController.stop()
        }

        notificationPort.showAlarmPlaying(alarm, startSong)
        Timber.i("Alarm notification shown for alarm id=${alarm.id}, song=${startSong.title}")

        _state.value = AlarmFireState.Playing(
            alarmId = alarm.id,
            currentSong = startSong,
        )
    }

    /**
     * Delegate alarm-trigger bookkeeping to [AlarmRepository.recordTriggered], which
     * updates [com.rabbithole.musicbbit.domain.model.Alarm.lastTriggeredAt], auto-disables
     * one-time alarms, and reschedules repeating alarms for their next occurrence.
     */
    private suspend fun bookkeepAlarmTrigger(alarmId: Long) {
        alarmRepository.recordTriggered(alarmId)
            .onFailure { Timber.e(it, "Failed to record trigger for alarm id=$alarmId") }
    }

    /**
     * Extend the auto-stop timer. The previous job is cancelled; a fresh delay starts.
     */
    fun extendAutoStop(minutes: Int) {
        val alarmId = state.value.alarmIdOrNull ?: -1L
        if (alarmId == -1L) {
            Timber.d("extendAutoStop: no active alarmId, ignoring")
            return
        }
        autoStopController.extend(minutes) {
            playbackController.stop()
        }
        Timber.i("Auto-stop extended by $minutes minutes")
    }

    private fun transitionToError(alarmId: Long, reason: String) {
        Timber.w("AlarmFireSession transitioning to Error: alarmId=$alarmId, reason=$reason")
        if (wakeLockPort.isHeld) wakeLockPort.release()
        autoStopController.reset()
        _state.value = AlarmFireState.Error(alarmId, reason)
    }

    /**
     * Called when playback is fully stopped (via stop(), autoStop, or end-of-queue)
     * to release wake lock, cancel the auto-stop job, dismiss the notification, and reset state.
     */
    fun onPlaybackStopped() {
        volumeRampPort.restoreVolume()
        autoStopController.reset()
        if (wakeLockPort.isHeld) {
            Timber.i("AlarmFireSession: releasing wake lock in onPlaybackStopped")
            wakeLockPort.release()
        }
        val alarmId = state.value.alarmIdOrNull
        if (alarmId != null) {
            notificationPort.cancel(alarmId)
        }
        _state.value = AlarmFireState.Stopped
    }

    companion object {
        // ALARM_WAKE_LOCK_TIMEOUT_MS moved to MusicPlaybackService
    }
}
