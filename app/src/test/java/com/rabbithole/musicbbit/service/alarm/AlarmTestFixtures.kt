package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Minimal in-memory [AlarmRepository]. Simulates `recordTriggered` with the same
 * logic as the real implementation: updates `lastTriggeredAt`, disables one-time alarms.
 */
class FakeAlarmRepository : AlarmRepository {
    private val rows = mutableMapOf<Long, Alarm>()

    fun insert(alarm: Alarm) {
        rows[alarm.id] = alarm
    }

    fun getById(id: Long): Alarm? = rows[id]

    override fun getAllAlarms(): Flow<List<Alarm>> = flowOf(rows.values.toList())

    override fun getEnabledAlarms(): Flow<List<Alarm>> =
        flowOf(rows.values.filter { it.isEnabled })

    override suspend fun getAlarmById(id: Long): Alarm? = rows[id]

    override suspend fun saveAlarm(alarm: Alarm): Result<Long> {
        val id = if (alarm.id == 0L) rows.size.toLong() + 1 else alarm.id
        rows[id] = alarm.copy(id = id)
        return Result.success(id)
    }

    override suspend fun updateAlarm(alarm: Alarm): Result<Unit> {
        rows[alarm.id] = alarm
        return Result.success(Unit)
    }

    override suspend fun deleteAlarm(alarm: Alarm): Result<Unit> {
        rows.remove(alarm.id)
        return Result.success(Unit)
    }

    override suspend fun enableAlarm(id: Long, enabled: Boolean): Result<Unit> {
        rows[id]?.let { rows[id] = it.copy(isEnabled = enabled) }
        return Result.success(Unit)
    }

    override suspend fun recordTriggered(alarmId: Long): Result<Unit> {
        val alarm = rows[alarmId] ?: return Result.success(Unit)
        val isOneTime = alarm.repeatDays.isEmpty()
        rows[alarmId] = alarm.copy(
            lastTriggeredAt = FakeClock.DEFAULT_NOW_MS,
            isEnabled = if (isOneTime) false else alarm.isEnabled,
        )
        return Result.success(Unit)
    }
}

/**
 * Minimal in-memory [PlaylistRepository]; only `getPlaylistWithSongs` is exercised by
 * the session, the rest throw because they are never invoked.
 */
class FakePlaylistRepository : PlaylistRepository {
    private val playlists = mutableMapOf<Long, MutableStateFlow<PlaylistWithSongs?>>()

    fun set(playlistId: Long, value: PlaylistWithSongs?) {
        playlists.getOrPut(playlistId) { MutableStateFlow(null) }.value = value
    }

    override fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> =
        playlists.getOrPut(playlistId) { MutableStateFlow(null) }

    override fun getAllPlaylists(): Flow<List<Playlist>> = error("unused in tests")
    override suspend fun getPlaylistById(id: Long): Playlist? = error("unused in tests")
    override suspend fun createPlaylist(name: String): Result<Long> = error("unused in tests")
    override suspend fun updatePlaylist(playlist: Playlist): Result<Unit> = error("unused in tests")
    override suspend fun deletePlaylist(playlist: Playlist): Result<Unit> = error("unused in tests")
    override suspend fun addSongToPlaylist(playlistId: Long, songId: Long): Result<Unit> =
        error("unused in tests")
    override suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>): Result<Unit> =
        error("unused in tests")
    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long): Result<Unit> =
        error("unused in tests")
    override suspend fun reorderPlaylistSongs(playlistId: Long, songIds: List<Long>): Result<Unit> =
        error("unused in tests")
}

/**
 * Minimal in-memory [PlaybackProgressRepository]. Records the last save so tests can
 * assert the progress reset behavior.
 */
class FakeProgressRepository : PlaybackProgressRepository {
    private val byPlaylist = mutableMapOf<Long, List<PlaybackProgress>>()
    var lastSaved: PlaybackProgress? = null
        private set

    fun set(playlistId: Long, entries: List<PlaybackProgress>) {
        byPlaylist[playlistId] = entries
    }

    override suspend fun saveProgress(progress: PlaybackProgress): Result<Unit> {
        lastSaved = progress
        return Result.success(Unit)
    }

    override suspend fun getProgress(songId: Long, playlistId: Long): Result<PlaybackProgress?> =
        Result.success(byPlaylist[playlistId]?.firstOrNull { it.songId == songId })

    override suspend fun deleteProgress(songId: Long, playlistId: Long): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteAllProgressForPlaylist(playlistId: Long): Result<Unit> =
        Result.success(Unit)

    override suspend fun getProgressForPlaylist(playlistId: Long): Result<List<PlaybackProgress>> =
        Result.success(byPlaylist[playlistId].orEmpty())
}

class FakeClock(private val fixed: Long) : Clock {
    override fun nowMs(): Long = fixed

    companion object {
        const val DEFAULT_NOW_MS = 1_700_000_000_000L
    }
}
