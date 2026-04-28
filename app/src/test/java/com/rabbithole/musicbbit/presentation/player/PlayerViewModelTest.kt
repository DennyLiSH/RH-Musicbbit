package com.rabbithole.musicbbit.presentation.player

import android.content.Context
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.MusicPlaybackService
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
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

    private lateinit var context: Context
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var mockService: MusicPlaybackService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        alarmRepository = mockk()
        mockService = mockk(relaxed = true)

        // Stub the service's playbackState so the ViewModel's collection doesn't crash
        every { mockService.playbackState } returns MutableStateFlow(PlaybackState())

        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Creates a [PlayerViewModel] with a pre-bound mock service so that
     * [Context.bindService] is never invoked and playback control tests
     * can verify delegation directly.
     */
    private fun createViewModelWithService(): PlayerViewModel {
        val viewModel = PlayerViewModel(context, alarmRepository)
        // Inject the mock service through reflection to bypass bindService
        val serviceField = PlayerViewModel::class.java.getDeclaredField("service")
        serviceField.isAccessible = true
        serviceField.set(viewModel, mockService)
        return viewModel
    }

    // ------------------------------------------------------------------
    // Alarm label tests
    // ------------------------------------------------------------------

    @Test
    fun `alarmLabel is null when alarmId is null`() = runTest(testDispatcher) {
        val viewModel = PlayerViewModel(context, alarmRepository)
        viewModel._playbackState.value = PlaybackState(alarmId = null)
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

        val viewModel = PlayerViewModel(context, alarmRepository)
        viewModel._playbackState.value = PlaybackState(alarmId = 1L)
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

        val viewModel = PlayerViewModel(context, alarmRepository)
        viewModel._playbackState.value = PlaybackState(alarmId = 1L)
        advanceUntilIdle()
        assertEquals("Morning Jog", viewModel.alarmLabel.value)

        viewModel._playbackState.value = PlaybackState(alarmId = null)
        advanceUntilIdle()

        assertNull(viewModel.alarmLabel.value)
    }

    @Test
    fun `alarmLabel is null when repository returns null`() = runTest(testDispatcher) {
        coEvery { alarmRepository.getAlarmById(1L) } returns null

        val viewModel = PlayerViewModel(context, alarmRepository)
        viewModel._playbackState.value = PlaybackState(alarmId = 1L)

        assertNull(viewModel.alarmLabel.value)
    }

    // ------------------------------------------------------------------
    // Playback control forwarding tests
    // ------------------------------------------------------------------

    @Test
    fun `play forwards to service with song and playlistId`() = runTest(testDispatcher) {
        val viewModel = createViewModelWithService()
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

        verify { mockService.play(song, 42L) }
    }

    @Test
    fun `pause forwards to service`() = runTest(testDispatcher) {
        val viewModel = createViewModelWithService()

        viewModel.pause()

        verify { mockService.pause() }
    }

    @Test
    fun `resume forwards to service`() = runTest(testDispatcher) {
        val viewModel = createViewModelWithService()

        viewModel.resume()

        verify { mockService.resume() }
    }

    @Test
    fun `stop forwards to service`() = runTest(testDispatcher) {
        val viewModel = createViewModelWithService()

        viewModel.stop()

        verify { mockService.stop() }
    }

    @Test
    fun `next forwards to service`() = runTest(testDispatcher) {
        val viewModel = createViewModelWithService()

        viewModel.next()

        verify { mockService.next() }
    }

    @Test
    fun `previous forwards to service`() = runTest(testDispatcher) {
        val viewModel = createViewModelWithService()

        viewModel.previous()

        verify { mockService.previous() }
    }

    @Test
    fun `seekTo forwards position to service`() = runTest(testDispatcher) {
        val viewModel = createViewModelWithService()

        viewModel.seekTo(30_000L)

        verify { mockService.seekTo(30_000L) }
    }

    @Test
    fun `setPlayMode forwards mode to service`() = runTest(testDispatcher) {
        val viewModel = createViewModelWithService()

        viewModel.setPlayMode(PlayMode.RANDOM)

        verify { mockService.setPlayMode(PlayMode.RANDOM) }
    }
}
