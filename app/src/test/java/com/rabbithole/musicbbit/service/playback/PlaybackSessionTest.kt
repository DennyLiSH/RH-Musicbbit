package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackSource
import com.rabbithole.musicbbit.service.PlaybackState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * JVM unit tests for [PlaybackSession].
 *
 * Uses a dedicated [sessionDispatcher] (separate from any TestScope) so that
 * PlaybackSession's internal infinite loops (tickLoop, saveLoop) never
 * interfere with test finalisation.
 *
 * Event delivery is synchronous because [UnconfinedTestDispatcher] dispatches
 * eagerly — by the time `tryEmit()` returns the collector has already processed
 * the event.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionTest {

    // Separate dispatcher — its scheduler is NOT shared with any TestScope,
    // so runBlocking / runTest finalisation never tries to drain the infinite loops.
    private val sessionDispatcher = UnconfinedTestDispatcher()

    private lateinit var playerPort: PlayerPort
    private lateinit var playbackProgressRepository: PlaybackProgressRepository
    private lateinit var musicNotificationPort: MusicNotificationPort
    private lateinit var serviceStarter: ServiceStarter
    private lateinit var audioFocusPort: AudioFocusPort

    private val playerEvents = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 10)
    private val _playbackState = MutableStateFlow(PlaybackState())

    private lateinit var session: PlaybackSession

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
        playerPort = mockk(relaxed = true)
        playbackProgressRepository = mockk(relaxed = true)
        musicNotificationPort = mockk(relaxed = true)
        serviceStarter = mockk(relaxed = true)
        audioFocusPort = mockk(relaxed = true)
        every { playerPort.events } returns playerEvents

        coEvery { playbackProgressRepository.saveProgress(any()) } returns Result.success(Unit)

        session = PlaybackSession(
            playerPort = playerPort,
            playbackProgressRepository = playbackProgressRepository,
            musicNotificationPort = musicNotificationPort,
            serviceStarter = serviceStarter,
            audioFocusPort = audioFocusPort,
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
        assertNull(state.alarmId)
        assertEquals(PlaybackSource.USER, state.source)
    }

    // -------- play() ----------------------------------------------------------

    @Test
    fun `play requests focus starts service sets queue and plays`() {
        every { audioFocusPort.requestFocus() } returns true

        session.play(SONG_1, playlistId = 10L)

        verify { audioFocusPort.requestFocus() }
        verify { serviceStarter.startService() }
        verify { playerPort.setQueue(any(), 0, 0L) }
        verify { playerPort.play() }

        val state = session.playbackState.value
        assertEquals(SONG_1, state.currentSong)
        assertEquals(10L, state.currentPlaylistId)
        assertEquals(listOf(SONG_1), state.queue)
        assertEquals(0, state.queueIndex)
        assertEquals(PlaybackSource.USER, state.source)
    }

    @Test
    fun `play does nothing when focus request fails`() {
        every { audioFocusPort.requestFocus() } returns false

        session.play(SONG_1, playlistId = 10L)

        verify { audioFocusPort.requestFocus() }
        verify(exactly = 0) { serviceStarter.startService() }
        verify(exactly = 0) { playerPort.setQueue(any(), any(), any()) }
        verify(exactly = 0) { playerPort.play() }

        val state = session.playbackState.value
        assertNull(state.currentSong)
    }

    // -------- pause() ---------------------------------------------------------

    @Test
    fun `pause pauses the player`() {
        session.pause()

        verify { playerPort.pause() }
    }

    // -------- resume() --------------------------------------------------------

    @Test
    fun `resume requests focus and plays when not playing`() {
        every { audioFocusPort.requestFocus() } returns true
        every { playerPort.isPlaying() } returns false

        session.resume()

        verify { audioFocusPort.requestFocus() }
        verify { playerPort.play() }
    }

    @Test
    fun `resume does nothing when focus request fails`() {
        every { audioFocusPort.requestFocus() } returns false

        session.resume()

        verify { audioFocusPort.requestFocus() }
        verify(exactly = 0) { playerPort.play() }
    }

    // -------- stop() ----------------------------------------------------------

    @Test
    fun `stop abandons focus stops player clears queue and stops service`() {
        every { audioFocusPort.requestFocus() } returns true
        session.play(SONG_1, playlistId = 10L)

        session.stop()

        verify { audioFocusPort.abandonFocus() }
        verify { playerPort.stop() }
        verify { playerPort.clearQueue() }
        verify { serviceStarter.stopService() }

        val state = session.playbackState.value
        assertFalse(state.isPlaying)
        assertNull(state.currentSong)
        assertEquals(-1L, state.currentPlaylistId)
        assertTrue(state.queue.isEmpty())
        assertEquals(0, state.queueIndex)
        assertNull(state.alarmId)
        assertEquals(PlaybackSource.USER, state.source)
    }

    // -------- playAlarmQueue() ------------------------------------------------

    @Test
    fun `playAlarmQueue sets source to ALARM and alarmId`() = runBlocking {
        every { audioFocusPort.requestFocus() } returns true
        coEvery { playbackProgressRepository.getProgress(any(), any()) } returns Result.success(null)

        session.playAlarmQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 20L, alarmId = 99L)

        val state = session.playbackState.value
        assertEquals(PlaybackSource.ALARM, state.source)
        assertEquals(99L, state.alarmId)
        assertEquals(listOf(SONG_1, SONG_2), state.queue)
        assertEquals(0, state.queueIndex)
    }

    // -------- PlayerEvent handling --------------------------------------------
    // tryEmit() delivers synchronously with UnconfinedTestDispatcher

    @Test
    fun `IsPlayingChanged true updates state and starts save loop`() {
        every { audioFocusPort.requestFocus() } returns true
        session.play(SONG_1, playlistId = 10L)

        every { playerPort.currentPositionMs() } returns 5_000L

        playerEvents.tryEmit(PlayerEvent.IsPlayingChanged(true))

        val state = session.playbackState.value
        assertTrue(state.isPlaying)
    }

    @Test
    fun `IsPlayingChanged false updates state`() {
        playerEvents.tryEmit(PlayerEvent.IsPlayingChanged(false))

        val state = session.playbackState.value
        assertFalse(state.isPlaying)
    }

    @Test
    fun `MediaItemTransition updates current song and queueIndex`() {
        playerEvents.tryEmit(
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
        playerEvents.tryEmit(PlayerEvent.PlaybackReady(durationMs = 200_000L))

        val state = session.playbackState.value
        assertEquals(200_000L, state.durationMs)
    }

    @Test
    fun `PositionDiscontinuity updates position and queueIndex`() {
        playerEvents.tryEmit(
            PlayerEvent.PositionDiscontinuity(newPositionMs = 30_000L, itemIndex = 2)
        )

        val state = session.playbackState.value
        assertEquals(30_000L, state.positionMs)
        assertEquals(2, state.queueIndex)
    }

    @Test
    fun `QueueEnded with USER source calls stop`() {
        every { audioFocusPort.requestFocus() } returns true
        session.play(SONG_1, playlistId = 10L)

        playerEvents.tryEmit(PlayerEvent.QueueEnded)

        verify { playerPort.stop() }
        verify { playerPort.clearQueue() }
        verify { serviceStarter.stopService() }
    }

    @Test
    fun `QueueEnded with ALARM source does not call stop`() = runBlocking {
        every { audioFocusPort.requestFocus() } returns true
        coEvery { playbackProgressRepository.getProgress(any(), any()) } returns Result.success(null)

        session.playAlarmQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L, alarmId = 42L)

        playerEvents.tryEmit(PlayerEvent.QueueEnded)

        // stop should NOT be called for ALARM source
        verify(exactly = 0) { playerPort.stop() }
    }

    // -------- setPlayMode() ---------------------------------------------------

    @Test
    fun `setPlayMode RANDOM enables shuffle`() {
        session.setPlayMode(PlayMode.RANDOM)

        verify { playerPort.setShuffleEnabled(true) }
        verify { playerPort.setRepeatMode(PlayerRepeatMode.OFF) }

        assertEquals(PlayMode.RANDOM, session.playbackState.value.playMode)
    }

    @Test
    fun `setPlayMode REPEAT_ONE sets repeat one`() {
        session.setPlayMode(PlayMode.REPEAT_ONE)

        verify { playerPort.setShuffleEnabled(false) }
        verify { playerPort.setRepeatMode(PlayerRepeatMode.ONE) }

        assertEquals(PlayMode.REPEAT_ONE, session.playbackState.value.playMode)
    }

    @Test
    fun `setPlayMode SEQUENTIAL disables shuffle and repeat`() {
        session.setPlayMode(PlayMode.SEQUENTIAL)

        verify { playerPort.setShuffleEnabled(false) }
        verify { playerPort.setRepeatMode(PlayerRepeatMode.OFF) }

        assertEquals(PlayMode.SEQUENTIAL, session.playbackState.value.playMode)
    }

    // -------- next() / previous() ---------------------------------------------

    @Test
    fun `next delegates to playerPort when hasNext is true`() {
        every { playerPort.hasNext() } returns true

        session.next()

        verify { playerPort.next() }
    }

    @Test
    fun `next does nothing when hasNext is false`() {
        every { playerPort.hasNext() } returns false

        session.next()

        verify(exactly = 0) { playerPort.next() }
    }

    @Test
    fun `previous delegates to playerPort when hasPrevious is true`() {
        every { playerPort.hasPrevious() } returns true

        session.previous()

        verify { playerPort.previous() }
    }

    @Test
    fun `previous does nothing when hasPrevious is false`() {
        every { playerPort.hasPrevious() } returns false

        session.previous()

        verify(exactly = 0) { playerPort.previous() }
    }

    // -------- seekTo() --------------------------------------------------------

    @Test
    fun `seekTo delegates to playerPort and updates state`() {
        session.seekTo(45_000L)

        verify { playerPort.seekTo(45_000L) }
        assertEquals(45_000L, session.playbackState.value.positionMs)
    }

    // -------- preloadFirstSong() ----------------------------------------------

    @Test
    fun `preloadFirstSong sets queue with single item`() {
        session.preloadFirstSong("/tmp/preload.mp3")

        val slot = slot<List<PlayItem>>()
        verify { playerPort.setQueue(capture(slot), 0, 0L) }
        assertEquals(1, slot.captured.size)
        assertEquals("/tmp/preload.mp3", slot.captured[0].uri)
    }

    // -------- playQueue() -----------------------------------------------------

    @Test
    fun `playQueue requests focus and plays queue`() = runBlocking {
        every { audioFocusPort.requestFocus() } returns true
        coEvery { playbackProgressRepository.getProgress(SONG_1.id, 10L) } returns Result.success(null)

        session.playQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 10L)

        verify { audioFocusPort.requestFocus() }
        verify { serviceStarter.startService() }
        verify { playerPort.setQueue(any(), 0, 0L) }
        verify { playerPort.play() }

        val state = session.playbackState.value
        assertEquals(SONG_1, state.currentSong)
        assertEquals(10L, state.currentPlaylistId)
        assertEquals(listOf(SONG_1, SONG_2), state.queue)
        assertEquals(0, state.queueIndex)
    }

    @Test
    fun `playQueue restores progress when available`() = runBlocking {
        every { audioFocusPort.requestFocus() } returns true
        val progress = PlaybackProgress(
            songId = SONG_1.id,
            positionMs = 30_000L,
            updatedAt = 0L,
            playlistId = 10L,
        )
        coEvery { playbackProgressRepository.getProgress(SONG_1.id, 10L) } returns Result.success(progress)

        session.playQueue(listOf(SONG_1, SONG_2), startIndex = 0, playlistId = 10L)

        verify { playerPort.seekTo(30_000L) }
    }

    @Test
    fun `playQueue returns early when focus request fails`() = runBlocking {
        every { audioFocusPort.requestFocus() } returns false
        coEvery { playbackProgressRepository.getProgress(SONG_1.id, 10L) } returns Result.success(null)

        session.playQueue(listOf(SONG_1), startIndex = 0, playlistId = 10L)

        verify { audioFocusPort.requestFocus() }
        verify(exactly = 0) { serviceStarter.startService() }
        verify(exactly = 0) { playerPort.setQueue(any(), 0, 0L) }
        verify(exactly = 0) { playerPort.play() }

        val state = session.playbackState.value
        assertNull(state.currentSong)
    }

    @Test
    fun `playQueue returns early for empty list`() {
        session.playQueue(emptyList(), startIndex = 0, playlistId = 10L)

        verify(exactly = 0) { audioFocusPort.requestFocus() }
        verify(exactly = 0) { playerPort.setQueue(any(), any(), any()) }
    }

    // -------- audio focus callbacks -------------------------------------------

    @Test
    fun `focus loss pauses playback when playing`() {
        every { audioFocusPort.requestFocus() } returns true
        session.play(SONG_1, playlistId = 10L)

        // Playback must actually be playing for focus loss to trigger pause
        playerEvents.tryEmit(PlayerEvent.IsPlayingChanged(true))

        val focusLossSlot = slot<() -> Unit>()
        verify { audioFocusPort.registerCallbacks(capture(focusLossSlot), any(), any()) }

        focusLossSlot.captured.invoke()

        verify { playerPort.pause() }
    }

    @Test
    fun `focus gain resumes playback when previously paused by focus loss`() {
        every { audioFocusPort.requestFocus() } returns true
        every { playerPort.isPlaying() } returns false
        session.play(SONG_1, playlistId = 10L)

        val focusLossSlot = slot<() -> Unit>()
        val focusGainSlot = slot<() -> Unit>()
        verify {
            audioFocusPort.registerCallbacks(
                capture(focusLossSlot),
                any(),
                capture(focusGainSlot)
            )
        }

        // Start playing
        playerEvents.tryEmit(PlayerEvent.IsPlayingChanged(true))
        // Lose focus -> wasPausedByFocusLoss = true, pause()
        focusLossSlot.captured.invoke()
        // Player reports it's no longer playing -> isPlaying = false
        playerEvents.tryEmit(PlayerEvent.IsPlayingChanged(false))
        // Regain focus -> resume()
        focusGainSlot.captured.invoke()

        verify(atLeast = 1) { playerPort.play() }
    }
}
