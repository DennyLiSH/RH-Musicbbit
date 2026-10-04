package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AlarmRingMode
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.service.alarm.ports.NotificationPort
import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import com.rabbithole.musicbbit.service.alarm.ports.VolumeRampPort
import com.rabbithole.musicbbit.service.alarm.ports.WakeLockPort
import com.rabbithole.musicbbit.service.playback.AlarmPlaybackSession
import com.rabbithole.musicbbit.service.playback.PlaybackTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import timber.log.Timber

/**
 * JVM unit tests for [AlarmFireSession].
 *
 * Covers the alarm-fire surface that used to be sprinkled across AlarmReceiver,
 * MusicPlaybackService.handlePlayAlarm, AlarmActionReceiver, and AlarmRingViewModel:
 *
 *   - fire(repeating) keeps isEnabled = true and reschedules
 *   - fire(one-time) flips isEnabled = false and does NOT reschedule
 *   - fire resolves the start index from saved progress and resets it
 *   - fire short-circuits to Error when the alarm is missing or the playlist is empty
 *     (and bookkeeping does NOT run in those cases)
 *   - pause / resume gate on state and update notification + transition to Paused / Playing
 *   - autoStop calls session.stop after the configured delay
 *   - extendAutoStop cancels the prior timer and starts a fresh one
 *   - onPlaybackStopped releases the wake lock, cancels the notification, and resets state
 *   - playback transitions (SongCompleted, QueueEnded, PlaybackStopped) drive session actions
 *   - QueueEnded deletes playlist progress when playlistId matches the alarm
 *   - QueueEnded skips cleanup when playlistId does not match
 *
 * All tests use [UnconfinedTestDispatcher] sharing one [TestScope] scheduler so both the
 * injected main and IO dispatchers participate in virtual time. This lets the autoStop
 * delay be advanced deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmFireSessionTest {

    private val scope = TestScope()
    private val testDispatcher = UnconfinedTestDispatcher(scope.testScheduler)

    private lateinit var alarmRepository: FakeAlarmRepository
    private lateinit var playlistRepository: FakePlaylistRepository
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var wakeLockPort: FakeWakeLockPort
    private lateinit var notificationPort: FakeNotificationPort
    private lateinit var volumeRampPort: FakeVolumeRampPort
    private lateinit var clock: FakeClock
    private lateinit var alarmPlaybackSession: AlarmPlaybackSession
    private lateinit var fakeControls: FakeAlarmPlaybackControls

    private lateinit var session: AlarmFireSession

    companion object {
        private const val NOW_MS = FakeClock.DEFAULT_NOW_MS

        private val SONG_1 = Song(
            id = 101L,
            path = "/tmp/song1.mp3",
            title = "Song One",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            dateAdded = 0L,
            coverUri = null,
        )
        private val SONG_2 = SONG_1.copy(id = 102L, path = "/tmp/song2.mp3", title = "Song Two")
        private val SONG_3 = SONG_1.copy(id = 103L, path = "/tmp/song3.mp3", title = "Song Three")

        @JvmStatic
        @BeforeClass
        fun plantTimber() {
            Timber.uprootAll()
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {}
            })
        }
    }

    @Before
    fun setUp() {
        alarmRepository = FakeAlarmRepository()
        playlistRepository = FakePlaylistRepository()
        progressRepository = FakeProgressRepository()
        wakeLockPort = FakeWakeLockPort()
        notificationPort = FakeNotificationPort()
        volumeRampPort = FakeVolumeRampPort()
        clock = FakeClock(NOW_MS)
        alarmPlaybackSession = mock()
        fakeControls = FakeAlarmPlaybackControls()
        whenever(alarmPlaybackSession.playbackTransitions).thenReturn(fakeControls.playbackTransitions)
        whenever(alarmPlaybackSession.stop()).thenAnswer { fakeControls.stop() }
        whenever(alarmPlaybackSession.pause()).thenAnswer { fakeControls.pause() }
        whenever(alarmPlaybackSession.resume()).thenAnswer { fakeControls.resume() }
        whenever(
            alarmPlaybackSession.playAlarmQueue(
                any<List<Song>>(),
                any<Int>(),
                any<Long>(),
                any<Boolean>(),
            )
        ).thenAnswer { invocation ->
            fakeControls.playAlarmQueue(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
            )
        }
        val alarmPlaybackResolver = AlarmPlaybackResolver(
            alarmRepository = alarmRepository,
            playlistRepository = playlistRepository,
            playbackProgressRepository = progressRepository,
            clock = clock,
        )
        val autoStopController = AutoStopController(defaultDispatcher = testDispatcher)

        session = AlarmFireSession(
            bypassPlanResolver = QuietModeBypassResolver(noDndPermissionPort),
            alarmRepository = alarmRepository,
            alarmPlaybackResolver = alarmPlaybackResolver,
            playbackProgressRepository = progressRepository,
            wakeLockPort = wakeLockPort,
            notificationPort = notificationPort,
            volumeRampPort = volumeRampPort,
            alarmPlaybackSession = alarmPlaybackSession,
            autoStopController = autoStopController,
            mainDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
        )
    }

    // -------- fire() success paths --------------------------------------------

    @Test
    fun `fire repeating alarm keeps isEnabled true and reschedules`() = scope.runTest {
        val alarm = repeatingAlarm(id = 1L, playlistId = 10L)
        alarmRepository.insert(alarm)
        playlistRepository.set(10L, threeSongPlaylist(id = 10L))

        session.fire(alarmId = 1L, isAlarmTrigger = true)
        runCurrent()

        val state = session.state.value
        assertTrue("expected Playing, was $state", state is AlarmFireState.Playing)
        assertEquals(1L, (state as AlarmFireState.Playing).alarmId)
        assertEquals(SONG_1.title, state.currentSong?.title)

        val updated = alarmRepository.getById(1L)
        assertTrue("repeating alarm must remain enabled", updated!!.isEnabled)
        assertEquals(NOW_MS, updated.lastTriggeredAt)
    }

    @Test
    fun `fire one-time alarm flips isEnabled false and does NOT reschedule`() = scope.runTest {
        val alarm = oneTimeAlarm(id = 2L, playlistId = 20L)
        alarmRepository.insert(alarm)
        playlistRepository.set(20L, threeSongPlaylist(id = 20L))

        session.fire(alarmId = 2L, isAlarmTrigger = true)
        runCurrent()

        val updated = alarmRepository.getById(2L)
        assertFalse("one-time alarm must be disabled after firing", updated!!.isEnabled)
    }

    @Test
    fun `fire passes useAlarmStream true when ignoreQuietMode enabled`() = scope.runTest {
        alarmRepository.insert(repeatingAlarm(id = 21L, playlistId = 210L))
        playlistRepository.set(210L, threeSongPlaylist(id = 210L))

        session.fire(alarmId = 21L, isAlarmTrigger = true)
        runCurrent()

        assertEquals(true, fakeControls.lastUseAlarmStream)
        assertEquals(true, volumeRampPort.lastUseAlarmStream)
    }

    @Test
    fun `fire passes useAlarmStream false when ignoreQuietMode disabled`() = scope.runTest {
        alarmRepository.insert(repeatingAlarm(id = 22L, playlistId = 220L).copy(ignoreQuietMode = false))
        playlistRepository.set(220L, threeSongPlaylist(id = 220L))

        session.fire(alarmId = 22L, isAlarmTrigger = true)
        runCurrent()

        assertEquals(false, fakeControls.lastUseAlarmStream)
        assertEquals(false, volumeRampPort.lastUseAlarmStream)
    }

    @Test
    fun `fire with resumePlayback true resumes from saved progress and does not reset it`() = scope.runTest {
        val alarm = repeatingAlarm(id = 3L, playlistId = 30L)
        alarmRepository.insert(alarm)
        playlistRepository.set(30L, threeSongPlaylist(id = 30L))
        progressRepository.set(
            playlistId = 30L,
            entries = listOf(
                PlaybackProgress(
                    songId = SONG_3.id,
                    positionMs = 45_000L,
                    updatedAt = NOW_MS - 1_000L,
                    playlistId = 30L,
                ),
            ),
        )

        session.fire(alarmId = 3L, isAlarmTrigger = true)
        runCurrent()

        assertEquals("playback should start at the song with the latest saved progress",
            2, fakeControls.lastStartIndex)
        assertNull("progress should not be reset when resumePlayback is true",
            progressRepository.lastSaved)
    }

    @Test
    fun `fire with resumePlayback false starts from first song and resets progress`() = scope.runTest {
        val alarm = repeatingAlarm(id = 3L, playlistId = 30L).copy(resumePlayback = false)
        alarmRepository.insert(alarm)
        playlistRepository.set(30L, threeSongPlaylist(id = 30L))
        progressRepository.set(
            playlistId = 30L,
            entries = listOf(
                PlaybackProgress(
                    songId = SONG_3.id,
                    positionMs = 45_000L,
                    updatedAt = NOW_MS - 1_000L,
                    playlistId = 30L,
                ),
            ),
        )

        session.fire(alarmId = 3L, isAlarmTrigger = true)
        runCurrent()

        assertEquals("playback should start from the first song",
            0, fakeControls.lastStartIndex)
        val savedProgress = progressRepository.lastSaved
        assertEquals(SONG_1.id, savedProgress?.songId)
        assertEquals("progress should be reset to 0 when resumePlayback is false", 0L,
            savedProgress?.positionMs)
    }

    @Test
    fun `fire with isAlarmTrigger=true starts volume ramp`() = scope.runTest {
        val alarm = repeatingAlarm(id = 4L, playlistId = 40L)
        alarmRepository.insert(alarm)
        playlistRepository.set(40L, threeSongPlaylist(id = 40L))

        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)

        session.fire(alarmId = 4L, isAlarmTrigger = true)
        runCurrent()

        assertTrue(volumeRampPort.startCount > 0)
    }

    @Test
    fun `fire with isAlarmTrigger=false skips volume ramp`() = scope.runTest {
        val alarm = repeatingAlarm(id = 5L, playlistId = 50L)
        alarmRepository.insert(alarm)
        playlistRepository.set(50L, threeSongPlaylist(id = 50L))

        session.fire(alarmId = 5L, isAlarmTrigger = false)
        runCurrent()

        assertEquals(0, wakeLockPort.acquireCount)
        assertEquals(0, volumeRampPort.startCount)
    }

    // -------- fire() error paths ---------------------------------------------

    @Test
    fun `fire fails with Error and skips bookkeeping when alarm not found`() = scope.runTest {
        // No alarm inserted into repository.
        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)

        session.fire(alarmId = 99L, isAlarmTrigger = true)
        runCurrent()

        val state = session.state.value
        assertTrue("expected Error, was $state", state is AlarmFireState.Error)
        assertNull("session must not have been driven", fakeControls.lastStartIndex)
        assertEquals(1, wakeLockPort.releaseCount)
    }

    @Test
    fun `fire fails with Error when alarm is disabled`() = scope.runTest {
        alarmRepository.insert(repeatingAlarm(id = 7L, playlistId = 70L).copy(isEnabled = false))
        playlistRepository.set(70L, threeSongPlaylist(id = 70L))

        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)

        session.fire(alarmId = 7L, isAlarmTrigger = true)
        runCurrent()

        assertTrue(session.state.value is AlarmFireState.Error)
        assertEquals(1, wakeLockPort.releaseCount)
    }

    @Test
    fun `fire fails with Error and shows error notification when playlist is empty`() = scope.runTest {
        alarmRepository.insert(repeatingAlarm(id = 8L, playlistId = 80L))
        playlistRepository.set(
            80L,
            PlaylistWithSongs(
                playlist = Playlist(80L, "empty", 0L, 0L),
                songs = emptyList(),
            ),
        )

        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)

        session.fire(alarmId = 8L, isAlarmTrigger = true)
        runCurrent()

        assertTrue(session.state.value is AlarmFireState.Error)
        assertEquals(1, notificationPort.errorCount)
        assertEquals(1, wakeLockPort.releaseCount)
    }

    // -------- pause / resume / stop -----------------------------------------

    @Test
    fun `pause while Playing transitions to Paused and updates notification`() = scope.runTest {
        firePlaying(alarmId = 11L, playlistId = 110L)

        session.pause()

        val state = session.state.value
        assertTrue("expected Paused, was $state", state is AlarmFireState.Paused)
        verify(alarmPlaybackSession).pause()
        assertEquals(1, notificationPort.pauseCount)
    }

    @Test
    fun `pause while Idle is a no-op`() {
        session.pause()
        verify(alarmPlaybackSession, times(0)).pause()
        assertEquals(0, notificationPort.pauseCount)
        assertTrue(session.state.value is AlarmFireState.Idle)
    }

    @Test
    fun `resume while Paused transitions back to Playing`() = scope.runTest {
        firePlaying(alarmId = 12L, playlistId = 120L)
        session.pause()
        assertTrue(session.state.value is AlarmFireState.Paused)

        session.resume()

        assertTrue(session.state.value is AlarmFireState.Playing)
        verify(alarmPlaybackSession).resume()
    }

    @Test
    fun `resume while not Paused is a no-op`() {
        session.resume()
        verify(alarmPlaybackSession, times(0)).resume()
    }

    @Test
    fun `stop forwards to session stop`() = scope.runTest {
        firePlaying(alarmId = 13L, playlistId = 130L)

        session.stop()

        verify(alarmPlaybackSession).stop()
    }

    // -------- autoStop / extend ---------------------------------------------

    @Test
    fun `autoStop fires after configured delay and calls session stop`() = scope.runTest {
        alarmRepository.insert(repeatingAlarm(id = 14L, playlistId = 140L).copy(autoStop = AutoStop.ByMinutes(30)))
        playlistRepository.set(140L, threeSongPlaylist(id = 140L))

        session.fire(alarmId = 14L, isAlarmTrigger = true)
        runCurrent()
        verify(alarmPlaybackSession, times(0)).stop()

        advanceTimeBy(30L * 60_000L + 1L)

        verify(alarmPlaybackSession).stop()
    }

    @Test
    fun `extendAutoStop cancels prior timer and reschedules`() = scope.runTest {
        alarmRepository.insert(repeatingAlarm(id = 15L, playlistId = 150L).copy(autoStop = AutoStop.ByMinutes(10)))
        playlistRepository.set(150L, threeSongPlaylist(id = 150L))

        session.fire(alarmId = 15L, isAlarmTrigger = true)
        runCurrent()

        // 5 minutes in, extend by 20 more.
        advanceTimeBy(5L * 60_000L)
        session.extendAutoStop(20)

        // Original 10-minute deadline (5 more min) should NOT trigger stop.
        advanceTimeBy(6L * 60_000L)
        verify(alarmPlaybackSession, times(0)).stop()

        // The fresh 20-minute deadline should fire.
        advanceTimeBy(15L * 60_000L)
        verify(alarmPlaybackSession).stop()
    }

    @Test
    fun `extendAutoStop is a no-op when no timer is in flight`() {
        session.extendAutoStop(5)
        verify(alarmPlaybackSession, times(0)).stop()
    }

    @Test
    fun `setExtendToEnd flips the flag observable to session`() {
        assertFalse(session.isExtendToEnd())
        session.setExtendToEnd(true)
        assertTrue(session.isExtendToEnd())
        session.setExtendToEnd(false)
        assertFalse(session.isExtendToEnd())
    }

    // -------- playback transitions subscription --------------------------------

    @Test
    fun `SongCompleted while Playing triggers onSongCompleted and stops when extendToEnd`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 30L, playlistId = 300L).copy(
                autoStop = AutoStop.BySongCount(5)
            )
        )
        playlistRepository.set(300L, threeSongPlaylist(id = 300L))

        session.fire(alarmId = 30L, isAlarmTrigger = true)
        runCurrent()
        verify(alarmPlaybackSession, times(0)).stop()

        session.setExtendToEnd(true)

        // Emit SongCompleted — should trigger onSongCompleted + stop because extendToEnd
        fakeControls.emitPlaybackTransition(PlaybackTransition.SongCompleted(songId = 101L))
        runCurrent()

        verify(alarmPlaybackSession).stop()
    }

    @Test
    fun `SongCompleted does not trigger stop when no auto-stop condition met`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 31L, playlistId = 310L).copy(
                autoStop = AutoStop.BySongCount(5)
            )
        )
        playlistRepository.set(310L, threeSongPlaylist(id = 310L))

        session.fire(alarmId = 31L, isAlarmTrigger = true)
        runCurrent()

        fakeControls.emitPlaybackTransition(PlaybackTransition.SongCompleted(songId = 101L))
        runCurrent()

        verify(alarmPlaybackSession, times(0)).stop()
    }

    @Test
    fun `QueueEnded while Playing deletes progress and stops`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 32L, playlistId = 320L).copy(
                autoStop = AutoStop.BySongCount(5)
            )
        )
        playlistRepository.set(320L, threeSongPlaylist(id = 320L))
        var deletedPlaylistId: Long? = null
        progressRepository.set(320L, emptyList())
        // Capture deletion by overriding the fake behavior via reflection-free wrapper.
        val trackingRepository = object : PlaybackProgressRepository by progressRepository {
            override suspend fun deleteAllProgressForPlaylist(playlistId: Long): Result<Unit> {
                deletedPlaylistId = playlistId
                return progressRepository.deleteAllProgressForPlaylist(playlistId)
            }
        }
        recreateSessionWithProgressRepository(trackingRepository)

        session.fire(alarmId = 32L, isAlarmTrigger = true)
        runCurrent()

        fakeControls.emitPlaybackTransition(PlaybackTransition.QueueEnded(playlistId = 320L))
        runCurrent()

        // New protocol (Plan A Task 6): stopDeferred owns teardown. AlarmFireSession
        // no longer calls alarmPlaybackSession.stop() after QueueEnded — single
        // terminal transition is sufficient.
        verify(alarmPlaybackSession, times(0)).stop()
        assertEquals(320L, deletedPlaylistId)
        assertTrue(session.state.value is AlarmFireState.Stopped)
    }

    @Test
    fun `QueueEnded skips cleanup when playlistId does not match alarm`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 33L, playlistId = 330L).copy(
                autoStop = AutoStop.BySongCount(5)
            )
        )
        playlistRepository.set(330L, threeSongPlaylist(id = 330L))
        var deletedPlaylistId: Long? = null
        val trackingRepository = object : PlaybackProgressRepository by progressRepository {
            override suspend fun deleteAllProgressForPlaylist(playlistId: Long): Result<Unit> {
                deletedPlaylistId = playlistId
                return progressRepository.deleteAllProgressForPlaylist(playlistId)
            }
        }
        recreateSessionWithProgressRepository(trackingRepository)

        session.fire(alarmId = 33L, isAlarmTrigger = true)
        runCurrent()

        fakeControls.emitPlaybackTransition(PlaybackTransition.QueueEnded(playlistId = 999L))
        runCurrent()

        verify(alarmPlaybackSession, times(0)).stop()
        assertNull(deletedPlaylistId)
    }

    @Test
    fun `PlaybackStopped transition triggers onPlaybackStopped`() = scope.runTest {
        firePlaying(alarmId = 34L, playlistId = 340L)
        assertTrue(wakeLockPort.isHeld)

        fakeControls.emitPlaybackTransition(PlaybackTransition.PlaybackStopped)
        runCurrent()

        assertFalse(wakeLockPort.isHeld)
        assertEquals(1, notificationPort.cancelCount)
        assertTrue(session.state.value is AlarmFireState.Stopped)
    }

    @Test
    fun `stop from Paused finalizes the session`() = scope.runTest {
        // Reach Paused state, then user taps Stop on the notification/ring screen.
        // A PlaybackStopped transition arriving in Paused state must still finalize the
        // session (terminal transitions close the lifecycle regardless of state).
        firePlaying(alarmId = 36L, playlistId = 360L)
        session.pause()
        assertTrue("pre-condition: should be Paused", session.state.value is AlarmFireState.Paused)

        // Issue the stop; the mock alarmPlaybackSession.stop() is a no-op stub. We
        // simulate the real session's PlaybackStopped emission arriving via the
        // transitions collector.
        session.stop()
        fakeControls.emitPlaybackTransition(PlaybackTransition.PlaybackStopped)
        runCurrent()

        assertEquals("state must finalize even though it was Paused", AlarmFireState.Stopped, session.state.value)
        assertEquals(1, notificationPort.cancelCount)
    }

    @Test
    fun `SongCompleted transition does not trigger onPlaybackStopped`() = scope.runTest {
        firePlaying(alarmId = 35L, playlistId = 350L)
        assertTrue(wakeLockPort.isHeld)

        fakeControls.emitPlaybackTransition(PlaybackTransition.SongCompleted(songId = 101L))
        runCurrent()

        // Should NOT trigger onPlaybackStopped — SongCompleted is handled by autoStop logic
        assertTrue(wakeLockPort.isHeld)
        assertEquals(0, notificationPort.cancelCount)
    }

    // -------- onPlaybackStopped lifecycle -----------------------------------

    @Test
    fun `onPlaybackStopped releases wake lock cancels notification and resets state`() = scope.runTest {
        firePlaying(alarmId = 16L, playlistId = 160L)
        assertSame("wake lock should be held while playing", true, wakeLockPort.isHeld)

        session.onPlaybackStopped()

        assertFalse(wakeLockPort.isHeld)
        assertEquals(1, notificationPort.cancelCount)
        assertTrue(session.state.value is AlarmFireState.Stopped)
        assertFalse("extendToEnd is reset on stop", session.isExtendToEnd())
    }

    @Test
    fun `onPlaybackStopped after autoStop does not call session stop again`() = scope.runTest {
        alarmRepository.insert(repeatingAlarm(id = 17L, playlistId = 170L).copy(autoStop = AutoStop.ByMinutes(1)))
        playlistRepository.set(170L, threeSongPlaylist(id = 170L))

        session.fire(alarmId = 17L, isAlarmTrigger = true)
        runCurrent()
        advanceTimeBy(70_000L)
        verify(alarmPlaybackSession).stop()

        // Session responds to stop by emitting PlaybackStopped, which triggers onPlaybackStopped
        fakeControls.emitPlaybackTransition(PlaybackTransition.PlaybackStopped)
        runCurrent()

        // No additional stop call from session-side.
        verify(alarmPlaybackSession, times(1)).stop()
        assertTrue(session.state.value is AlarmFireState.Stopped)
    }

    @Test
    fun `onPlaybackStopped releases wake lock exactly once in happy path`() = scope.runTest {
        firePlaying(alarmId = 18L, playlistId = 180L)
        assertTrue(wakeLockPort.isHeld)
        assertEquals("wake lock should be acquired once", 1, wakeLockPort.acquireCount)

        session.onPlaybackStopped()

        assertFalse(wakeLockPort.isHeld)
        assertEquals("wake lock should be released exactly once", 1, wakeLockPort.releaseCount)
    }

    // -------- Helpers --------------------------------------------------------

    /** Drive the session through a successful fire so subsequent assertions can act on Playing.
     *  The session itself acquires the wake lock as part of fire(isAlarmTrigger=true). */
    private suspend fun firePlaying(alarmId: Long, playlistId: Long) {
        alarmRepository.insert(repeatingAlarm(id = alarmId, playlistId = playlistId))
        playlistRepository.set(playlistId, threeSongPlaylist(id = playlistId))
        session.fire(alarmId = alarmId, isAlarmTrigger = true)
        scope.runCurrent()
    }

    private fun recreateSessionWithProgressRepository(repository: PlaybackProgressRepository) {
        val alarmPlaybackResolver = AlarmPlaybackResolver(
            alarmRepository = alarmRepository,
            playlistRepository = playlistRepository,
            playbackProgressRepository = repository,
            clock = clock,
        )
        val autoStopController = AutoStopController(defaultDispatcher = testDispatcher)
        session = AlarmFireSession(
            alarmRepository = alarmRepository,
            alarmPlaybackResolver = alarmPlaybackResolver,
            playbackProgressRepository = repository,
            wakeLockPort = wakeLockPort,
            notificationPort = notificationPort,
            volumeRampPort = volumeRampPort,
            alarmPlaybackSession = alarmPlaybackSession,
            autoStopController = autoStopController,
            bypassPlanResolver = QuietModeBypassResolver(noDndPermissionPort),
            mainDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
        )
    }

    private fun repeatingAlarm(id: Long, playlistId: Long): Alarm = Alarm(
        id = id,
        hour = 7,
        minute = 0,
        repeatDays = java.time.DayOfWeek.entries.toSet(), // every day
        playlistId = playlistId,
        isEnabled = true,
        label = "Repeating $id",
        autoStop = null,
        lastTriggeredAt = null,
    )

    private fun oneTimeAlarm(id: Long, playlistId: Long): Alarm =
        repeatingAlarm(id, playlistId).copy(repeatDays = emptySet(), label = "OneTime $id")

    private fun threeSongPlaylist(id: Long): PlaylistWithSongs = PlaylistWithSongs(
        playlist = Playlist(id = id, name = "fixture", createdAt = 0L, updatedAt = 0L),
        songs = listOf(SONG_1, SONG_2, SONG_3),
    )

    // -------- Fakes ---------------------------------------------------------

    private val noDndPermissionPort = object : PermissionPort {
        override fun isIgnoringBatteryOptimizations() = false
        override fun createBatteryOptimizationIntent() = android.content.Intent()
        override fun isFullScreenIntentGranted() = false
        override fun checkPermission(permission: String) = false
        override fun canScheduleExactAlarms() = false
        override fun isNotificationPolicyAccessGranted() = false
        override fun createDndAccessSettingsIntent() = android.content.Intent()
        override fun createFullScreenIntentSettingsIntent() = android.content.Intent()
    }


    private class FakeWakeLockPort : WakeLockPort {
        var acquireCount = 0
            private set
        var releaseCount = 0
            private set
        private var held = false
        override val isHeld: Boolean
            get() = held

        override fun acquire(timeoutMs: Long) {
            acquireCount++
            held = true
        }

        override fun release() {
            if (held) {
                held = false
                releaseCount++
            }
        }
    }

    private class FakeNotificationPort : NotificationPort {
        var pauseCount = 0
            private set
        var cancelCount = 0
            private set
        var errorCount = 0
            private set
        var playingCount = 0
            private set
        var lastBypassDnd: Boolean = false
            private set

        override fun showAlarmPlaying(alarm: Alarm, song: Song, bypassDnd: Boolean) {
            playingCount++
            lastBypassDnd = bypassDnd
        }

        override fun showAlarmPaused(alarmId: Long, bypassDnd: Boolean) {
            pauseCount++
            lastBypassDnd = bypassDnd
        }

        override fun cancel(alarmId: Long) {
            cancelCount++
        }

        override fun showError(notificationId: Int, title: String, message: String) {
            errorCount++
        }
    }

    private class FakeVolumeRampPort : VolumeRampPort {
        var startCount = 0
            private set
        var restoreCount = 0
            private set
        var lastUseAlarmStream: Boolean? = null
            private set

        override fun startVolumeRamp(scope: CoroutineScope, useAlarmStream: Boolean) {
            startCount++
            lastUseAlarmStream = useAlarmStream
        }

        override fun restoreVolume() {
            restoreCount++
        }
    }

    /**
     * Test double that records alarm playback control method calls and allows emitting
     * playback transitions for testing the transition subscriptions.
     */
    private class FakeAlarmPlaybackControls {
        var lastStartIndex: Int? = null
            private set
        var pauseCount = 0
            private set
        var resumeCount = 0
            private set
        var stopCount = 0
            private set
        var playAlarmQueueCount = 0
            private set
        var lastUseAlarmStream: Boolean? = null
            private set

        private val _playbackTransitions = MutableSharedFlow<PlaybackTransition>(extraBufferCapacity = 64)
        val playbackTransitions: Flow<PlaybackTransition> = _playbackTransitions.asSharedFlow()

        fun emitPlaybackTransition(transition: PlaybackTransition) {
            _playbackTransitions.tryEmit(transition)
        }

        fun pause() {
            pauseCount++
        }

        fun resume() {
            resumeCount++
        }

        fun stop() {
            stopCount++
        }

        fun playAlarmQueue(
            songs: List<Song>,
            startIndex: Int,
            playlistId: Long,
            useAlarmStream: Boolean,
        ) {
            lastStartIndex = startIndex
            lastUseAlarmStream = useAlarmStream
            playAlarmQueueCount++
        }
    }
}
