package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.service.alarm.ports.NotificationPort
import com.rabbithole.musicbbit.service.alarm.ports.VolumeRampPort
import com.rabbithole.musicbbit.service.alarm.ports.WakeLockPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 *  - Driving playback through [AlarmPlaybackHost]
 *  - Showing / cancelling the alarm notification
 *  - Exposing observable [AlarmFireState] for UI
 *
 * State machine: `Idle → Loading → Playing → (Paused) → Stopped`. Errors transition to
 * [AlarmFireState.Error]; the session waits for the next [fire] call before progressing.
 */
@Singleton
class AlarmFireSession @Inject constructor(
    private val alarmDao: AlarmDao,
    private val playlistRepository: PlaylistRepository,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val wakeLockPort: WakeLockPort,
    private val notificationPort: NotificationPort,
    private val volumeRampPort: VolumeRampPort,
    private val clock: Clock,
) {

    private val sessionJob = SupervisorJob()
    private val sessionScope = CoroutineScope(sessionJob + Dispatchers.Main)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<AlarmFireState>(AlarmFireState.Idle)
    val state: StateFlow<AlarmFireState> = _state.asStateFlow()

    private var host: AlarmPlaybackHost? = null
    private var autoStopJob: Job? = null
    private var extendToEnd: Boolean = false

    /** Register the playback host. Called from MusicPlaybackService.onCreate. */
    fun bindHost(newHost: AlarmPlaybackHost) {
        host = newHost
        Timber.d("AlarmFireSession host bound")
    }

    /** Unregister the playback host. Called from MusicPlaybackService.onDestroy. */
    fun unbindHost(oldHost: AlarmPlaybackHost) {
        if (host === oldHost) {
            host = null
            Timber.d("AlarmFireSession host unbound")
        }
    }

    /** Whether playback should stop after the current song completes. */
    fun isExtendToEnd(): Boolean = extendToEnd

    /** Toggle extend-to-end mode. The host queries [isExtendToEnd] on natural transitions. */
    fun setExtendToEnd(enabled: Boolean) {
        extendToEnd = enabled
        Timber.i("Extend-to-end mode: $enabled")
    }

    /**
     * Fire an alarm. Loads alarm + playlist, resolves start position, drives playback via
     * the bound host, schedules the auto-stop timer, and shows the alarm notification.
     */
    fun fire(alarmId: Long, isAlarmTrigger: Boolean) {
        if (alarmId == -1L) {
            Timber.w("AlarmFireSession.fire: invalid alarmId")
            return
        }

        if (isAlarmTrigger) {
            wakeLockPort.acquire(ALARM_WAKE_LOCK_TIMEOUT_MS)
        }

        sessionScope.launch(Dispatchers.IO) {
            mutex.withLock {
                _state.value = AlarmFireState.Loading(alarmId)

                val alarm = alarmDao.getById(alarmId)
                if (alarm == null || !alarm.isEnabled) {
                    Timber.w("Alarm id=$alarmId not found or disabled")
                    _state.value = AlarmFireState.Error(alarmId, "Alarm not found or disabled")
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
                    _state.value = AlarmFireState.Error(alarmId, "Playlist is empty")
                    return@withLock
                }

                val songs = playlistWithSongs.songs
                val startIndex = resolveStartIndex(songs, alarm.playlistId)
                val startSong = songs[startIndex]

                resetPlaybackProgress(startSong, alarm.playlistId)

                withContext(Dispatchers.Main) {
                    val currentHost = host
                    if (currentHost == null) {
                        Timber.w("No AlarmPlaybackHost bound; cannot drive playback")
                        _state.value =
                            AlarmFireState.Error(alarmId, "No playback host bound")
                        return@withContext
                    }

                    if (isAlarmTrigger && songs.isNotEmpty()) {
                        currentHost.preloadFirstSong(songs.first().path)
                    }

                    currentHost.playAlarmQueue(songs, startIndex, alarm.playlistId, alarmId)

                    if (isAlarmTrigger) {
                        volumeRampPort.startVolumeRamp(sessionScope)
                        Timber.i("Started volume ramp for alarm playback")
                    }

                    val autoStopMinutes = alarm.autoStopMinutes
                    if (autoStopMinutes != null && autoStopMinutes > 0) {
                        scheduleAutoStop(autoStopMinutes, alarmId)
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
                }
            }
        }
    }

    /**
     * Extend the auto-stop timer. The previous job is cancelled; a fresh delay starts.
     */
    fun extendAutoStop(minutes: Int) {
        if (autoStopJob == null) {
            Timber.d("extendAutoStop: no auto-stop in flight, ignoring")
            return
        }
        val alarmId = state.value.alarmId ?: -1L
        if (alarmId == -1L) {
            Timber.d("extendAutoStop: no active alarmId, ignoring")
            return
        }
        scheduleAutoStop(minutes, alarmId)
        Timber.i("Auto-stop extended by $minutes minutes")
    }

    /**
     * Called by the host when playback is fully stopped (via stop(), autoStop, or end-of-queue)
     * to release wake lock, cancel the auto-stop job, dismiss the notification, and reset state.
     */
    fun onPlaybackStopped() {
        cancelAutoStop()
        wakeLockPort.release()
        val alarmId = state.value.alarmId
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
            host?.stopPlayback()
        }
    }

    private fun cancelAutoStop() {
        autoStopJob?.cancel()
        autoStopJob = null
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
        private const val ALARM_WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
