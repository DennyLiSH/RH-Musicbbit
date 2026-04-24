package com.rabbithole.musicbbit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * BroadcastReceiver that handles alarm trigger events from [AlarmManager].
 *
 * Responsibilities:
 * - Acquire a partial wake lock to ensure the device stays awake during processing
 * - Load the triggered alarm and its associated playlist
 * - Determine the starting song index based on saved playback progress
 * - Reset playback progress for the starting song
 * - Start [MusicPlaybackService] as a foreground service
 * - Update the alarm's [lastTriggeredAt] timestamp
 * - Reschedule repeating alarms for their next occurrence
 * - Display an alarm notification via [AlarmNotificationHelper]
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmDao: AlarmDao

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    @Inject
    lateinit var playbackProgressRepository: PlaybackProgressRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("AlarmReceiver triggered")

        val pendingResult = goAsync()
        val wakeLock = acquireWakeLock(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                if (alarmId == -1L) {
                    Timber.e("AlarmReceiver received invalid alarmId")
                    return@launch
                }

                Timber.i("Processing alarm id=$alarmId")

                val alarm = alarmDao.getById(alarmId)
                if (alarm == null) {
                    Timber.w("Alarm id=$alarmId not found in database")
                    return@launch
                }

                if (!alarm.isEnabled) {
                    Timber.d("Alarm id=$alarmId is disabled, ignoring trigger")
                    return@launch
                }

                val playlistWithSongs =
                    playlistRepository.getPlaylistWithSongs(alarm.playlistId).first()

                if (playlistWithSongs == null || playlistWithSongs.songs.isEmpty()) {
                    Timber.w("Playlist id=${alarm.playlistId} is empty or not found for alarm id=$alarmId")
                    AlarmNotificationHelper.showErrorNotification(
                        context,
                        alarmId.toInt(),
                        alarm.label ?: "Music Alarm",
                        "Playlist is empty"
                    )
                    return@launch
                }

                val songs = playlistWithSongs.songs
                val startIndex = resolveStartIndex(songs, alarm)
                val startSong = songs[startIndex]

                // Reset playback progress for the starting song to beginning
                resetPlaybackProgress(startSong, alarm)

                // Start the playback service
                startPlaybackService(context, alarm, songs, startIndex)

                // Update last triggered timestamp
                val updatedAlarm = alarm.copy(lastTriggeredAt = System.currentTimeMillis())
                alarmDao.update(updatedAlarm)
                Timber.i("Updated lastTriggeredAt for alarm id=$alarmId")

                // Reschedule repeating alarms
                if (alarm.repeatDaysBitmask != 0) {
                    Timber.i("Rescheduling repeating alarm id=$alarmId")
                    alarmScheduler.schedule(updatedAlarm)
                }

                // Show alarm notification
                AlarmNotificationHelper.show(context, alarm, startSong)
                Timber.i("Alarm notification shown for alarm id=$alarmId, song=${startSong.title}")
            } catch (e: Exception) {
                Timber.e(e, "AlarmReceiver processing failed")
            } finally {
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Timber.d("WakeLock released")
                    }
                }
                pendingResult.finish()
            }
        }
    }

    /**
     * Acquire a partial wake lock with a 10-second timeout.
     *
     * @param context The context to use.
     * @return The acquired wake lock, or null if acquisition failed.
     */
    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
                Timber.d("WakeLock acquired")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire wake lock")
            null
        }
    }

    /**
     * Resolve the starting song index based on saved playback progress.
     *
     * If progress exists for the playlist, the song with the most recent update
     * is selected. Otherwise, playback starts from the first song.
     *
     * @param songs The list of songs in the playlist.
     * @param alarm The triggered alarm entity.
     * @return The index of the song to start playback from.
     */
    private suspend fun resolveStartIndex(songs: List<Song>, alarm: AlarmEntity): Int {
        val progressList =
            playbackProgressRepository.getProgressForPlaylist(alarm.playlistId).getOrNull()

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

    /**
     * Reset playback progress for the starting song to the beginning.
     *
     * @param startSong The song that will be played first.
     * @param alarm The triggered alarm entity.
     */
    private suspend fun resetPlaybackProgress(startSong: Song, alarm: AlarmEntity) {
        val progress = PlaybackProgress(
            songId = startSong.id,
            positionMs = 0,
            updatedAt = System.currentTimeMillis(),
            playlistId = alarm.playlistId
        )
        playbackProgressRepository.saveProgress(progress).onSuccess {
            Timber.d("Reset progress for song id=${startSong.id}")
        }.onFailure { error ->
            Timber.e(error, "Failed to reset progress for song id=${startSong.id}")
        }
    }

    /**
     * Start [MusicPlaybackService] as a foreground service with the alarm playback intent.
     *
     * @param context The context to use.
     * @param alarm The triggered alarm entity.
     * @param songs The list of songs to play.
     * @param startIndex The index of the song to start from.
     */
    private fun startPlaybackService(
        context: Context,
        alarm: AlarmEntity,
        songs: List<Song>,
        startIndex: Int
    ) {
        val serviceIntent = MusicPlaybackService.createIntent(context).apply {
            action = MusicPlaybackService.ACTION_PLAY_ALARM
            putParcelableArrayListExtra(MusicPlaybackService.EXTRA_SONGS, ArrayList(songs))
            putExtra(MusicPlaybackService.EXTRA_START_INDEX, startIndex)
            putExtra(MusicPlaybackService.EXTRA_PLAYLIST_ID, alarm.playlistId)
            putExtra(MusicPlaybackService.EXTRA_AUTO_STOP_MINUTES, alarm.autoStopMinutes ?: 0)
            putExtra(MusicPlaybackService.EXTRA_ALARM_ID, alarm.id)
            putExtra(MusicPlaybackService.EXTRA_IS_ALARM_TRIGGER, true)
        }
        context.startForegroundService(serviceIntent)
        Timber.i("Started MusicPlaybackService for alarm id=${alarm.id}, startIndex=$startIndex")
    }

    companion object {
        private const val WAKE_LOCK_TAG = "RH-Musicbbit::AlarmWakeLock"
        private const val WAKE_LOCK_TIMEOUT_MS = 10_000L
    }
}
