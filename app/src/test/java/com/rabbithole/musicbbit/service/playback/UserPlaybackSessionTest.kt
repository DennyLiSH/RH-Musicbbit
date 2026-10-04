package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackState
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import timber.log.Timber

/**
 * JVM unit tests for [UserPlaybackSession].
 *
 * Uses a dedicated [sessionDispatcher] (separate from any TestScope) so that
 * UserPlaybackSession's internal infinite loops (tickLoop, saveLoop) never
 * interfere with test finalisation.
 *
 * Event delivery is synchronous because [UnconfinedTestDispatcher] dispatches
 * eagerly — by the time `tryEmit()` returns the collector has already processed
 * the event.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserPlaybackSessionTest {

    // Separate dispatcher — its scheduler is NOT shared with any TestScope,
    // so runBlocking / runTest finalisation never tries to drain the infinite loops.
    private val sessionDispatcher = UnconfinedTestDispatcher()

    private lateinit var playerPort: FakePlayerPort
    private lateinit var playbackProgressRepository: PlaybackProgressRepository
    private lateinit var serviceStarter: ServiceStarter
    private lateinit var audioFocusPort: FakeAudioFocusPort
    private lateinit var playbackCoordinator: PlaybackCoordinator

    private val _playbackState = MutableStateFlow(PlaybackState())

    private lateinit var session: UserPlaybackSession

    /** Simulates the alarm session owning the shared player. */
    private val otherConsumer = object : PlaybackCoordinator.PlaybackConsumer {
        override fun onPlayerEvent(event: PlayerEvent) {}
        override fun onFocusLoss() {}
        override fun onFocusLossTransient() {}
        override fun onFocusGain() {}
        override fun onDeactivated() {}
    }

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
        private val SONG_2 = SONG_1.copy(id = 2L, path = "/tmp/song2.mp3", title = "Song Two")

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
        playerPort = FakePlayerPort()
        playbackProgressRepository = mock()
        serviceStarter = mock()
        audioFocusPort = FakeAudioFocusPort()
        playbackCoordinator = PlaybackCoordinator(
            playerPort = playerPort,
            audioFocusPort = audioFocusPort,
            mainDispatcher = sessionDispatcher,
        )
        wheneverBlocking { playbackProgressRepository.saveProgress(any()) } doReturn Result.success(Unit)

        session = UserPlaybackSession(
            playerPort = playerPort,
            audioStreamPort = FakeAudioStreamPort(),
            playbackProgressRepository = playbackProgressRepository,
            serviceStarter = serviceStarter,
            audioFocusPort = audioFocusPort,
            playbackCoordinator = playbackCoordinator,
            mainDispatcher = sessionDispatcher,
        )
    }

    @After
    fun tearDown() {
        session.close()
    }

    // -------- initial state ---------------------------------------------------

    @Test
    fun `initial state is empty`() {
        val state = session.playbackState.value
        assertFalse(state.isPlaying)
        assertNull(state.currentSong)
        assertEquals(-1L, state.currentPlaylistId)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertEquals(PlayMode.SEQUENTIAL, state.playMode)
        assertTrue(state.queue.isEmpty())
        assertEquals(0, state.queueIndex)
    }

    @Test
    fun `init does not ensure notification channel exists`() {
        // No longer assertable here — channel ensure migrated to ForegroundNotificationController.
        // Kept as placeholder; playback session no longer touches notification port.
    }

    // -------- play() ----------------------------------------------------------

    @Test
    fun `play requests focus activates coordinator starts service sets queue and plays`() {
        session.play(SONG_1, playlistId = 10L)

        assertEquals(1, audioFocusPort.requestFocusCallCount)
        verify(serviceStarter).startService()
        assertEquals(1, playerPort.queueCalls.size)
        assertEquals(1, playerPort.playCalls.size)

        val state = session.playbackState.value
        assertEquals(SONG_1, state.currentSong)
        assertEquals(10L, state.currentPlaylistId)
        assertEquals(listOf(SONG_1), state.queue)
        assertEquals(0, state.queueIndex)
    }

    @Test
    fun `play does nothing when focus request fails`() {
        audioFocusPort.setRequestFocusResult(false)

        session.play(SONG_1, playlistId = 10L)

        assertEquals(1, audioFocusPort.requestFocusCallCount)
        verify(serviceStarter, org.mockito.Mockito.never()).startService()
        assertEquals(0, playerPort.queueCalls.size)
        assertEquals(0, playerPort.playCalls.size)

        val state = session.playbackState.value
        assertNull(state.currentSong)
    }

    // -------- pause() ---------------------------------------------------------

    @Test
    fun `pause pauses the player`() {
        session.pause()

        assertEquals(1, playerPort.pauseCalls.size)
    }

    // -------- togglePlayPause() ----------------------------------------------

    @Test
    fun `togglePlayPause pauses when playing`() {
        // Drive the session into a state where isPlaying=true via the same path
        // the real player uses: a play() call followed by an IsPlayingChanged event.
        playerPort.isPlayingValue = true
        session.play(SONG_1, playlistId = 1L)
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        session.togglePlayPause()

        assertEquals(1, playerPort.pauseCalls.size)
    }

    @Test
    fun `togglePlayPause resumes when paused`() {
        playerPort.isPlayingValue = false

        session.togglePlayPause()

        assertEquals(1, audioFocusPort.requestFocusCallCount)
        assertEquals(1, playerPort.playCalls.size)
    }

    // -------- resume() --------------------------------------------------------

    @Test
    fun `resume requests focus and plays when not playing`() {
        playerPort.isPlayingValue = false

        session.resume()

        assertEquals(1, audioFocusPort.requestFocusCallCount)
        assertEquals(1, playerPort.playCalls.size)
    }

    @Test
    fun `resume does nothing when focus request fails`() {
        audioFocusPort.setRequestFocusResult(false)

        session.resume()

        assertEquals(1, audioFocusPort.requestFocusCallCount)
        assertEquals(0, playerPort.playCalls.size)
    }

    // -------- stop() ----------------------------------------------------------

    @Test
    fun `stop abandons focus stops player clears queue and stops service`() {
        session.play(SONG_1, playlistId = 10L)

        session.stop()

        assertEquals(1, audioFocusPort.abandonFocusCallCount)
        assertEquals(1, playerPort.stopCalls.size)
        assertEquals(1, playerPort.clearQueueCalls.size)
        verify(serviceStarter).stopService()

        val state = session.playbackState.value
        assertFalse(state.isPlaying)
        assertNull(state.currentSong)
        assertEquals(-1L, state.currentPlaylistId)
        assertTrue(state.queue.isEmpty())
        assertEquals(0, state.queueIndex)
    }

    // -------- PlayerEvent handling --------------------------------------------
    // tryEmit() delivers synchronously with UnconfinedTestDispatcher

    @Test
    fun `IsPlayingChanged true updates state and starts save loop`() {
        session.play(SONG_1, playlistId = 10L)

        playerPort.currentPositionMsValue = 5_000L
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        val state = session.playbackState.value
        assertTrue(state.isPlaying)
    }

    @Test
    fun `IsPlayingChanged false updates state`() {
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(false))

        val state = session.playbackState.value
        assertFalse(state.isPlaying)
    }

    @Test
    fun `MediaItemTransition updates current song and queueIndex`() {
        session.play(SONG_1, playlistId = 10L)

        playerPort.emitEvent(
            PlayerEvent.MediaItemTransition(
                itemTag = SONG_2,
                itemIndex = 1,
                reason = TransitionReason.AUTO,
            )
        )

        val state = session.playbackState.value
        assertEquals(SONG_2, state.currentSong)
        assertEquals(1, state.queueIndex)
        assertEquals(0L, state.positionMs)
    }

    @Test
    fun `PlaybackReady updates duration`() {
        session.play(SONG_1, playlistId = 10L)

        playerPort.emitEvent(PlayerEvent.PlaybackReady(durationMs = 200_000L))

        val state = session.playbackState.value
        assertEquals(200_000L, state.durationMs)
    }

    @Test
    fun `PositionDiscontinuity updates position and queueIndex`() {
        session.play(SONG_1, playlistId = 10L)

        playerPort.emitEvent(
            PlayerEvent.PositionDiscontinuity(newPositionMs = 30_000L, itemIndex = 2)
        )

        val state = session.playbackState.value
        assertEquals(30_000L, state.positionMs)
        assertEquals(2, state.queueIndex)
    }

    @Test
    fun `QueueEnded calls stop`() {
        session.play(SONG_1, playlistId = 10L)

        playerPort.emitEvent(PlayerEvent.QueueEnded)

        assertEquals(1, playerPort.stopCalls.size)
        assertEquals(1, playerPort.clearQueueCalls.size)
        verify(serviceStarter).stopService()
    }

    // -------- setPlayMode() ---------------------------------------------------

    @Test
    fun `setPlayMode RANDOM enables shuffle`() {
        session.setPlayMode(PlayMode.RANDOM)

        assertTrue(playerPort.lastShuffleEnabled)
        assertEquals(PlayerRepeatMode.OFF, playerPort.lastRepeatMode)
        assertEquals(PlayMode.RANDOM, session.playbackState.value.playMode)
    }

    @Test
    fun `setPlayMode REPEAT_ONE sets repeat one`() {
        session.setPlayMode(PlayMode.REPEAT_ONE)

        assertFalse(playerPort.lastShuffleEnabled)
        assertEquals(PlayerRepeatMode.ONE, playerPort.lastRepeatMode)
        assertEquals(PlayMode.REPEAT_ONE, session.playbackState.value.playMode)
    }

    @Test
    fun `setPlayMode SEQUENTIAL disables shuffle and repeat`() {
        session.setPlayMode(PlayMode.SEQUENTIAL)

        assertFalse(playerPort.lastShuffleEnabled)
        assertEquals(PlayerRepeatMode.OFF, playerPort.lastRepeatMode)
        assertEquals(PlayMode.SEQUENTIAL, session.playbackState.value.playMode)
    }

    // -------- next() / previous() ---------------------------------------------

    @Test
    fun `next delegates to playerPort when hasNext is true`() {
        playerPort.hasNextValue = true

        session.next()

        assertEquals(1, playerPort.nextCalls.size)
    }

    @Test
    fun `next does nothing when hasNext is false`() {
        playerPort.hasNextValue = false

        session.next()

        assertEquals(0, playerPort.nextCalls.size)
    }

    @Test
    fun `previous delegates to playerPort when hasPrevious is true`() {
        playerPort.hasPreviousValue = true

        session.previous()

        assertEquals(1, playerPort.previousCalls.size)
    }

    @Test
    fun `previous does nothing when hasPrevious is false`() {
        playerPort.hasPreviousValue = false

        session.previous()

        assertEquals(0, playerPort.previousCalls.size)
    }

    // -------- seekTo() --------------------------------------------------------

    @Test
    fun `seekTo delegates to playerPort and updates state`() {
        session.seekTo(45_000L)

        assertEquals(1, playerPort.seekCalls.size)
        assertEquals(45_000L, playerPort.seekCalls[0])
        assertEquals(45_000L, session.playbackState.value.positionMs)
    }

    // -------- playQueue() -----------------------------------------------------

    @Test
    fun `playQueue requests focus and plays queue`() = runBlocking {
        wheneverBlocking { playbackProgressRepository.getProgress(SONG_1.id, 10L) } doReturn Result.success(null)

        session.playQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 10L)

        assertEquals(1, audioFocusPort.requestFocusCallCount)
        verify(serviceStarter).startService()
        assertEquals(1, playerPort.queueCalls.size)
        assertEquals(0, playerPort.queueCalls[0].startIndex)

        val state = session.playbackState.value
        assertEquals(SONG_1, state.currentSong)
        assertEquals(10L, state.currentPlaylistId)
        assertEquals(listOf(SONG_1, SONG_2), state.queue)
        assertEquals(0, state.queueIndex)
    }

    @Test
    fun `playQueue restores progress when available`() = runBlocking {
        audioFocusPort.setRequestFocusResult(true)
        val progress = PlaybackProgress(
            songId = SONG_1.id,
            positionMs = 30_000L,
            updatedAt = 0L,
            playlistId = 10L,
        )
        wheneverBlocking { playbackProgressRepository.getProgress(SONG_1.id, 10L) } doReturn Result.success(progress)

        session.playQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 10L)

        assertTrue(playerPort.seekCalls.contains(30_000L))
    }

    @Test
    fun `playQueue returns early when focus request fails`() = runBlocking {
        audioFocusPort.setRequestFocusResult(false)
        wheneverBlocking { playbackProgressRepository.getProgress(SONG_1.id, 10L) } doReturn Result.success(null)

        session.playQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L)

        assertEquals(1, audioFocusPort.requestFocusCallCount)
        verify(serviceStarter, org.mockito.Mockito.never()).startService()
        assertEquals(0, playerPort.queueCalls.size)
        assertEquals(0, playerPort.playCalls.size)

        val state = session.playbackState.value
        assertNull(state.currentSong)
    }

    @Test
    fun `playQueue returns early for empty list`() {
        session.playQueue(emptyList(), startIndex = 0, playlistId = 10L)

        assertEquals(0, audioFocusPort.requestFocusCallCount)
        assertEquals(0, playerPort.queueCalls.size)
    }

    // -------- audio focus callbacks -------------------------------------------

    @Test
    fun `focus loss pauses playback when playing`() {
        session.play(SONG_1, playlistId = 10L)

        // Playback must actually be playing for focus loss to trigger pause
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        audioFocusPort.simulateFocusLoss()

        assertEquals(1, playerPort.pauseCalls.size)
    }

    @Test
    fun `focus gain resumes playback when previously paused by focus loss`() {
        playerPort.isPlayingValue = false
        session.play(SONG_1, playlistId = 10L)

        // Start playing
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))
        // Lose focus -> wasPausedByFocusLoss = true, pause()
        audioFocusPort.simulateFocusLoss()
        // Player reports it's no longer playing -> isPlaying = false
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(false))
        // Regain focus -> resume()
        audioFocusPort.simulateFocusGain()

        assertTrue(playerPort.playCalls.size >= 1)
    }

    // -------- coordinator activation ------------------------------------------

    @Test
    fun `session activates itself on play and deactivates on stop`() {
        session.play(SONG_1, playlistId = 10L)

        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))
        // Event should be handled by this session
        assertTrue(session.playbackState.value.isPlaying)

        session.stop()

        // After stop, the session is deactivated; subsequent events are ignored.
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(false))
        assertFalse(session.playbackState.value.isPlaying)
    }

    // -------- onDeactivated() -------------------------------------------------

    @Test
    fun `onDeactivated stops loops and clears isPlaying flag`() = runBlocking {
        session.play(SONG_1, playlistId = 10L)
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))
        assertTrue(session.playbackState.value.isPlaying)

        // Simulate coordinator handoff: another consumer takes over
        val other = object : PlaybackCoordinator.PlaybackConsumer {
            override fun onPlayerEvent(event: PlayerEvent) {}
            override fun onFocusLoss() {}
            override fun onFocusLossTransient() {}
            override fun onFocusGain() {}
            override fun onDeactivated() {}
        }
        playbackCoordinator.activate(other)

        // After handoff: isPlaying should be false (onDeactivated cleared it)
        assertFalse(session.playbackState.value.isPlaying)
    }

    @Test
    fun `onDeactivated does NOT call saveProgress`() = runBlocking {
        session.play(SONG_1, playlistId = 10L)
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))

        // Trigger handoff
        val other = object : PlaybackCoordinator.PlaybackConsumer {
            override fun onPlayerEvent(event: PlayerEvent) {}
            override fun onFocusLoss() {}
            override fun onFocusLossTransient() {}
            override fun onFocusGain() {}
            override fun onDeactivated() {}
        }
        playbackCoordinator.activate(other)

        // Critical: onDeactivated must not save progress — at handoff time playerPort
        // may already be configured for the incoming consumer (another consumer reconfigures playerPort),
        // so saveProgress would write the wrong position.
        verify(playbackProgressRepository, org.mockito.kotlin.never())
            .saveProgress(any())
        Unit
    }

    @Test
    fun `activate setQueue order regression`() = runBlocking {
        // Lock the activate→setQueue order invariant in UserPlaybackSession.play().
        // Reverse handoff (alarm active, user plays) requires activate BEFORE setQueue
        // so onDeactivated fires while playerPort still holds the previous consumer's state.
        session.play(SONG_1, playlistId = 10L)

        // After play(): coordinator has this session as active consumer AND playerPort
        // has the queue set. Both must hold for the order invariant to be meaningful.
        assertEquals(1, playerPort.queueCalls.size)
        // Emitting an event should route to this session (proves activate ran)
        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(true))
        assertTrue(session.playbackState.value.isPlaying)
    }

    // -------- alarm-active guard ---------------------------------------------

    @Test
    fun `play is blocked while another session owns the player`() {
        playbackCoordinator.activate(otherConsumer)

        session.play(SONG_1, playlistId = -1L)

        assertTrue("setQueue must not reach the player during alarm ownership",
            playerPort.queueCalls.isEmpty())
        assertEquals("guard must run BEFORE requestFocus (focus steal would mute the alarm)",
            0, audioFocusPort.requestFocusCallCount)
        assertNull("user state must stay untouched", session.playbackState.value.currentSong)
        assertTrue(playerPort.playCalls.isEmpty())
    }

    @Test
    fun `playQueue is blocked while another session owns the player`() {
        playbackCoordinator.activate(otherConsumer)

        session.playQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 10L)

        assertTrue(playerPort.queueCalls.isEmpty())
        assertEquals(0, audioFocusPort.requestFocusCallCount)
        assertNull(session.playbackState.value.currentSong)
    }

    @Test
    fun `pause is blocked while another session owns the player`() {
        playbackCoordinator.activate(otherConsumer)

        session.pause()

        assertTrue(playerPort.pauseCalls.isEmpty())
    }

    @Test
    fun `resume is blocked while another session owns the player`() {
        playbackCoordinator.activate(otherConsumer)

        session.resume()

        assertEquals(0, audioFocusPort.requestFocusCallCount)
        assertTrue(playerPort.playCalls.isEmpty())
    }

    @Test
    fun `seekTo is blocked while another session owns the player`() {
        playbackCoordinator.activate(otherConsumer)

        session.seekTo(30_000L)

        assertTrue(playerPort.seekCalls.isEmpty())
    }

    @Test
    fun `next and previous are blocked while another session owns the player`() {
        playbackCoordinator.activate(otherConsumer)
        playerPort.hasNextValue = true
        playerPort.hasPreviousValue = true

        session.next()
        session.previous()

        assertTrue(playerPort.nextCalls.isEmpty())
        assertTrue(playerPort.previousCalls.isEmpty())
    }

    @Test
    fun `stop is blocked while another session owns the player`() {
        playbackCoordinator.activate(otherConsumer)

        session.stop()

        assertTrue(playerPort.stopCalls.isEmpty())
        assertTrue(playerPort.clearQueueCalls.isEmpty())
        verify(serviceStarter, never()).stopService()
    }

    @Test
    fun `setPlayMode is blocked while another session owns the player`() {
        playbackCoordinator.activate(otherConsumer)

        session.setPlayMode(PlayMode.RANDOM)

        assertEquals(PlayerRepeatMode.OFF, playerPort.lastRepeatMode)
        assertFalse(playerPort.lastShuffleEnabled)
    }

    @Test
    fun `togglePlayPause is blocked while another session owns the player`() {
        playbackCoordinator.activate(otherConsumer)

        session.togglePlayPause()

        assertTrue(playerPort.playCalls.isEmpty())
        assertTrue(playerPort.pauseCalls.isEmpty())
    }

    @Test
    fun `commands work again after the other session deactivates`() {
        playbackCoordinator.activate(otherConsumer)
        playbackCoordinator.deactivate(otherConsumer)

        session.play(SONG_1, playlistId = -1L)

        assertEquals(1, playerPort.queueCalls.size)
        assertEquals(SONG_1, session.playbackState.value.currentSong)
    }

    @Test
    fun `commands work while user session itself is the active consumer`() {
        // play() activates the user session as its own consumer
        session.play(SONG_1, playlistId = -1L)

        session.pause()

        assertEquals(1, playerPort.pauseCalls.size)
    }

    // -------- playQueue() suspension window (ownership re-check) -------------

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `playQueue aborts after suspension when alarm takes over during progress load`() =
        kotlinx.coroutines.test.runTest {
            // Use StandardTestDispatcher so the launch inside playQueue is suspended on
            // getProgress and we can interleave an alarm takeover before it resumes.
            val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
            val sharedPlayer = FakePlayerPort()
            val sharedFocus = FakeAudioFocusPort()
            val sharedProgressRepo = SuspendingProgressRepository()
            val sharedServiceStarter: ServiceStarter = mock()
            val sharedCoordinator = PlaybackCoordinator(
                playerPort = sharedPlayer,
                audioFocusPort = sharedFocus,
                mainDispatcher = dispatcher,
            )
            val user = UserPlaybackSession(
                playerPort = sharedPlayer,
                audioStreamPort = FakeAudioStreamPort(),
                playbackProgressRepository = sharedProgressRepo,
                serviceStarter = sharedServiceStarter,
                audioFocusPort = sharedFocus,
                playbackCoordinator = sharedCoordinator,
                mainDispatcher = dispatcher,
            )
            val alarm = AlarmPlaybackSession(
                playerPort = sharedPlayer,
                audioStreamPort = FakeAudioStreamPort(),
                playbackProgressRepository = sharedProgressRepo,
                audioFocusPort = sharedFocus,
                serviceStarter = sharedServiceStarter,
                playbackCoordinator = sharedCoordinator,
                mainDispatcher = dispatcher,
            )

            // Start user playQueue. The internal launch suspends on getProgress.
            user.playQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 1L)
            // While user is suspended on getProgress, alarm takes over the player.
            alarm.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 2L, useAlarmStream = false)

            // Advance virtual time past the suspend window; the user's continuation
            // should re-check ownership and abort before calling seekTo/play.
            advanceTimeBy(200)
            runCurrent()

            // User's seekTo/play must NOT have happened after the alarm took over.
            // Both setQueue calls (user + alarm) are expected; the alarm calls play()
            // during its own takeover. The user's post-suspend seekTo/play must be dropped
            // by the ownership re-check.
            assertEquals(2, sharedPlayer.queueCalls.size)
            assertEquals("user seekTo must NOT happen after suspension+takeover", 0, sharedPlayer.seekCalls.size)
            // alarm.playAlarmQueue calls play() once on takeover
            assertEquals(1, sharedPlayer.playCalls.size)
        }

    /**
     * Suspending fake — getProgress uses delay() (controlled by virtual time) so the
     * test can advance time and let the suspension release deterministically.
     */
    private class SuspendingProgressRepository : com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository {
        var lastSaved: com.rabbithole.musicbbit.domain.model.PlaybackProgress? = null
            private set

        override suspend fun saveProgress(progress: com.rabbithole.musicbbit.domain.model.PlaybackProgress): Result<Unit> {
            lastSaved = progress
            return Result.success(Unit)
        }

        override suspend fun getProgress(songId: Long, playlistId: Long): Result<com.rabbithole.musicbbit.domain.model.PlaybackProgress?> {
            kotlinx.coroutines.delay(100)
            return Result.success(null)
        }

        override suspend fun deleteProgress(songId: Long, playlistId: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun deleteAllProgressForPlaylist(playlistId: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun getProgressForPlaylist(playlistId: Long): Result<List<com.rabbithole.musicbbit.domain.model.PlaybackProgress>> =
            Result.success(emptyList())
    }
}
