package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackSource
import com.rabbithole.musicbbit.service.PlaybackState
import com.rabbithole.musicbbit.service.alarm.ports.NotificationPort
import com.rabbithole.musicbbit.service.alarm.ports.VolumeRampPort
import com.rabbithole.musicbbit.service.alarm.ports.WakeLockPort
import com.rabbithole.musicbbit.service.playback.PlayerEvent
import com.rabbithole.musicbbit.service.playback.PlaybackController
import com.rabbithole.musicbbit.service.playback.TransitionReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
 *   - autoStop calls controller.stop after the configured delay
 *   - extendAutoStop cancels the prior timer and starts a fresh one
 *   - onPlaybackStopped releases the wake lock, cancels the notification, and resets state
 *   - playerEvents (MediaItemTransition, QueueEnded) drive onSongCompleted/onQueueEnded
 *   - playbackState changes (alarmId=null, isPlaying=false) trigger onPlaybackStopped
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
    private lateinit var playbackController: FakePlaybackController

    private lateinit var session: AlarmFireSession

    companion object {
        private const val NOW_MS = 1_700_000_000_000L

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
        playbackController = FakePlaybackController()

        session = AlarmFireSession(
            alarmRepository = alarmRepository,
            playlistRepository = playlistRepository,
            playbackProgressRepository = progressRepository,
            wakeLockPort = wakeLockPort,
            notificationPort = notificationPort,
            volumeRampPort = volumeRampPort,
            playbackController = playbackController,
            clock = clock,
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
    fun `fire resolves start index from saved progress and resets it to zero`() = scope.runTest {
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
            2, playbackController.lastStartIndex)
        val savedProgress = progressRepository.lastSaved
        assertEquals(SONG_3.id, savedProgress?.songId)
        assertEquals("progress for the resumed song should be reset to 0", 0L,
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
        assertEquals("preloaded the first song's URI", SONG_1.path, playbackController.lastPreloadUri)
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
        assertNull(playbackController.lastPreloadUri)
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
        assertNull("controller must not have been driven", playbackController.lastStartIndex)
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
        assertEquals(1, playbackController.pauseCount)
        assertEquals(1, notificationPort.pauseCount)
    }

    @Test
    fun `pause while Idle is a no-op`() {
        session.pause()
        assertEquals(0, playbackController.pauseCount)
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
        assertEquals(1, playbackController.resumeCount)
    }

    @Test
    fun `resume while not Paused is a no-op`() {
        session.resume()
        assertEquals(0, playbackController.resumeCount)
    }

    @Test
    fun `stop forwards to controller stop`() = scope.runTest {
        firePlaying(alarmId = 13L, playlistId = 130L)

        session.stop()

        assertEquals(1, playbackController.stopCount)
    }

    // -------- autoStop / extend ---------------------------------------------

    @Test
    fun `autoStop fires after configured delay and calls controller stop`() = scope.runTest {
        alarmRepository.insert(repeatingAlarm(id = 14L, playlistId = 140L).copy(autoStop = AutoStop.ByMinutes(30)))
        playlistRepository.set(140L, threeSongPlaylist(id = 140L))

        session.fire(alarmId = 14L, isAlarmTrigger = true)
        runCurrent()
        assertEquals("autoStop should not fire before the delay elapses", 0, playbackController.stopCount)

        advanceTimeBy(30L * 60_000L + 1L)

        assertEquals(1, playbackController.stopCount)
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
        assertEquals(0, playbackController.stopCount)

        // The fresh 20-minute deadline should fire.
        advanceTimeBy(15L * 60_000L)
        assertEquals(1, playbackController.stopCount)
    }

    @Test
    fun `extendAutoStop is a no-op when no timer is in flight`() {
        session.extendAutoStop(5)
        assertEquals(0, playbackController.stopCount)
    }

    @Test
    fun `setExtendToEnd flips the flag observable to controller`() {
        assertFalse(session.isExtendToEnd())
        session.setExtendToEnd(true)
        assertTrue(session.isExtendToEnd())
        session.setExtendToEnd(false)
        assertFalse(session.isExtendToEnd())
    }

    // -------- song counter auto-stop ----------------------------------------

    @Test
    fun `fire with BySongCount(3) stops after 3 songs`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 19L, playlistId = 190L).copy(
                autoStop = AutoStop.BySongCount(3)
            )
        )
        playlistRepository.set(190L, threeSongPlaylist(id = 190L))

        session.fire(alarmId = 19L, isAlarmTrigger = true)
        runCurrent()
        assertEquals(0, playbackController.stopCount)

        // First song completed
        session.onSongCompleted()
        assertEquals(0, playbackController.stopCount)

        // Second song completed
        session.onSongCompleted()
        assertEquals(0, playbackController.stopCount)

        // Third song completed — counter reaches zero
        session.onSongCompleted()
        assertEquals(1, playbackController.stopCount)
    }

    @Test
    fun `fire with BySongCount(1) stops after first song`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 20L, playlistId = 200L).copy(
                autoStop = AutoStop.BySongCount(1)
            )
        )
        playlistRepository.set(200L, threeSongPlaylist(id = 200L))

        session.fire(alarmId = 20L, isAlarmTrigger = true)
        runCurrent()

        session.onSongCompleted()
        assertEquals(1, playbackController.stopCount)
    }

    @Test
    fun `onSongCompleted does nothing when no counter active`() = scope.runTest {
        firePlaying(alarmId = 21L, playlistId = 210L)

        session.onSongCompleted()

        assertEquals(0, playbackController.stopCount)
    }

    @Test
    fun `onQueueEnded stops when song counter active`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 22L, playlistId = 220L).copy(
                autoStop = AutoStop.BySongCount(5)
            )
        )
        playlistRepository.set(220L, threeSongPlaylist(id = 220L))

        session.fire(alarmId = 22L, isAlarmTrigger = true)
        runCurrent()

        // Queue ends before counter reaches zero
        session.onQueueEnded()
        assertEquals(1, playbackController.stopCount)
    }

    @Test
    fun `onPlaybackStopped resets song counter`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 23L, playlistId = 230L).copy(
                autoStop = AutoStop.BySongCount(2)
            )
        )
        playlistRepository.set(230L, threeSongPlaylist(id = 230L))

        session.fire(alarmId = 23L, isAlarmTrigger = true)
        runCurrent()

        session.onPlaybackStopped()

        // Counter should be reset; calling onSongCompleted should do nothing
        session.onSongCompleted()
        assertEquals(0, playbackController.stopCount)
    }

    @Test
    fun `transitionToError resets song counter`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 24L, playlistId = 240L).copy(
                autoStop = AutoStop.BySongCount(2)
            )
        )
        playlistRepository.set(240L, threeSongPlaylist(id = 240L))
        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)

        session.fire(alarmId = 24L, isAlarmTrigger = true)
        runCurrent()

        // Force an error by calling fire with a non-existent alarm
        session.fire(alarmId = 25L, isAlarmTrigger = true)
        runCurrent()

        assertTrue(session.state.value is AlarmFireState.Error)
        // Counter should be reset; calling onSongCompleted should do nothing
        session.onSongCompleted()
        assertEquals(0, playbackController.stopCount)
    }

    // -------- playerEvents subscription -------------------------------------

    @Test
    fun `MediaItemTransition AUTO while Playing triggers onSongCompleted and stops when extendToEnd`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 30L, playlistId = 300L).copy(
                autoStop = AutoStop.BySongCount(5)
            )
        )
        playlistRepository.set(300L, threeSongPlaylist(id = 300L))

        session.fire(alarmId = 30L, isAlarmTrigger = true)
        runCurrent()
        assertEquals(0, playbackController.stopCount)

        session.setExtendToEnd(true)

        // Emit AUTO MediaItemTransition — should trigger onSongCompleted + stop because extendToEnd
        playbackController.emitPlayerEvent(
            PlayerEvent.MediaItemTransition(itemTag = null, itemIndex = 1, reason = TransitionReason.AUTO)
        )
        runCurrent()

        assertEquals(1, playbackController.stopCount)
    }

    @Test
    fun `MediaItemTransition SEEK does not trigger onSongCompleted`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 31L, playlistId = 310L).copy(
                autoStop = AutoStop.BySongCount(1)
            )
        )
        playlistRepository.set(310L, threeSongPlaylist(id = 310L))

        session.fire(alarmId = 31L, isAlarmTrigger = true)
        runCurrent()

        // Emit SEEK MediaItemTransition — should NOT trigger onSongCompleted
        playbackController.emitPlayerEvent(
            PlayerEvent.MediaItemTransition(itemTag = null, itemIndex = 2, reason = TransitionReason.SEEK)
        )
        runCurrent()

        assertEquals(0, playbackController.stopCount)
    }

    @Test
    fun `QueueEnded while Playing triggers onQueueEnded`() = scope.runTest {
        alarmRepository.insert(
            repeatingAlarm(id = 32L, playlistId = 320L).copy(
                autoStop = AutoStop.BySongCount(5)
            )
        )
        playlistRepository.set(320L, threeSongPlaylist(id = 320L))

        session.fire(alarmId = 32L, isAlarmTrigger = true)
        runCurrent()

        playbackController.emitPlayerEvent(PlayerEvent.QueueEnded)
        runCurrent()

        assertEquals(1, playbackController.stopCount)
    }

    // -------- playbackState subscription ------------------------------------

    @Test
    fun `playbackState with alarmId null and not playing triggers onPlaybackStopped`() = scope.runTest {
        firePlaying(alarmId = 33L, playlistId = 330L)
        assertTrue(wakeLockPort.isHeld)

        // Simulate controller stopping: alarmId=null, isPlaying=false
        playbackController.setPlaybackState(PlaybackState(alarmId = null, isPlaying = false))
        runCurrent()

        assertFalse(wakeLockPort.isHeld)
        assertEquals(1, notificationPort.cancelCount)
        assertTrue(session.state.value is AlarmFireState.Stopped)
    }

    @Test
    fun `playbackState with alarmId set does not trigger onPlaybackStopped`() = scope.runTest {
        firePlaying(alarmId = 34L, playlistId = 340L)
        assertTrue(wakeLockPort.isHeld)

        // Simulate pause while alarm is active: alarmId still set, isPlaying=false
        playbackController.setPlaybackState(PlaybackState(alarmId = 34L, isPlaying = false))
        runCurrent()

        // Should NOT trigger onPlaybackStopped because alarmId is not null
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
    fun `onPlaybackStopped after autoStop does not call controller stop again`() = scope.runTest {
        alarmRepository.insert(repeatingAlarm(id = 17L, playlistId = 170L).copy(autoStop = AutoStop.ByMinutes(1)))
        playlistRepository.set(170L, threeSongPlaylist(id = 170L))

        session.fire(alarmId = 17L, isAlarmTrigger = true)
        runCurrent()
        advanceTimeBy(70_000L)
        assertEquals(1, playbackController.stopCount)

        // Controller responds to stop by resetting playbackState, which triggers onPlaybackStopped
        playbackController.setPlaybackState(PlaybackState(alarmId = null, isPlaying = false))
        runCurrent()

        // No additional stop call from session-side.
        assertEquals(1, playbackController.stopCount)
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
     *  Simulates the Service having already acquired the wake lock before calling fire(). */
    private suspend fun firePlaying(alarmId: Long, playlistId: Long) {
        alarmRepository.insert(repeatingAlarm(id = alarmId, playlistId = playlistId))
        playlistRepository.set(playlistId, threeSongPlaylist(id = playlistId))
        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)
        session.fire(alarmId = alarmId, isAlarmTrigger = true)
        scope.runCurrent()
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

    /**
     * Minimal in-memory [AlarmRepository]. Simulates `recordTriggered` with the same
     * logic as the real implementation: updates `lastTriggeredAt`, disables one-time alarms.
     */
    private class FakeAlarmRepository : AlarmRepository {
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

        override suspend fun recordTriggered(alarmId: Long) {
            val alarm = rows[alarmId] ?: return
            val isOneTime = alarm.repeatDays.isEmpty()
            rows[alarmId] = alarm.copy(
                lastTriggeredAt = NOW_MS,
                isEnabled = if (isOneTime) false else alarm.isEnabled,
            )
        }
    }

    /**
     * Minimal in-memory [PlaylistRepository]; only `getPlaylistWithSongs` is exercised by
     * the session, the rest throw because they are never invoked.
     */
    private class FakePlaylistRepository : PlaylistRepository {
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
    private class FakeProgressRepository : PlaybackProgressRepository {
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

        override fun showAlarmPlaying(alarm: Alarm, song: Song) {
            playingCount++
        }

        override fun showAlarmPaused(alarmId: Long) {
            pauseCount++
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

        override fun startVolumeRamp(scope: CoroutineScope) {
            startCount++
        }

        override fun restoreVolume() {
            restoreCount++
        }
    }

    private class FakeClock(private val fixed: Long) : Clock {
        override fun nowMs(): Long = fixed
    }

    /**
     * Fake [PlaybackController] that records all method calls and allows emitting
     * player events and playback state changes for testing the event subscriptions.
     */
    private class FakePlaybackController : PlaybackController {
        var lastPreloadUri: String? = null
            private set
        var lastStartIndex: Int? = null
            private set
        var lastQueueAlarmId: Long? = null
            private set
        var pauseCount = 0
            private set
        var resumeCount = 0
            private set
        var stopCount = 0
            private set
        var playCount = 0
            private set
        var playQueueCount = 0
            private set
        var nextCount = 0
            private set
        var previousCount = 0
            private set
        var seekCount = 0
            private set
        var setPlayModeCount = 0
            private set

        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        private val _playerEvents = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
        override val playerEvents: SharedFlow<PlayerEvent> = _playerEvents.asSharedFlow()

        fun setPlaybackState(state: PlaybackState) {
            _playbackState.value = state
        }

        fun emitPlayerEvent(event: PlayerEvent) {
            _playerEvents.tryEmit(event)
        }

        override fun play(song: Song, playlistId: Long) {
            playCount++
        }

        override fun playQueue(songs: List<Song>, startIndex: Int, playlistId: Long) {
            playQueueCount++
        }

        override fun pause() {
            pauseCount++
        }

        override fun resume() {
            resumeCount++
        }

        override fun next() {
            nextCount++
        }

        override fun previous() {
            previousCount++
        }

        override fun seekTo(positionMs: Long) {
            seekCount++
        }

        override fun stop() {
            stopCount++
            // Simulate real controller: stop resets playbackState to alarmId=null, isPlaying=false
            _playbackState.value = PlaybackState(alarmId = null, isPlaying = false)
        }

        override fun setPlayMode(mode: PlayMode) {
            setPlayModeCount++
        }

        override fun playAlarmQueue(
            songs: List<Song>,
            startIndex: Int,
            playlistId: Long,
            alarmId: Long
        ) {
            lastStartIndex = startIndex
            lastQueueAlarmId = alarmId
            // Simulate real controller: set alarmId and isPlaying=true
            _playbackState.value = PlaybackState(
                alarmId = alarmId,
                isPlaying = true,
                currentPlaylistId = playlistId,
                source = PlaybackSource.ALARM,
            )
        }

        override fun preloadFirstSong(uri: String) {
            lastPreloadUri = uri
        }
    }
}
