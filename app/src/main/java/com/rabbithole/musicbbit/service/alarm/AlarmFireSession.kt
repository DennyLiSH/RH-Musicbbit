package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.di.IoDispatcher
import com.rabbithole.musicbbit.di.MainDispatcher
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.service.alarm.ports.NotificationPort
import com.rabbithole.musicbbit.service.alarm.ports.VolumeRampPort
import com.rabbithole.musicbbit.service.alarm.ports.WakeLockPort
import com.rabbithole.musicbbit.service.playback.PlayerEvent
import com.rabbithole.musicbbit.service.playback.PlaybackController
import com.rabbithole.musicbbit.service.playback.TransitionReason
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 *  - Loading the alarm + playlist
 *  - Resolving the start song / position from saved progress
 *  - Acquiring the wake lock and scheduling the auto-stop timer
 *  - Driving playback through [PlaybackController]
 *  - Showing / cancelling the alarm notification
 *  - Exposing observable [AlarmFireState] for UI
 *
 * State machine: `Idle → Loading → Playing → (Paused) → Stopped`. Errors transition to
 * [AlarmFireState.Error]; the session waits for the next [fire] call before progressing.
 */
@Singleton
class AlarmFireSession @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val wakeLockPort: WakeLockPort,
    private val notificationPort: NotificationPort,
    private val volumeRampPort: VolumeRampPort,
    private val playbackController: PlaybackController,
    private val clock: Clock,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val sessionJob = SupervisorJob()
    private val sessionScope = CoroutineScope(sessionJob + mainDispatcher)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<AlarmFireState>(AlarmFireState.Idle)
    val state: StateFlow<AlarmFireState> = _state.asStateFlow()

    private var autoStopJob: Job? = null
    private var songsRemaining: Int = 0
    private var extendToEnd: Boolean = false

    init {
        // Subscribe to playerEvents to handle song completion and extend-to-end
        sessionScope.launch {
            playbackController.playerEvents.collect { event ->
                when (event) {
                    is PlayerEvent.MediaItemTransition -> {
                        if (event.reason == TransitionReason.AUTO && _state.value is AlarmFireState.Playing) {
                            onSongCompleted()
                            if (extendToEnd) {
                                playbackController.stop()
                            }
                        }
                    }
                    is PlayerEvent.QueueEnded -> {
                        if (_state.value is AlarmFireState.Playing) {
                            onQueueEnded()
                        }
                    }
                    else -> {}
                }
            }
        }

        // Subscribe to playbackState to detect external stop
        sessionScope.launch {
            playbackController.playbackState.collect { state ->
                if (!state.isPlaying && state.alarmId == null && _state.value is AlarmFireState.Playing) {
                    onPlaybackStopped()
                }
            }
        }
    }

    /** Whether playback should stop after the current song completes. */
    fun isExtendToEnd(): Boolean = extendToEnd

    /** Toggle extend-to-end mode. The controller queries [isExtendToEnd] on natural transitions. */
    fun setExtendToEnd(enabled: Boolean) {
        extendToEnd = enabled
        Timber.i("Extend-to-end mode: $enabled")
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
        playbackController.stop()
    }

    /**
     * Fire an alarm. Loads alarm + playlist, resolves start position, drives playback via
     * the [playbackController], schedules the auto-stop timer, and shows the alarm notification.
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

                val alarm = alarmRepository.getAlarmById(alarmId)
                if (alarm == null || !alarm.isEnabled) {
                    Timber.w("Alarm id=$alarmId not found or disabled")
                    transitionToError(alarmId, "Alarm not found or disabled")
                    return@withLock
                }

                val playlistWithSongs =
                    playlistRepository.getPlaylistWithSongs(alarm.playlistId).first()
                if (playlistWithSongs == null || playlistWithSongs.songs.isEmpty()) {
                    Timber.w(
                        "Playlist id=${alarm.playlistId} is empty or not found for alarm id=$alarmId"
                    )
                    notificationPort.showError(
                        notificationId = alarmId.toInt(),
                        title = alarm.label ?: "Music Alarm",
                        message = "Playlist is empty",
                    )
                    transitionToError(alarmId, "Playlist is empty")
                    return@withLock
                }

                val songs = playlistWithSongs.songs
                val startIndex = resolveStartIndex(songs, alarm.playlistId)
                val startSong = songs[startIndex]

                resetPlaybackProgress(startSong, alarm.playlistId)

                var playbackStarted = false

                withContext(mainDispatcher) {
                    if (isAlarmTrigger && songs.isNotEmpty()) {
                        playbackController.preloadFirstSong(songs.first().path)
                    }

                    playbackController.playAlarmQueue(songs, startIndex, alarm.playlistId, alarmId)

                    if (isAlarmTrigger) {
                        volumeRampPort.startVolumeRamp(sessionScope)
                        Timber.i("Started volume ramp for alarm playback")
                    }

                    when (val stop = alarm.autoStop) {
                        is AutoStop.ByMinutes -> scheduleAutoStop(stop.minutes, alarmId)
                        is AutoStop.BySongCount -> {
                            songsRemaining = stop.count
                            Timber.i("Song counter set to ${stop.count} for alarm $alarmId")
                        }
                        null -> {}
                    }

                    extendToEnd = false

                    notificationPort.showAlarmPlaying(alarm, startSong)
                    Timber.i(
                        "Alarm notification shown for alarm id=$alarmId, song=${startSong.title}"
                    )

                    _state.value = AlarmFireState.Playing(
                        alarmId = alarmId,
                        currentSong = startSong,
                    )
                    playbackStarted = true
                }

                if (playbackStarted) {
                    bookkeepAlarmTrigger(alarmId)
                }
            }
        }
    }

    /**
     * Delegate alarm-trigger bookkeeping to [AlarmRepository.recordTriggered], which
     * updates [com.rabbithole.musicbbit.domain.model.Alarm.lastTriggeredAt], auto-disables
     * one-time alarms, and reschedules repeating alarms for their next occurrence.
     */
    private suspend fun bookkeepAlarmTrigger(alarmId: Long) {
        alarmRepository.recordTriggered(alarmId)
    }

    /**
     * Extend the auto-stop timer. The previous job is cancelled; a fresh delay starts.
     */
    fun extendAutoStop(minutes: Int) {
        if (autoStopJob == null) {
            Timber.d("extendAutoStop: no auto-stop in flight, ignoring")
            return
        }
        val alarmId = state.value.alarmIdOrNull ?: -1L
        if (alarmId == -1L) {
            Timber.d("extendAutoStop: no active alarmId, ignoring")
            return
        }
        scheduleAutoStop(minutes, alarmId)
        Timber.i("Auto-stop extended by $minutes minutes")
    }

    private fun transitionToError(alarmId: Long, reason: String) {
        Timber.w("AlarmFireSession transitioning to Error: alarmId=$alarmId, reason=$reason")
        if (wakeLockPort.isHeld) wakeLockPort.release()
        songsRemaining = 0
        _state.value = AlarmFireState.Error(alarmId, reason)
    }

    /**
     * Called when a song completes naturally (auto-advance).
     * Decrements the song counter; when it reaches zero, stops playback.
     * Only active when the session is in [AlarmFireState.Playing].
     */
    fun onSongCompleted() {
        if (_state.value !is AlarmFireState.Playing) return
        if (songsRemaining <= 0) return
        songsRemaining--
        Timber.d("Song completed, songsRemaining=$songsRemaining")
        if (songsRemaining <= 0) {
            Timber.i("Song counter reached zero, stopping playback")
            playbackController.stop()
        }
    }

    /**
     * Called when the queue ends before the song counter reaches zero.
     * Stops playback if a song counter is active and the session is [AlarmFireState.Playing].
     */
    fun onQueueEnded() {
        if (_state.value !is AlarmFireState.Playing) return
        if (songsRemaining > 0) {
            Timber.i("Queue ended with songsRemaining=$songsRemaining, stopping")
            playbackController.stop()
        }
    }

    /**
     * Called when playback is fully stopped (via stop(), autoStop, or end-of-queue)
     * to release wake lock, cancel the auto-stop job, dismiss the notification, and reset state.
     */
    fun onPlaybackStopped() {
        cancelAutoStop()
        songsRemaining = 0
        if (wakeLockPort.isHeld) {
            Timber.i("AlarmFireSession: releasing wake lock in onPlaybackStopped")
            wakeLockPort.release()
        }
        val alarmId = state.value.alarmIdOrNull
        if (alarmId != null) {
            notificationPort.cancel(alarmId)
        }
        extendToEnd = false
        _state.value = AlarmFireState.Stopped
    }

    private fun scheduleAutoStop(minutes: Int, alarmId: Long) {
        autoStopJob?.cancel()
        val delayMs = minutes * 60_000L
        Timber.i("Scheduling auto-stop in $minutes minutes for alarm $alarmId")
        autoStopJob = sessionScope.launch {
            delay(delayMs)
            Timber.i("Auto-stop triggered for alarm $alarmId")
            playbackController.stop()
        }
    }

    private fun cancelAutoStop() {
        autoStopJob?.cancel()
        autoStopJob = null
        songsRemaining = 0
        Timber.d("Auto-stop cancelled")
    }

    private suspend fun resolveStartIndex(songs: List<Song>, playlistId: Long): Int {
        val progressList =
            playbackProgressRepository.getProgressForPlaylist(playlistId).getOrNull()
        return if (!progressList.isNullOrEmpty()) {
            val latestProgress = progressList.maxByOrNull { it.updatedAt }
            val index = latestProgress?.let { progress ->
                songs.indexOfFirst { it.id == progress.songId }
            } ?: 0
            index.coerceIn(0, songs.lastIndex)
        } else {
            0
        }
    }

    private suspend fun resetPlaybackProgress(startSong: Song, playlistId: Long) {
        val progress = PlaybackProgress(
            songId = startSong.id,
            positionMs = 0,
            updatedAt = clock.nowMs(),
            playlistId = playlistId,
        )
        playbackProgressRepository.saveProgress(progress).onSuccess {
            Timber.d("Reset progress for song id=${startSong.id}")
        }.onFailure { error ->
            Timber.e(error, "Failed to reset progress for song id=${startSong.id}")
        }
    }

    companion object {
        // ALARM_WAKE_LOCK_TIMEOUT_MS moved to MusicPlaybackService
    }
}
