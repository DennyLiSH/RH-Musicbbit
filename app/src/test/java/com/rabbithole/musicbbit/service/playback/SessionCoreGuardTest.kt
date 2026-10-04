package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.alarm.FakeProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SessionCore-level command guard: symmetric for both sessions.
 * A session that does not own the shared player must not issue player commands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoreGuardTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var playerPort: FakePlayerPort
    private lateinit var audioStreamPort: FakeAudioStreamPort
    private lateinit var audioFocusPort: FakeAudioFocusPort
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var serviceStarter: FakeServiceStarter
    private lateinit var coordinator: PlaybackCoordinator
    private lateinit var userSession: UserPlaybackSession
    private lateinit var alarmSession: AlarmPlaybackSession

    companion object {
        private val SONG_1 = Song(
            id = 1L,
            path = "/tmp/song1.mp3",
            title = "Song One",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            dateAdded = 0L,
            coverUri = null,
        )
        private val ALARM_SONG = SONG_1.copy(id = 100L, path = "/tmp/alarm.mp3", title = "Alarm Song")
    }

    @Before
    fun setUp() {
        playerPort = FakePlayerPort()
        audioStreamPort = FakeAudioStreamPort()
        audioFocusPort = FakeAudioFocusPort()
        progressRepository = FakeProgressRepository()
        serviceStarter = FakeServiceStarter()
        coordinator = PlaybackCoordinator(
            playerPort = playerPort,
            audioFocusPort = audioFocusPort,
            mainDispatcher = dispatcher,
        )
        userSession = UserPlaybackSession(
            playerPort = playerPort,
            audioStreamPort = audioStreamPort,
            playbackProgressRepository = progressRepository,
            serviceStarter = serviceStarter,
            audioFocusPort = audioFocusPort,
            playbackCoordinator = coordinator,
            mainDispatcher = dispatcher,
        )
        alarmSession = AlarmPlaybackSession(
            playerPort = playerPort,
            audioStreamPort = audioStreamPort,
            playbackProgressRepository = progressRepository,
            audioFocusPort = audioFocusPort,
            serviceStarter = serviceStarter,
            playbackCoordinator = coordinator,
            mainDispatcher = dispatcher,
        )
    }

    @After
    fun tearDown() {
        userSession.close()
        alarmSession.close()
        coordinator.close()
    }

    @Test
    fun `seekTo is a no-op when another session owns the player`() {
        alarmSession.playAlarmQueue(
            listOf(ALARM_SONG),
            startIndex = 0,
            playlistId = 1L,
            useAlarmStream = false,
        )
        // alarm now owns the player; user session must not seek
        userSession.seekTo(1000L)
        // FakePlayerPort records seekTo calls — assert none happened
        assertTrue(
            "user.seekTo must be dropped when alarm owns the player",
            playerPort.seekCalls.isEmpty(),
        )
    }

    @Test
    fun `alarm pause is a no-op when the user session owns the player`() {
        userSession.play(SONG_1, playlistId = -1L)
        // user owns the player; alarm must not be able to pause
        alarmSession.pause()
        assertTrue(
            "alarm.pause must be dropped when user owns the player",
            playerPort.pauseCalls.isEmpty(),
        )
    }

    @Test
    fun `issueCommand passes through when session owns the player`() {
        userSession.play(SONG_1, playlistId = -1L)
        userSession.pause()
        userSession.resume()
        // both pause/resume should have reached the port
        assertEquals(1, playerPort.pauseCalls.size)
        assertTrue("user resume should have called play() at least once", playerPort.playCalls.isNotEmpty())
    }

    private class FakeServiceStarter : ServiceStarter {
        var startCalled = false
            private set
        var stopCalled = false
            private set

        override fun startService() {
            startCalled = true
        }

        override fun stopService() {
            stopCalled = true
        }
    }
}
