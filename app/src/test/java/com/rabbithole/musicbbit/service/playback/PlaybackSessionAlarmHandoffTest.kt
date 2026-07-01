package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.alarm.FakeProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Cross-session integration test verifying PlaybackCoordinator handoff behavior between
 * [PlaybackSession] (user playback) and [AlarmPlaybackSession] (alarm playback).
 *
 * These tests use real session instances sharing a single [FakePlayerPort] /
 * [FakeAudioFocusPort] / [PlaybackCoordinator] — no mocks of the sessions themselves —
 * to catch interaction bugs that single-session unit tests miss.
 *
 * Driven by the Iteration 1 finding that PlaybackSession's progress loops did not stop
 * when alarm took over the shared PlayerPort, corrupting user progress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionAlarmHandoffTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var playerPort: FakePlayerPort
    private lateinit var audioFocusPort: FakeAudioFocusPort
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var musicNotificationPort: MusicNotificationPort
    private lateinit var serviceStarter: FakeServiceStarter
    private lateinit var coordinator: PlaybackCoordinator
    private lateinit var userSession: PlaybackSession
    private lateinit var alarmSession: AlarmPlaybackSession

    companion object {
        private val USER_SONG = Song(
            id = 1L,
            path = "/tmp/user.mp3",
            title = "User Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            dateAdded = 0L,
            coverUri = null,
        )
        private val ALARM_SONG_1 = USER_SONG.copy(
            id = 100L,
            path = "/tmp/alarm1.mp3",
            title = "Alarm Song 1",
        )
        private val ALARM_SONG_2 = USER_SONG.copy(
            id = 101L,
            path = "/tmp/alarm2.mp3",
            title = "Alarm Song 2",
        )
    }

    @Before
    fun setUp() {
        playerPort = FakePlayerPort()
        audioFocusPort = FakeAudioFocusPort()
        progressRepository = FakeProgressRepository()
        musicNotificationPort = mock()
        serviceStarter = FakeServiceStarter()
        coordinator = PlaybackCoordinator(
            playerPort = playerPort,
            audioFocusPort = audioFocusPort,
            mainDispatcher = dispatcher,
        )
        userSession = PlaybackSession(
            playerPort = playerPort,
            playbackProgressRepository = progressRepository,
            musicNotificationPort = musicNotificationPort,
            serviceStarter = serviceStarter,
            audioFocusPort = audioFocusPort,
            playbackCoordinator = coordinator,
            mainDispatcher = dispatcher,
        )
        alarmSession = AlarmPlaybackSession(
            playerPort = playerPort,
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
    fun `alarm fires while user playing triggers userSession onDeactivated`() = runBlocking {
        // Start user playback
        userSession.play(USER_SONG, playlistId = 50L)
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))
        assertTrue(userSession.playbackState.value.isPlaying)

        // Alarm takes over
        alarmSession.playAlarmQueue(
            listOf(ALARM_SONG_1, ALARM_SONG_2),
            startIndex = 0,
            playlistId = 100L,
        )

        // userSession should be marked inactive (isPlaying=false via onDeactivated)
        assertFalse(
            "userSession.onDeactivated must clear isPlaying on handoff",
            userSession.playbackState.value.isPlaying,
        )
        Unit
    }

    @Test
    fun `alarm fires while user playing does not corrupt user progress`() = runBlocking {
        // Start user playback and capture the last-saved user progress before handoff.
        userSession.play(USER_SONG, playlistId = 50L)
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))
        // Simulate the periodic saveLoop writing the user's position.
        playerPort.currentPositionMsValue = 30_000L
        userSession.pause()  // triggers saveProgress with USER_SONG @ 30_000ms

        val savedBeforeHandoff = progressRepository.lastSaved
        assertNotNull("Pre-condition: user progress saved before alarm", savedBeforeHandoff)
        assertEquals(USER_SONG.id, savedBeforeHandoff?.songId)
        assertEquals(30_000L, savedBeforeHandoff?.positionMs)
        assertEquals(50L, savedBeforeHandoff?.playlistId)

        // User resumes, then alarm fires (handoff).
        userSession.resume()
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        alarmSession.playAlarmQueue(
            listOf(ALARM_SONG_1),
            startIndex = 0,
            playlistId = 100L,
        )

        // Critical assertion: handoff must NOT trigger a saveProgress that would
        // write alarm's data (songId=ALARM_SONG_1.id, positionMs=0, playlistId=100)
        // over the user's saved progress.
        // If a save had happened, lastSaved would now hold the alarm's data.
        val savedAfterHandoff = progressRepository.lastSaved
        assertEquals(
            "onDeactivated must not saveProgress during handoff (songId would change to alarm's)",
            savedBeforeHandoff?.songId,
            savedAfterHandoff?.songId,
        )
        assertEquals(
            "user position must remain at pre-handoff value, not alarm's position",
            savedBeforeHandoff?.positionMs,
            savedAfterHandoff?.positionMs,
        )
        Unit
    }

    @Test
    fun `alarm fires while user playing makes alarmSession the active consumer`() = runBlocking {
        userSession.play(USER_SONG, playlistId = 50L)
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        alarmSession.playAlarmQueue(
            listOf(ALARM_SONG_1),
            startIndex = 0,
            playlistId = 100L,
        )

        // Player events emitted after handoff should be handled by alarmSession, not userSession.
        // userSession.isPlaying was cleared by onDeactivated; an IsPlayingChanged(false) event
        // routed to alarmSession shouldn't affect userSession state.
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(false))

        // alarmSession.isPlay set to false by event; userSession stays false (already cleared)
        assertFalse(alarmSession.playbackState.value.isPlaying)
        assertFalse(userSession.playbackState.value.isPlaying)
        Unit
    }

    @Test
    fun `user can resume playback after alarm ends without state leak`() = runBlocking {
        // User was playing
        userSession.play(USER_SONG, playlistId = 50L)
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        // Alarm fires, plays, ends
        alarmSession.playAlarmQueue(
            listOf(ALARM_SONG_1),
            startIndex = 0,
            playlistId = 100L,
        )
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        // Alarm stops (e.g. user dismissed or auto-stop fired)
        alarmSession.stop()

        // User can play again — no exception, no leaked state from alarm
        userSession.play(USER_SONG, playlistId = 50L)
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        assertEquals(USER_SONG, userSession.playbackState.value.currentSong)
        assertTrue(userSession.playbackState.value.isPlaying)
        Unit
    }

    @Test
    fun `reverse handoff - user plays during alarm triggers alarmSession onDeactivated`() = runBlocking {
        // Alarm is active
        alarmSession.playAlarmQueue(
            listOf(ALARM_SONG_1, ALARM_SONG_2),
            startIndex = 0,
            playlistId = 100L,
        )
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))
        assertTrue(alarmSession.playbackState.value.isPlaying)

        // User manually plays a song (defensive path — even if UI blocks this normally,
        // the coordinator must handle it correctly)
        userSession.play(USER_SONG, playlistId = 50L)

        // alarmSession should be marked inactive via onDeactivated
        assertFalse(
            "alarmSession.onDeactivated must clear isPlaying on reverse handoff",
            alarmSession.playbackState.value.isPlaying,
        )
        Unit
    }

    @Test
    fun `user idle when alarm fires is no-op safe`() = runBlocking {
        // Pre-condition: user session has no current song (never played)
        assertNull(userSession.playbackState.value.currentSong)

        // Alarm fires while user idle
        alarmSession.playAlarmQueue(
            listOf(ALARM_SONG_1),
            startIndex = 0,
            playlistId = 100L,
        )

        // userSession.onDeactivated runs but has nothing to do — no crash, no save.
        // userSession state remains idle.
        assertNull(userSession.playbackState.value.currentSong)
        // alarmSession takes over normally
        assertEquals(ALARM_SONG_1, alarmSession.playbackState.value.currentSong)
        Unit
    }

    // -------------------------------------------------------------------------
    // Local fakes (avoid cross-test-file dependencies)
    // -------------------------------------------------------------------------

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
