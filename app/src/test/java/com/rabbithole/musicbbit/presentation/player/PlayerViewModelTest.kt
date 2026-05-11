package com.rabbithole.musicbbit.presentation.player

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackState
import com.rabbithole.musicbbit.service.playback.PlaybackSession
import com.rabbithole.musicbbit.service.playback.PlayerEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var playbackController: PlaybackSession
    private lateinit var alarmRepository: AlarmRepository
    private val playbackStateFlow = MutableStateFlow(PlaybackState())
    private val playerEventsFlow = MutableSharedFlow<PlayerEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playbackController = mockk(relaxed = true)
        alarmRepository = mockk()

        every { playbackController.playbackState } returns playbackStateFlow
        every { playbackController.playerEvents } returns playerEventsFlow

        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PlayerViewModel {
        return PlayerViewModel(playbackController, alarmRepository)
    }

    // ------------------------------------------------------------------
    // Alarm label tests
    // ------------------------------------------------------------------

    @Test
    fun `alarmLabel is null when alarmId is null`() = runTest(testDispatcher) {
        playbackStateFlow.value = PlaybackState(alarmId = null)
        val viewModel = createViewModel()
        assertNull(viewModel.alarmLabel.value)
    }

    @Test
    fun `alarmLabel loaded from repository when alarmId present`() = runTest(testDispatcher) {
        val alarm = Alarm(
            id = 1L,
            hour = 7,
            minute = 30,
            repeatDays = emptySet(),
            playlistId = 10L,
            isEnabled = true,
            label = "Morning Jog",
            autoStop = null,
            lastTriggeredAt = null
        )
        coEvery { alarmRepository.getAlarmById(1L) } returns alarm

        playbackStateFlow.value = PlaybackState(alarmId = 1L)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Morning Jog", viewModel.alarmLabel.value)
    }

    @Test
    fun `alarmLabel cleared when alarmId becomes null`() = runTest(testDispatcher) {
        val alarm = Alarm(
            id = 1L,
            hour = 7,
            minute = 30,
            repeatDays = emptySet(),
            playlistId = 10L,
            isEnabled = true,
            label = "Morning Jog",
            autoStop = null,
            lastTriggeredAt = null
        )
        coEvery { alarmRepository.getAlarmById(1L) } returns alarm

        playbackStateFlow.value = PlaybackState(alarmId = 1L)
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals("Morning Jog", viewModel.alarmLabel.value)

        playbackStateFlow.value = PlaybackState(alarmId = null)
        advanceUntilIdle()

        assertNull(viewModel.alarmLabel.value)
    }

    @Test
    fun `alarmLabel is null when repository returns null`() = runTest(testDispatcher) {
        coEvery { alarmRepository.getAlarmById(1L) } returns null

        playbackStateFlow.value = PlaybackState(alarmId = 1L)
        val viewModel = createViewModel()

        assertNull(viewModel.alarmLabel.value)
    }

    // ------------------------------------------------------------------
    // Playback control forwarding tests
    // ------------------------------------------------------------------

    @Test
    fun `play forwards to playbackController with song and playlistId`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val song = Song(
            id = 1L,
            path = "/music/test.mp3",
            title = "Test Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            dateAdded = 0L,
            coverUri = null
        )

        viewModel.play(song, playlistId = 42L)

        verify { playbackController.play(song, 42L) }
    }

    @Test
    fun `playPlaylist forwards to playbackController`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val songs = listOf(
            Song(
                id = 1L,
                path = "/music/test.mp3",
                title = "Test Song",
                artist = "Artist",
                album = "Album",
                durationMs = 180_000L,
                dateAdded = 0L,
                coverUri = null
            )
        )

        viewModel.playPlaylist(songs, startIndex = 0, playlistId = 10L)

        verify { playbackController.playQueue(songs, 0, 10L) }
    }

    @Test
    fun `pause forwards to playbackController`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.pause()

        verify { playbackController.pause() }
    }

    @Test
    fun `resume forwards to playbackController`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.resume()

        verify { playbackController.resume() }
    }

    @Test
    fun `stop forwards to playbackController`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.stop()

        verify { playbackController.stop() }
    }

    @Test
    fun `next forwards to playbackController`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.next()

        verify { playbackController.next() }
    }

    @Test
    fun `previous forwards to playbackController`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.previous()

        verify { playbackController.previous() }
    }

    @Test
    fun `seekTo forwards position to playbackController`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.seekTo(30_000L)

        verify { playbackController.seekTo(30_000L) }
    }

    @Test
    fun `setPlayMode forwards mode to playbackController`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.setPlayMode(PlayMode.RANDOM)

        verify { playbackController.setPlayMode(PlayMode.RANDOM) }
    }
}
