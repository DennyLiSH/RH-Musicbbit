package com.rabbithole.musicbbit.service

import android.content.Intent
import com.rabbithole.musicbbit.MusicApplication
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

/**
 * Robolectric unit tests for [AlarmReceiver].
 *
 * These tests verify that [AlarmReceiver.onReceive] correctly starts the
 * foreground service when given a valid alarm ID, and gracefully handles
 * invalid / missing alarm IDs without crashing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MusicApplication::class)
class AlarmReceiverTest {

    @Test
    fun `onReceive with valid alarmId starts foreground service`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, 1L)
        }

        val receiver = AlarmReceiver()
        injectStubs(receiver)

        receiver.onReceive(context, intent)

        // Allow coroutine to complete
        Thread.sleep(300)

        val shadowApp = ShadowApplication.getInstance()
        val startedService = shadowApp.peekNextStartedService()

        assert(startedService != null) {
            "Expected a service to be started after onReceive with valid alarmId"
        }
    }

    @Test
    fun `onReceive with invalid alarmId does not start service`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        }

        val receiver = AlarmReceiver()
        injectStubs(receiver)

        receiver.onReceive(context, intent)

        // Allow a brief moment for the coroutine to process
        Thread.sleep(100)

        val shadowApp = ShadowApplication.getInstance()
        val startedService = shadowApp.peekNextStartedService()

        assert(startedService == null) {
            "Expected no service to be started when alarmId is invalid"
        }
    }

    @Test
    fun `onReceive with missing alarmId does not crash`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, AlarmReceiver::class.java)
        // No EXTRA_ALARM_ID attached

        val receiver = AlarmReceiver()
        injectStubs(receiver)

        // Should not throw
        receiver.onReceive(context, intent)

        Thread.sleep(100)

        val shadowApp = ShadowApplication.getInstance()
        val startedService = shadowApp.peekNextStartedService()

        assert(startedService == null) {
            "Expected no service to be started when alarmId is missing"
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun injectStubs(receiver: AlarmReceiver) {
        // Prevent Hilt from overwriting our stubs during onReceive
        val hiltClazz = Class.forName("com.rabbithole.musicbbit.service.Hilt_AlarmReceiver")
        hiltClazz.getDeclaredField("injected").apply {
            isAccessible = true
            set(receiver, true)
        }

        val clazz = AlarmReceiver::class.java

        clazz.getDeclaredField("alarmDao").apply {
            isAccessible = true
            set(receiver, StubAlarmDao())
        }
        clazz.getDeclaredField("playlistRepository").apply {
            isAccessible = true
            set(receiver, StubPlaylistRepository())
        }
        clazz.getDeclaredField("playbackProgressRepository").apply {
            isAccessible = true
            set(receiver, StubPlaybackProgressRepository())
        }
        clazz.getDeclaredField("alarmScheduler").apply {
            isAccessible = true
            set(receiver, stubAlarmScheduler())
        }
    }

    private class StubAlarmDao : AlarmDao {
        override suspend fun insert(alarm: AlarmEntity): Long = 1L
        override suspend fun update(alarm: AlarmEntity) {}
        override suspend fun delete(alarm: AlarmEntity) {}
        override fun getAll(): Flow<List<AlarmEntity>> = flowOf(emptyList())
        override fun getEnabledAlarms(): Flow<List<AlarmEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): AlarmEntity = AlarmEntity(
            id = id,
            hour = 8,
            minute = 0,
            repeatDaysBitmask = 0,
            playlistId = 1L,
            isEnabled = true,
            label = "Test Alarm",
            autoStopMinutes = null,
            lastTriggeredAt = null
        )
    }

    private class StubPlaylistRepository : PlaylistRepository {
        override fun getAllPlaylists(): Flow<List<Playlist>> = flowOf(emptyList())
        override suspend fun getPlaylistById(id: Long): Playlist? = null
        override suspend fun createPlaylist(name: String): Long = 1L
        override suspend fun updatePlaylist(playlist: Playlist) {}
        override suspend fun deletePlaylist(playlist: Playlist) {}
        override fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> = flowOf(
            PlaylistWithSongs(
                playlist = Playlist(
                    id = playlistId,
                    name = "Test Playlist",
                    createdAt = 0L,
                    updatedAt = 0L
                ),
                songs = listOf(
                    Song(
                        id = 1L,
                        path = "/test/song.mp3",
                        title = "Test Song",
                        artist = null,
                        album = null,
                        durationMs = 180_000L,
                        dateAdded = 0L,
                        coverUri = null
                    )
                )
            )
        )
        override suspend fun addSongToPlaylist(playlistId: Long, songId: Long, sortOrder: Int) {}
        override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {}
        override suspend fun reorderPlaylistSongs(playlistId: Long, songIds: List<Long>) {}
    }

    private class StubPlaybackProgressRepository : PlaybackProgressRepository {
        override suspend fun saveProgress(progress: PlaybackProgress): Result<Unit> = Result.success(Unit)
        override suspend fun getProgress(songId: Long, playlistId: Long): Result<PlaybackProgress?> = Result.success(null)
        override suspend fun deleteProgress(songId: Long, playlistId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAllProgressForPlaylist(playlistId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun getProgressForPlaylist(playlistId: Long): Result<List<PlaybackProgress>> = Result.success(emptyList())
    }

    private fun stubAlarmScheduler(): AlarmScheduler {
        // AlarmScheduler is final, so we instantiate the real class.
        // In Robolectric, calls to AlarmManager are shadowed and safe.
        return AlarmScheduler(RuntimeEnvironment.getApplication())
    }
}
