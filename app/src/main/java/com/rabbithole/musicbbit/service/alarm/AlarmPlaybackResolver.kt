package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Resolves everything needed to start alarm playback: loads the alarm, loads the playlist,
 * determines the start song from saved progress, and resets that progress.
 *
 * This is a deep module: a lot of data-fetching and decision-making behind a tiny interface
 * (`resolve(alarmId)`). Errors are normalized so the caller can handle them uniformly.
 */
class AlarmPlaybackResolver(
    private val alarmRepository: AlarmRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val clock: Clock,
) {

    sealed interface Result {
        data class Success(
            val alarm: Alarm,
            val songs: List<Song>,
            val startIndex: Int,
            val startSong: Song,
        ) : Result

        data class Error(
            val reason: String,
            val notificationTitle: String?,
            val notificationMessage: String,
        ) : Result
    }

    suspend fun resolve(alarmId: Long): Result {
        val alarm = alarmRepository.getAlarmById(alarmId)
        if (alarm == null || !alarm.isEnabled) {
            Timber.w("Alarm id=$alarmId not found or disabled")
            return Result.Error(
                reason = "Alarm not found or disabled",
                notificationTitle = null,
                notificationMessage = "Alarm not found or disabled",
            )
        }

        val playlistWithSongs =
            playlistRepository.getPlaylistWithSongs(alarm.playlistId).first()
        if (playlistWithSongs == null || playlistWithSongs.songs.isEmpty()) {
            Timber.w("Playlist id=${alarm.playlistId} is empty or not found for alarm id=$alarmId")
            return Result.Error(
                reason = "Playlist is empty",
                notificationTitle = alarm.label ?: "Music Alarm",
                notificationMessage = "Playlist is empty",
            )
        }

        val songs = playlistWithSongs.songs
        val startIndex = resolveStartIndex(songs, alarm.playlistId)
        val startSong = songs[startIndex]

        resetPlaybackProgress(startSong, alarm.playlistId)

        return Result.Success(alarm, songs, startIndex, startSong)
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
}
