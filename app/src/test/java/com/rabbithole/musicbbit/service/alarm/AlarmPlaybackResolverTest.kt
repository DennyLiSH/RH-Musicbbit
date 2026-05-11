package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmPlaybackResolverTest {

    private val clock = FakeClock(1_700_000_000_000L)

    private val song1 = Song(
        id = 1L, path = "/tmp/1.mp3", title = "One",
        artist = "A", album = "B", durationMs = 180_000L, dateAdded = 0L, coverUri = null
    )
    private val song2 = Song(
        id = 2L, path = "/tmp/2.mp3", title = "Two",
        artist = "A", album = "B", durationMs = 180_000L, dateAdded = 0L, coverUri = null
    )

    @Test
    fun `resolve returns Success when alarm and playlist exist`() = runTest {
        val alarmRepo = FakeAlarmRepository().apply {
            insert(alarm(id = 1L, playlistId = 10L))
        }
        val playlistRepo = FakePlaylistRepository().apply {
            set(10L, PlaylistWithSongs(Playlist(10L, "Test", 0L, 0L), listOf(song1, song2)))
        }
        val progressRepo = FakeProgressRepository()

        val resolver = AlarmPlaybackResolver(alarmRepo, playlistRepo, progressRepo, clock)
        val result = resolver.resolve(1L)

        assertTrue(result is AlarmPlaybackResolver.Result.Success)
        val success = result as AlarmPlaybackResolver.Result.Success
        assertEquals(1L, success.alarm.id)
        assertEquals(2, success.songs.size)
        assertEquals(0, success.startIndex)
        assertEquals(song1, success.startSong)
    }

    @Test
    fun `resolve returns Error when alarm not found`() = runTest {
        val alarmRepo = FakeAlarmRepository()
        val playlistRepo = FakePlaylistRepository()
        val progressRepo = FakeProgressRepository()

        val resolver = AlarmPlaybackResolver(alarmRepo, playlistRepo, progressRepo, clock)
        val result = resolver.resolve(99L)

        assertTrue(result is AlarmPlaybackResolver.Result.Error)
    }

    @Test
    fun `resolve returns Error when alarm is disabled`() = runTest {
        val alarmRepo = FakeAlarmRepository().apply {
            insert(alarm(id = 1L, playlistId = 10L).copy(isEnabled = false))
        }
        val playlistRepo = FakePlaylistRepository()
        val progressRepo = FakeProgressRepository()

        val resolver = AlarmPlaybackResolver(alarmRepo, playlistRepo, progressRepo, clock)
        val result = resolver.resolve(1L)

        assertTrue(result is AlarmPlaybackResolver.Result.Error)
    }

    @Test
    fun `resolve returns Error when playlist is empty`() = runTest {
        val alarmRepo = FakeAlarmRepository().apply {
            insert(alarm(id = 1L, playlistId = 10L))
        }
        val playlistRepo = FakePlaylistRepository().apply {
            set(10L, PlaylistWithSongs(Playlist(10L, "Empty", 0L, 0L), emptyList()))
        }
        val progressRepo = FakeProgressRepository()

        val resolver = AlarmPlaybackResolver(alarmRepo, playlistRepo, progressRepo, clock)
        val result = resolver.resolve(1L)

        assertTrue(result is AlarmPlaybackResolver.Result.Error)
    }

    @Test
    fun `resolve picks start index from latest progress`() = runTest {
        val alarmRepo = FakeAlarmRepository().apply {
            insert(alarm(id = 1L, playlistId = 10L))
        }
        val playlistRepo = FakePlaylistRepository().apply {
            set(10L, PlaylistWithSongs(Playlist(10L, "Test", 0L, 0L), listOf(song1, song2)))
        }
        val progressRepo = FakeProgressRepository().apply {
            set(10L, listOf(
                PlaybackProgress(songId = 2L, positionMs = 30_000L, updatedAt = 100L, playlistId = 10L)
            ))
        }

        val resolver = AlarmPlaybackResolver(alarmRepo, playlistRepo, progressRepo, clock)
        val result = resolver.resolve(1L)

        assertTrue(result is AlarmPlaybackResolver.Result.Success)
        val success = result as AlarmPlaybackResolver.Result.Success
        assertEquals(1, success.startIndex) // song2 is at index 1
        assertEquals(song2, success.startSong)
    }

    private fun alarm(id: Long, playlistId: Long) = Alarm(
        id = id, hour = 7, minute = 0,
        repeatDays = emptySet(), playlistId = playlistId,
        isEnabled = true, label = "Test", autoStop = null, lastTriggeredAt = null
    )
}
