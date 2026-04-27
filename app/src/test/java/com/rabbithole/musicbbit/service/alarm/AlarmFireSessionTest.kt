package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.model.PlaylistWithSongs
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.service.AlarmScheduler
import com.rabbithole.musicbbit.service.alarm.ports.NotificationPort
import com.rabbithole.musicbbit.service.alarm.ports.VolumeRampPort
import com.rabbithole.musicbbit.service.alarm.ports.WakeLockPort
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Test

/**
 * JVM unit tests for [AlarmFireSession].
 *
 * Covers the alarm-fire surface that used to be sprinkled across AlarmReceiver,
 * MusicPlaybackService.handlePlayAlarm, AlarmActionReceiver, and AlarmRingViewModel:
 *
 *   - fire(repeating) keeps isEnabled = true and reschedules
 *   - fire(one-time) flips isEnabled = false and does NOT reschedule
 *   - fire resolves the start index from saved progress and resets it
 *   - fire short-circuits to Error when the host is unbound, the alarm is missing,
 *     or the playlist is empty (and bookkeeping does NOT run in those cases)
 *   - pause / resume gate on state and update notification + transition to Paused / Playing
 *   - autoStop calls host.stopPlayback after the configured delay
 *   - extendAutoStop cancels the prior timer and starts a fresh one
 *   - onPlaybackStopped releases the wake lock, cancels the notification, and resets state
 *
 * All tests use [UnconfinedTestDispatcher] sharing one [TestScope] scheduler so both the
 * injected main and IO dispatchers participate in virtual time. This lets the autoStop
 * delay be advanced deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmFireSessionTest {

    private val scope = TestScope()
    private val testDispatcher = UnconfinedTestDispatcher(scope.testScheduler)

    private lateinit var alarmDao: FakeAlarmDao
    private lateinit var playlistRepository: FakePlaylistRepository
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var wakeLockPort: FakeWakeLockPort
    private lateinit var notificationPort: FakeNotificationPort
    private lateinit var volumeRampPort: FakeVolumeRampPort
    private lateinit var clock: FakeClock
    private lateinit var host: FakeAlarmPlaybackHost

    private lateinit var session: AlarmFireSession

    @Before
    fun setUp() {
        alarmDao = FakeAlarmDao()
        playlistRepository = FakePlaylistRepository()
        progressRepository = FakeProgressRepository()
        // AlarmScheduler is a final class with a Context dependency; mockk handles the
        // suspend `schedule` cleanly without invoking the real constructor.
        alarmScheduler = mockk(relaxed = true)
        wakeLockPort = FakeWakeLockPort()
        notificationPort = FakeNotificationPort()
        volumeRampPort = FakeVolumeRampPort()
        clock = FakeClock(NOW_MS)
        host = FakeAlarmPlaybackHost()

        session = AlarmFireSession(
            alarmDao = alarmDao,
            playlistRepository = playlistRepository,
            playbackProgressRepository = progressRepository,
            alarmScheduler = alarmScheduler,
            wakeLockPort = wakeLockPort,
            notificationPort = notificationPort,
            volumeRampPort = volumeRampPort,
            clock = clock,
            mainDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
        )
        session.bindHost(host)
    }

    // -------- fire() success paths --------------------------------------------

    @Test
    fun `fire repeating alarm keeps isEnabled true and reschedules`() = scope.runTest {
        val alarm = repeatingAlarm(id = 1L, playlistId = 10L)
        alarmDao.upsert(alarm)
        playlistRepository.set(10L, threeSongPlaylist(id = 10L))

        session.fire(alarmId = 1L, isAlarmTrigger = true)
        runCurrent()

        val state = session.state.value
        assertTrue("expected Playing, was $state", state is AlarmFireState.Playing)
        assertEquals(1L, (state as AlarmFireState.Playing).alarmId)
        assertEquals(SONG_1.title, state.currentSong?.title)

        val updated = alarmDao.getById(1L)
        assertTrue("repeating alarm must remain enabled", updated!!.isEnabled)
        assertEquals(NOW_MS, updated.lastTriggeredAt)
        coVerify(exactly = 1) { alarmScheduler.schedule(updated) }
    }

    @Test
    fun `fire one-time alarm flips isEnabled false and does NOT reschedule`() = scope.runTest {
        val alarm = oneTimeAlarm(id = 2L, playlistId = 20L)
        alarmDao.upsert(alarm)
        playlistRepository.set(20L, threeSongPlaylist(id = 20L))

        session.fire(alarmId = 2L, isAlarmTrigger = true)
        runCurrent()

        val updated = alarmDao.getById(2L)
        assertFalse("one-time alarm must be disabled after firing", updated!!.isEnabled)
        coVerify(exactly = 0) { alarmScheduler.schedule(any()) }
    }

    @Test
    fun `fire resolves start index from saved progress and resets it to zero`() = scope.runTest {
        val alarm = repeatingAlarm(id = 3L, playlistId = 30L)
        alarmDao.upsert(alarm)
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
            2, host.lastStartIndex)
        val savedProgress = progressRepository.lastSaved
        assertEquals(SONG_3.id, savedProgress?.songId)
        assertEquals("progress for the resumed song should be reset to 0", 0L,
            savedProgress?.positionMs)
    }

    @Test
    fun `fire with isAlarmTrigger=true starts volume ramp`() = scope.runTest {
        val alarm = repeatingAlarm(id = 4L, playlistId = 40L)
        alarmDao.upsert(alarm)
        playlistRepository.set(40L, threeSongPlaylist(id = 40L))

        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)

        session.fire(alarmId = 4L, isAlarmTrigger = true)
        runCurrent()

        assertTrue(volumeRampPort.startCount > 0)
        assertEquals("preloaded the first song's URI", SONG_1.path, host.lastPreloadUri)
    }

    @Test
    fun `fire with isAlarmTrigger=false skips volume ramp`() = scope.runTest {
        val alarm = repeatingAlarm(id = 5L, playlistId = 50L)
        alarmDao.upsert(alarm)
        playlistRepository.set(50L, threeSongPlaylist(id = 50L))

        session.fire(alarmId = 5L, isAlarmTrigger = false)
        runCurrent()

        assertEquals(0, wakeLockPort.acquireCount)
        assertEquals(0, volumeRampPort.startCount)
        assertNull(host.lastPreloadUri)
    }

    // -------- fire() error paths ---------------------------------------------

    @Test
    fun `fire fails with Error and skips bookkeeping when alarm not found`() = scope.runTest {
        // No alarm inserted into DAO.
        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)

        session.fire(alarmId = 99L, isAlarmTrigger = true)
        runCurrent()

        val state = session.state.value
        assertTrue("expected Error, was $state", state is AlarmFireState.Error)
        assertNull("host must not have been driven", host.lastStartIndex)
        assertEquals(1, wakeLockPort.releaseCount)
        coVerify(exactly = 0) { alarmScheduler.schedule(any()) }
    }

    @Test
    fun `fire fails with Error when alarm is disabled`() = scope.runTest {
        alarmDao.upsert(repeatingAlarm(id = 7L, playlistId = 70L).copy(isEnabled = false))
        playlistRepository.set(70L, threeSongPlaylist(id = 70L))

        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)

        session.fire(alarmId = 7L, isAlarmTrigger = true)
        runCurrent()

        assertTrue(session.state.value is AlarmFireState.Error)
        assertEquals(1, wakeLockPort.releaseCount)
        coVerify(exactly = 0) { alarmScheduler.schedule(any()) }
    }

    @Test
    fun `fire fails with Error and shows error notification when playlist is empty`() = scope.runTest {
        alarmDao.upsert(repeatingAlarm(id = 8L, playlistId = 80L))
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
        coVerify(exactly = 0) { alarmScheduler.schedule(any()) }
    }

    @Test
    fun `fire fails with Error when no host is bound and skips bookkeeping`() = scope.runTest {
        session.unbindHost(host)
        alarmDao.upsert(repeatingAlarm(id = 9L, playlistId = 90L))
        playlistRepository.set(90L, threeSongPlaylist(id = 90L))

        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)

        session.fire(alarmId = 9L, isAlarmTrigger = true)
        runCurrent()

        val state = session.state.value
        assertTrue("expected Error when host is null, was $state", state is AlarmFireState.Error)
        assertEquals(1, wakeLockPort.releaseCount)
        // Bookkeeping must not run if playback never started.
        val updated = alarmDao.getById(9L)
        assertNull("lastTriggeredAt must NOT be written when playback never starts",
            updated?.lastTriggeredAt)
        coVerify(exactly = 0) { alarmScheduler.schedule(any()) }
    }

    // -------- pause / resume / stop -----------------------------------------

    @Test
    fun `pause while Playing transitions to Paused and updates notification`() = scope.runTest {
        firePlaying(alarmId = 11L, playlistId = 110L)

        session.pause()

        val state = session.state.value
        assertTrue("expected Paused, was $state", state is AlarmFireState.Paused)
        assertEquals(1, host.pauseCount)
        assertEquals(1, notificationPort.pauseCount)
    }

    @Test
    fun `pause while Idle is a no-op`() {
        session.pause()
        assertEquals(0, host.pauseCount)
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
        assertEquals(1, host.resumeCount)
    }

    @Test
    fun `resume while not Paused is a no-op`() {
        session.resume()
        assertEquals(0, host.resumeCount)
    }

    @Test
    fun `stop forwards to host stopPlayback`() = scope.runTest {
        firePlaying(alarmId = 13L, playlistId = 130L)

        session.stop()

        assertEquals(1, host.stopCount)
    }

    // -------- autoStop / extend ---------------------------------------------

    @Test
    fun `autoStop fires after configured delay and calls host stopPlayback`() = scope.runTest {
        alarmDao.upsert(repeatingAlarm(id = 14L, playlistId = 140L).copy(autoStopMinutes = 30))
        playlistRepository.set(140L, threeSongPlaylist(id = 140L))

        session.fire(alarmId = 14L, isAlarmTrigger = true)
        runCurrent()
        assertEquals("autoStop should not fire before the delay elapses", 0, host.stopCount)

        advanceTimeBy(30L * 60_000L + 1L)

        assertEquals(1, host.stopCount)
    }

    @Test
    fun `extendAutoStop cancels prior timer and reschedules`() = scope.runTest {
        alarmDao.upsert(repeatingAlarm(id = 15L, playlistId = 150L).copy(autoStopMinutes = 10))
        playlistRepository.set(150L, threeSongPlaylist(id = 150L))

        session.fire(alarmId = 15L, isAlarmTrigger = true)
        runCurrent()

        // 5 minutes in, extend by 20 more.
        advanceTimeBy(5L * 60_000L)
        session.extendAutoStop(20)

        // Original 10-minute deadline (5 more min) should NOT trigger stop.
        advanceTimeBy(6L * 60_000L)
        assertEquals(0, host.stopCount)

        // The fresh 20-minute deadline should fire.
        advanceTimeBy(15L * 60_000L)
        assertEquals(1, host.stopCount)
    }

    @Test
    fun `extendAutoStop is a no-op when no timer is in flight`() {
        session.extendAutoStop(5)
        assertEquals(0, host.stopCount)
    }

    @Test
    fun `setExtendToEnd flips the flag observable to host`() {
        assertFalse(session.isExtendToEnd())
        session.setExtendToEnd(true)
        assertTrue(session.isExtendToEnd())
        session.setExtendToEnd(false)
        assertFalse(session.isExtendToEnd())
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
    fun `onPlaybackStopped after autoStop does not call host stopPlayback again`() = scope.runTest {
        alarmDao.upsert(repeatingAlarm(id = 17L, playlistId = 170L).copy(autoStopMinutes = 1))
        playlistRepository.set(170L, threeSongPlaylist(id = 170L))

        session.fire(alarmId = 17L, isAlarmTrigger = true)
        runCurrent()
        advanceTimeBy(70_000L)
        assertEquals(1, host.stopCount)

        // Host responds to its stopPlayback by calling onPlaybackStopped — should be safe.
        session.onPlaybackStopped()
        // No additional stop call from session-side.
        assertEquals(1, host.stopCount)
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
        alarmDao.upsert(repeatingAlarm(id = alarmId, playlistId = playlistId))
        playlistRepository.set(playlistId, threeSongPlaylist(id = playlistId))
        // Simulate Service having already acquired the wake lock
        wakeLockPort.acquire(10 * 60 * 1000L)
        session.fire(alarmId = alarmId, isAlarmTrigger = true)
        scope.runCurrent()
    }

    private fun repeatingAlarm(id: Long, playlistId: Long): AlarmEntity = AlarmEntity(
        id = id,
        hour = 7,
        minute = 0,
        repeatDaysBitmask = 0x7F, // every day
        playlistId = playlistId,
        isEnabled = true,
        label = "Repeating $id",
        autoStopMinutes = null,
        lastTriggeredAt = null,
    )

    private fun oneTimeAlarm(id: Long, playlistId: Long): AlarmEntity =
        repeatingAlarm(id, playlistId).copy(repeatDaysBitmask = 0, label = "OneTime $id")

    private fun threeSongPlaylist(id: Long): PlaylistWithSongs = PlaylistWithSongs(
        playlist = Playlist(id = id, name = "fixture", createdAt = 0L, updatedAt = 0L),
        songs = listOf(SONG_1, SONG_2, SONG_3),
    )

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
    }

    // -------- Fakes ---------------------------------------------------------

    /**
     * Minimal in-memory [AlarmDao]. Only the methods the session touches are real;
     * the rest throw because the session never calls them.
     */
    private class FakeAlarmDao : AlarmDao {
        private val rows = mutableMapOf<Long, AlarmEntity>()

        fun upsert(alarm: AlarmEntity) {
            rows[alarm.id] = alarm
        }

        override suspend fun insert(alarm: AlarmEntity): Long {
            rows[alarm.id] = alarm
            return alarm.id
        }

        override suspend fun update(alarm: AlarmEntity) {
            rows[alarm.id] = alarm
        }

        override suspend fun delete(alarm: AlarmEntity) {
            rows.remove(alarm.id)
        }

        override fun getAll(): Flow<List<AlarmEntity>> = flowOf(rows.values.toList())

        override fun getEnabledAlarms(): Flow<List<AlarmEntity>> =
            flowOf(rows.values.filter { it.isEnabled })

        override suspend fun getById(id: Long): AlarmEntity? = rows[id]
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
        override suspend fun addSongToPlaylist(playlistId: Long, songId: Long, sortOrder: Int): Result<Unit> =
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

        override fun showAlarmPlaying(alarm: AlarmEntity, song: Song) {
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

    private class FakeAlarmPlaybackHost : AlarmPlaybackHost {
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

        override fun preloadFirstSong(uri: String) {
            lastPreloadUri = uri
        }

        override fun playAlarmQueue(
            songs: List<Song>,
            startIndex: Int,
            playlistId: Long,
            alarmId: Long
        ) {
            lastStartIndex = startIndex
            lastQueueAlarmId = alarmId
        }

        override fun pauseAlarm() {
            pauseCount++
        }

        override fun resumeAlarm() {
            resumeCount++
        }

        override fun stopPlayback() {
            stopCount++
        }
    }
}
