package com.rabbithole.musicbbit.presentation.alarm

import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.service.alarm.AlarmFireSession
import com.rabbithole.musicbbit.service.alarm.AlarmFireState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmRingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var alarmFireSession: AlarmFireSession
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var alarmRingSettingsRepository: AlarmRingSettingsRepository

    private val alarmFireStateFlow = MutableStateFlow<AlarmFireState>(AlarmFireState.Idle)
    private val breathingEnabledFlow = MutableStateFlow(true)
    private val breathingPeriodMsFlow = MutableStateFlow(3500L)

    companion object {
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
        Dispatchers.setMain(testDispatcher)

        alarmFireSession = mockk(relaxed = true)
        alarmRepository = mockk()
        alarmRingSettingsRepository = mockk()

        every { alarmFireSession.state } returns alarmFireStateFlow
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns breathingEnabledFlow
        every { alarmRingSettingsRepository.getBreathingPeriodMs() } returns breathingPeriodMsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AlarmRingViewModel {
        return AlarmRingViewModel(alarmFireSession, alarmRepository, alarmRingSettingsRepository)
    }

    // ------------------------------------------------------------------
    // Test fixtures
    // ------------------------------------------------------------------

    private val testSong = Song(
        id = 1L,
        path = "/music/test_song.mp3",
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        durationMs = 180_000L,
        dateAdded = 0L,
        coverUri = null
    )

    private val testAlarm = Alarm(
        id = 42L,
        hour = 7,
        minute = 30,
        repeatDays = emptySet(),
        playlistId = 10L,
        isEnabled = true,
        label = "Morning Alarm",
        autoStop = null,
        lastTriggeredAt = null
    )

    // ------------------------------------------------------------------
    // 1. Initial UI state
    // ------------------------------------------------------------------

    @Test
    fun `initial uiState has isPlaying false, hasPlayback false, empty alarmLabel`() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertFalse(state.isPlaying)
        assertFalse(state.hasPlayback)
        assertEquals("", state.alarmLabel)
        assertNull(state.currentSongTitle)
        assertNull(state.currentSongArtist)
        assertNull(state.errorMessageResId)
    }

    // ------------------------------------------------------------------
    // 2. Playing state → isPlaying=true, hasPlayback=true, song info set
    // ------------------------------------------------------------------

    @Test
    fun `Playing state sets isPlaying true, hasPlayback true, and song info`() = runTest {
        alarmFireStateFlow.value = AlarmFireState.Playing(
            alarmId = 42L,
            currentSong = testSong
        )

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue(state.isPlaying)
        assertTrue(state.hasPlayback)
        assertEquals("Test Song", state.currentSongTitle)
        assertEquals("Test Artist", state.currentSongArtist)
    }

    @Test
    fun `Playing state with null song sets hasPlayback false`() = runTest {
        alarmFireStateFlow.value = AlarmFireState.Playing(
            alarmId = 42L,
            currentSong = null
        )

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue(state.isPlaying)
        assertFalse(state.hasPlayback)
        assertNull(state.currentSongTitle)
        assertNull(state.currentSongArtist)
    }

    // ------------------------------------------------------------------
    // 3. Paused state → isPlaying=false, hasPlayback=true, song info retained
    // ------------------------------------------------------------------

    @Test
    fun `Paused state sets isPlaying false, hasPlayback true, song info retained`() = runTest {
        alarmFireStateFlow.value = AlarmFireState.Paused(
            alarmId = 42L,
            currentSong = testSong,
            positionMs = 15_000L
        )

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isPlaying)
        assertTrue(state.hasPlayback)
        assertEquals("Test Song", state.currentSongTitle)
        assertEquals("Test Artist", state.currentSongArtist)
    }

    @Test
    fun `transition from Playing to Paused updates isPlaying to false`() = runTest {
        alarmFireStateFlow.value = AlarmFireState.Playing(
            alarmId = 42L,
            currentSong = testSong
        )
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isPlaying)

        alarmFireStateFlow.value = AlarmFireState.Paused(
            alarmId = 42L,
            currentSong = testSong,
            positionMs = 10_000L
        )

        assertFalse(viewModel.uiState.value.isPlaying)
        assertTrue(viewModel.uiState.value.hasPlayback)
    }

    // ------------------------------------------------------------------
    // 4. Stopped state → isPlaying=false, hasPlayback=false
    // ------------------------------------------------------------------

    @Test
    fun `Stopped state sets isPlaying false and hasPlayback false`() = runTest {
        alarmFireStateFlow.value = AlarmFireState.Playing(
            alarmId = 42L,
            currentSong = testSong
        )
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isPlaying)

        alarmFireStateFlow.value = AlarmFireState.Stopped

        val state = viewModel.uiState.value
        assertFalse(state.isPlaying)
        assertFalse(state.hasPlayback)
        assertNull(state.currentSongTitle)
        assertNull(state.currentSongArtist)
    }

    @Test
    fun `Idle state sets isPlaying false and hasPlayback false`() = runTest {
        alarmFireStateFlow.value = AlarmFireState.Playing(
            alarmId = 42L,
            currentSong = testSong
        )
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isPlaying)

        alarmFireStateFlow.value = AlarmFireState.Idle

        val state = viewModel.uiState.value
        assertFalse(state.isPlaying)
        assertFalse(state.hasPlayback)
    }

    // ------------------------------------------------------------------
    // 5. Error state → error message set
    // ------------------------------------------------------------------

    @Test
    fun `Error state sets errorMessageResId`() = runTest {
        alarmFireStateFlow.value = AlarmFireState.Error(
            alarmId = 42L,
            message = "Something went wrong"
        )

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isPlaying)
        assertFalse(state.hasPlayback)
        assertEquals(R.string.error_load_failed, state.errorMessageResId)
    }

    @Test
    fun `Loading state does not set error`() = runTest {
        alarmFireStateFlow.value = AlarmFireState.Loading(alarmId = 42L)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isPlaying)
        assertFalse(state.hasPlayback)
        assertNull(state.errorMessageResId)
    }

    // ------------------------------------------------------------------
    // 6. pause() delegates to alarmFireSession
    // ------------------------------------------------------------------

    @Test
    fun `pause delegates to alarmFireSession`() = runTest {
        val viewModel = createViewModel()

        viewModel.pause()

        verify { alarmFireSession.pause() }
    }

    // ------------------------------------------------------------------
    // 7. resume() delegates to alarmFireSession
    // ------------------------------------------------------------------

    @Test
    fun `resume delegates to alarmFireSession`() = runTest {
        val viewModel = createViewModel()

        viewModel.resume()

        verify { alarmFireSession.resume() }
    }

    // ------------------------------------------------------------------
    // 8. stop() delegates to alarmFireSession
    // ------------------------------------------------------------------

    @Test
    fun `stop delegates to alarmFireSession`() = runTest {
        val viewModel = createViewModel()

        viewModel.stop()

        verify { alarmFireSession.stop() }
    }

    // ------------------------------------------------------------------
    // 9. Breathing settings combine correctly
    // ------------------------------------------------------------------

    @Test
    fun `breathing settings loaded from repository`() = runTest {
        breathingEnabledFlow.value = false
        breathingPeriodMsFlow.value = 5000L

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.breathingEnabled)
        assertEquals(5000L, state.breathingPeriodMs)
    }

    @Test
    fun `breathing settings update when repository flows emit new values`() = runTest {
        val viewModel = createViewModel()
        assertEquals(true, viewModel.uiState.value.breathingEnabled)
        assertEquals(3500L, viewModel.uiState.value.breathingPeriodMs)

        breathingEnabledFlow.value = false
        breathingPeriodMsFlow.value = 7000L

        val state = viewModel.uiState.value
        assertFalse(state.breathingEnabled)
        assertEquals(7000L, state.breathingPeriodMs)
    }

    @Test
    fun `breathing settings error sets errorMessageResId`() = runTest {
        every { alarmRingSettingsRepository.isBreathingEnabled() } returns
            kotlinx.coroutines.flow.flow { throw RuntimeException("DataStore error") }

        val viewModel = createViewModel()

        assertEquals(R.string.error_load_failed, viewModel.uiState.value.errorMessageResId)
    }

    // ------------------------------------------------------------------
    // 10. Alarm label resolved from alarmRepository
    // ------------------------------------------------------------------

    @Test
    fun `alarm label resolved from repository when Playing state has alarmId`() = runTest {
        coEvery { alarmRepository.getAlarmById(42L) } returns testAlarm

        alarmFireStateFlow.value = AlarmFireState.Playing(
            alarmId = 42L,
            currentSong = testSong
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Morning Alarm", viewModel.uiState.value.alarmLabel)
    }

    @Test
    fun `alarm label resolved when Paused state has alarmId`() = runTest {
        coEvery { alarmRepository.getAlarmById(42L) } returns testAlarm

        alarmFireStateFlow.value = AlarmFireState.Paused(
            alarmId = 42L,
            currentSong = testSong,
            positionMs = 5_000L
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Morning Alarm", viewModel.uiState.value.alarmLabel)
    }

    @Test
    fun `alarm label is empty string when alarm has null label`() = runTest {
        val alarmWithNullLabel = testAlarm.copy(label = null)
        coEvery { alarmRepository.getAlarmById(42L) } returns alarmWithNullLabel

        alarmFireStateFlow.value = AlarmFireState.Playing(
            alarmId = 42L,
            currentSong = testSong
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.alarmLabel)
    }

    @Test
    fun `alarm label is empty string when repository returns null alarm`() = runTest {
        coEvery { alarmRepository.getAlarmById(99L) } returns null

        alarmFireStateFlow.value = AlarmFireState.Playing(
            alarmId = 99L,
            currentSong = testSong
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.alarmLabel)
    }

    @Test
    fun `alarm label is empty string when repository throws`() = runTest {
        coEvery { alarmRepository.getAlarmById(42L) } throws RuntimeException("DB error")

        alarmFireStateFlow.value = AlarmFireState.Playing(
            alarmId = 42L,
            currentSong = testSong
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.alarmLabel)
    }

    @Test
    fun `alarm label not fetched for Idle state`() = runTest {
        alarmFireStateFlow.value = AlarmFireState.Idle

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.alarmLabel)
        // getAlarmById should never be called since alarmIdOrNull is null for Idle
        coEvery { alarmRepository.getAlarmById(any()) } answers {
            throw AssertionError("Should not be called")
        }
    }

    @Test
    fun `alarm label not fetched for Stopped state`() = runTest {
        alarmFireStateFlow.value = AlarmFireState.Stopped

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.alarmLabel)
    }

    // ------------------------------------------------------------------
    // State transition sequence
    // ------------------------------------------------------------------

    @Test
    fun `full state transition sequence reflects in uiState`() = runTest {
        coEvery { alarmRepository.getAlarmById(42L) } returns testAlarm

        // Start with Idle
        alarmFireStateFlow.value = AlarmFireState.Idle
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isPlaying)
        assertFalse(viewModel.uiState.value.hasPlayback)

        // Transition to Loading
        alarmFireStateFlow.value = AlarmFireState.Loading(alarmId = 42L)
        assertFalse(viewModel.uiState.value.isPlaying)
        assertFalse(viewModel.uiState.value.hasPlayback)

        // Transition to Playing
        alarmFireStateFlow.value = AlarmFireState.Playing(
            alarmId = 42L,
            currentSong = testSong
        )
        assertTrue(viewModel.uiState.value.isPlaying)
        assertTrue(viewModel.uiState.value.hasPlayback)
        assertEquals("Test Song", viewModel.uiState.value.currentSongTitle)

        // Transition to Paused
        alarmFireStateFlow.value = AlarmFireState.Paused(
            alarmId = 42L,
            currentSong = testSong,
            positionMs = 10_000L
        )
        assertFalse(viewModel.uiState.value.isPlaying)
        assertTrue(viewModel.uiState.value.hasPlayback)

        // Transition to Stopped
        alarmFireStateFlow.value = AlarmFireState.Stopped
        assertFalse(viewModel.uiState.value.isPlaying)
        assertFalse(viewModel.uiState.value.hasPlayback)
    }
}
