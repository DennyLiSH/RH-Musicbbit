package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.alarm.FakeProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmPlaybackSessionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var playerPort: FakePlayerPort
    private lateinit var audioStreamPort: FakeAudioStreamPort
    private lateinit var progressRepository: FakeProgressRepository
    private lateinit var audioFocusPort: FakeAudioFocusPort
    private lateinit var serviceStarter: FakeServiceStarter
    private lateinit var coordinator: PlaybackCoordinator
    private lateinit var session: AlarmPlaybackSession

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
    }

    @Before
    fun setUp() {
        playerPort = FakePlayerPort()
        audioStreamPort = FakeAudioStreamPort()
        progressRepository = FakeProgressRepository()
        audioFocusPort = FakeAudioFocusPort()
        serviceStarter = FakeServiceStarter()
        coordinator = PlaybackCoordinator(
            playerPort = playerPort,
            audioFocusPort = audioFocusPort,
            mainDispatcher = dispatcher,
        )
        session = AlarmPlaybackSession(
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
        session.close()
        coordinator.close()
    }

    @Test
    fun `playAlarmQueue requests focus starts service sets queue and plays`() {
        session.playAlarmQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        assertEquals(1, audioFocusPort.requestFocusCallCount)
        assertTrue(serviceStarter.startCalled)
        assertEquals(1, playerPort.queueCalls.size)
        assertEquals(2, playerPort.queueCalls[0].items.size)
        assertEquals(0, playerPort.queueCalls[0].startIndex)
        assertEquals(1, playerPort.playCalls.size)
        assertTrue(audioStreamPort.lastAlarmStream)

        val state = session.playbackState.value
        assertEquals(SONG_1, state.currentSong)
        assertEquals(10L, state.currentPlaylistId)
        assertEquals(listOf(SONG_1, SONG_2), state.queue)
        assertEquals(0, state.queueIndex)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `playAlarmQueue with useAlarmStream false configures media stream`() {
        session.playAlarmQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 10L, useAlarmStream = false)

        assertFalse(audioStreamPort.lastAlarmStream)
    }

    @Test
    fun `playAlarmQueue coerces startIndex to valid range`() {
        session.playAlarmQueue(listOf(SONG_1, SONG_2), startIndex = 5, playlistId = 10L, useAlarmStream = true)

        assertEquals(1, playerPort.queueCalls[0].startIndex)
        assertEquals(SONG_2, session.playbackState.value.currentSong)
    }

    @Test
    fun `playAlarmQueue is no-op for empty songs`() {
        session.playAlarmQueue(emptyList(), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        assertEquals(0, audioFocusPort.requestFocusCallCount)
        assertFalse(serviceStarter.startCalled)
        assertEquals(0, playerPort.queueCalls.size)
    }

    @Test
    fun `playAlarmQueue is no-op for invalid playlistId`() {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = -1L, useAlarmStream = true)

        assertEquals(0, audioFocusPort.requestFocusCallCount)
        assertFalse(serviceStarter.startCalled)
        assertEquals(0, playerPort.queueCalls.size)
    }

    @Test
    fun `playAlarmQueue does nothing when focus request fails`() {
        audioFocusPort.setRequestFocusResult(false)

        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        assertFalse(serviceStarter.startCalled)
        assertEquals(0, playerPort.queueCalls.size)
        assertEquals(0, playerPort.playCalls.size)
    }

    @Test
    fun `pause pauses player and saves progress`() {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        session.pause()

        assertEquals(1, playerPort.pauseCalls.size)
        assertEquals(SONG_1.id, progressRepository.lastSaved?.songId)
    }

    @Test
    fun `resume requests focus and plays when not playing`() {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)
        playerPort.isPlayingValue = false

        session.resume()

        assertEquals(2, audioFocusPort.requestFocusCallCount)
        assertEquals(2, playerPort.playCalls.size)
    }

    @Test
    fun `resume does nothing when focus request fails`() {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)
        audioFocusPort.setRequestFocusResult(false)

        session.resume()

        assertEquals(2, audioFocusPort.requestFocusCallCount)
        assertEquals(1, playerPort.playCalls.size)
    }

    @Test
    fun `stop abandons focus stops player clears queue and deactivates`() {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        session.stop()

        assertEquals(1, audioFocusPort.abandonFocusCallCount)
        assertEquals(1, playerPort.stopCalls.size)
        assertEquals(1, playerPort.clearQueueCalls.size)
        assertTrue(serviceStarter.stopCalled)

        val state = session.playbackState.value
        assertNull(state.currentSong)
        assertEquals(-1L, state.currentPlaylistId)
        assertTrue(state.queue.isEmpty())
    }

    @Test
    fun `stop after queueEnded skips saveProgress`() = runBlocking {
        // Queue-ended path: handleQueueEnded sets queueEndedPending=true; the subsequent
        // stop() call (driven by AlarmFireSession upon receiving QueueEnded) must skip
        // saveProgress to avoid writing the just-finished song's end position.
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        playerPort.emitEvent(PlayerEvent.QueueEnded)
        // Calling stop here simulates AlarmFireSession's response to QueueEnded.
        // UnconfinedTestDispatcher executes the sessionScope.launch in handleQueueEnded eagerly.
        session.stop()

        assertNull(progressRepository.lastSaved)
    }

    @Test
    fun `stop without prior queueEnded saves progress`() = runBlocking {
        // Non-queue-ended stop path (manual stop): saveProgress should still run.
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        session.stop()

        assertNotNull(progressRepository.lastSaved)
    }

    @Test
    fun `stop emits PlaybackStopped transition`() = runBlocking {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)
        val transitions = mutableListOf<PlaybackTransition>()
        val collectJob = launch(dispatcher) {
            session.playbackTransitions.collect { transitions.add(it) }
        }

        session.stop()

        collectJob.cancel()
        assertTrue(transitions.any { it is PlaybackTransition.PlaybackStopped })
    }

    @Test
    fun `seekTo delegates to playerPort and updates state`() {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        session.seekTo(45_000L)

        assertEquals(1, playerPort.seekCalls.size)
        assertEquals(45_000L, playerPort.seekCalls[0])
        assertEquals(45_000L, session.playbackState.value.positionMs)
    }

    @Test
    fun `IsPlayingChanged updates state`() {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        playerPort.emitEvent(PlayerEvent.IsPlayingChanged(false))

        assertFalse(session.playbackState.value.isPlaying)
    }

    @Test
    fun `MediaItemTransition updates current song and queueIndex`() {
        session.playAlarmQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 10L, useAlarmStream = true)

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
    fun `MediaItemTransition AUTO emits SongCompleted`() = runBlocking {
        session.playAlarmQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 10L, useAlarmStream = true)
        val transitions = mutableListOf<PlaybackTransition>()
        val collectJob = launch(dispatcher) {
            session.playbackTransitions.collect { transitions.add(it) }
        }

        playerPort.emitEvent(
            PlayerEvent.MediaItemTransition(
                itemTag = SONG_2,
                itemIndex = 1,
                reason = TransitionReason.AUTO,
            )
        )

        collectJob.cancel()
        val transition = transitions.firstOrNull { it is PlaybackTransition.SongCompleted }
        assertTrue(transition is PlaybackTransition.SongCompleted)
        assertEquals(SONG_2.id, (transition as PlaybackTransition.SongCompleted).songId)
    }

    @Test
    fun `PlaybackReady updates duration`() {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        playerPort.emitEvent(PlayerEvent.PlaybackReady(durationMs = 200_000L))

        assertEquals(200_000L, session.playbackState.value.durationMs)
    }

    @Test
    fun `PositionDiscontinuity updates position and queueIndex`() {
        session.playAlarmQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        playerPort.emitEvent(
            PlayerEvent.PositionDiscontinuity(newPositionMs = 30_000L, itemIndex = 1)
        )

        val state = session.playbackState.value
        assertEquals(30_000L, state.positionMs)
        assertEquals(1, state.queueIndex)
    }

    @Test
    fun `QueueEnded emits QueueEnded transition with playlistId`() = runBlocking {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)
        val transitions = mutableListOf<PlaybackTransition>()
        val collectJob = launch(dispatcher) {
            session.playbackTransitions.collect { transitions.add(it) }
        }

        playerPort.emitEvent(PlayerEvent.QueueEnded)

        collectJob.cancel()
        val transition = transitions.firstOrNull { it is PlaybackTransition.QueueEnded }
        assertTrue(transition is PlaybackTransition.QueueEnded)
        assertEquals(10L, (transition as PlaybackTransition.QueueEnded).playlistId)
    }

    @Test
    fun `QueueEnded does not delete playlist progress`() {
        progressRepository.set(10L, emptyList())
        // FakeProgressRepository.deleteAllProgressForPlaylist does not track call count,
        // so we verify indirectly: QueueEnded alone must NOT reset state (Fix 2 moved the
        // reset into stop()'s path). State reset happens when AlarmFireSession receives
        // the QueueEnded transition and calls stop().
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        playerPort.emitEvent(PlayerEvent.QueueEnded)

        // Post-Fix-2: state is NOT reset by QueueEnded alone (queueEndedPending flag set,
        // but state preserved until stop() runs).
        assertEquals(SONG_1, session.playbackState.value.currentSong)

        // stop() — simulating AlarmFireSession's response to QueueEnded — resets state.
        session.stop()
        assertNull(session.playbackState.value.currentSong)
        assertEquals(-1L, session.playbackState.value.currentPlaylistId)
    }

    @Test
    fun `focus loss pauses playback when playing`() {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)

        audioFocusPort.simulateFocusLoss()

        assertEquals(1, playerPort.pauseCalls.size)
    }

    @Test
    fun `focus gain resumes playback after focus loss pause`() {
        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, useAlarmStream = true)
        playerPort.isPlayingValue = false

        audioFocusPort.simulateFocusLoss()
        audioFocusPort.simulateFocusGain()

        assertEquals(2, playerPort.playCalls.size)
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

/** Records the last stream choice made by the session under test. */
class FakeAudioStreamPort : AudioStreamPort {
    var lastAlarmStream: Boolean = false
    override fun setAlarmStream(alarmStream: Boolean) {
        lastAlarmStream = alarmStream
    }
}
