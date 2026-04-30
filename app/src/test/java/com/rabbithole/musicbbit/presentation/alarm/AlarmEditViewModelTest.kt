package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.navigation.AlarmEdit
import com.rabbithole.musicbbit.service.AlarmScheduler
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/**
 * Robolectric tests for [AlarmEditViewModel].
 *
 * Covers:
 *   - New alarm default state (alarmId = 0)
 *   - Load existing alarm (alarmId != 0)
 *   - Load non-existent alarm (error state)
 *   - Save validation fail (no playlist selected)
 *   - Save success
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmEditViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var alarmRingSettingsRepository: AlarmRingSettingsRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        alarmRepository = mockk(relaxed = true)
        playlistRepository = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        alarmRingSettingsRepository = mockk(relaxed = true)
        mockkObject(FullScreenIntentPermissionHelper)
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true
        every { alarmScheduler.canScheduleExactAlarms() } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `new alarm has default state`() {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        val state = viewModel.uiState.value
        assertEquals("Default hour should be 7", 7, state.hour)
        assertEquals("Default minute should be 30", 30, state.minute)
        assertTrue("Repeat days should be empty", state.repeatDays.isEmpty())
        assertFalse("Exclude holidays should be false", state.excludeHolidays)
        assertEquals("PlaylistId should be 0 (none selected)", 0L, state.playlistId)
        assertEquals("Label should be empty", "", state.label)
        assertNull("AutoStop should be null", state.autoStop)
        assertTrue("isEnabled should be true", state.isEnabled)
        assertTrue("isNewAlarm should be true", state.isNewAlarm)
        assertFalse("isLoading should be false", state.isLoading)
        assertFalse("isSaving should be false", state.isSaving)
        assertFalse("saveCompleted should be false", state.saveCompleted)
        assertNull("errorMessageResId should be null", state.errorMessageResId)
    }

    @Test
    fun `load existing alarm populates uiState`() = runTest {
        val existingAlarm = Alarm(
            id = 1L,
            hour = 8,
            minute = 15,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            excludeHolidays = true,
            playlistId = 10L,
            isEnabled = true,
            label = "Work Alarm",
            autoStop = AutoStop.ByMinutes(20),
            lastTriggeredAt = 1_700_000_000_000L
        )

        io.mockk.coEvery { alarmRepository.getAlarmById(1L) } returns existingAlarm
        every { playlistRepository.getAllPlaylists() } returns flowOf(
            listOf(Playlist(10L, "Work Mix", 0L, 0L))
        )

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 1L))
        val viewModel = createViewModel(savedStateHandle)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("isNewAlarm should be false", state.isNewAlarm)
        assertFalse("isLoading should be false after load", state.isLoading)
        assertEquals("Hour should match alarm", 8, state.hour)
        assertEquals("Minute should match alarm", 15, state.minute)
        assertEquals("Repeat days should match alarm",
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), state.repeatDays)
        assertTrue("Exclude holidays should match alarm", state.excludeHolidays)
        assertEquals("PlaylistId should match alarm", 10L, state.playlistId)
        assertEquals("Label should match alarm", "Work Alarm", state.label)
        assertEquals("AutoStop should match alarm", AutoStop.ByMinutes(20), state.autoStop)
        assertTrue("isEnabled should match alarm", state.isEnabled)
    }

    @Test
    fun `load non-existent alarm shows error state`() = runTest {
        io.mockk.coEvery { alarmRepository.getAlarmById(999L) } returns null
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 999L))
        val viewModel = createViewModel(savedStateHandle)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("isLoading should be false after load attempt", state.isLoading)
        assertFalse("isNewAlarm should be false", state.isNewAlarm)
        assertNotNull("errorMessageResId should be set for missing alarm", state.errorMessageResId)
    }

    @Test
    fun `save validation fails when no playlist selected`() = runTest {
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        // Ensure playlistId is 0 (no playlist selected)
        val beforeState = viewModel.uiState.value
        assertEquals("PlaylistId should be 0", 0L, beforeState.playlistId)

        viewModel.onAction(AlarmEditAction.OnSave)

        val state = viewModel.uiState.value
        assertFalse("saveCompleted should be false", state.saveCompleted)
        assertNotNull("errorMessageResId should be set", state.errorMessageResId)
    }

    @Test
    fun `save success sets saveCompleted true`() = runTest {
        every { playlistRepository.getAllPlaylists() } returns flowOf(
            listOf(Playlist(10L, "Morning Mix", 0L, 0L))
        )
        io.mockk.coEvery { alarmRepository.saveAlarm(any()) } returns Result.success(1L)

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        // Set all required fields
        viewModel.onAction(AlarmEditAction.OnTimeChanged(8, 0))
        viewModel.onAction(AlarmEditAction.OnPlaylistSelected(10L))
        viewModel.onAction(AlarmEditAction.OnLabelChanged("Test Alarm"))

        viewModel.onAction(AlarmEditAction.OnSave)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("saveCompleted should be true", state.saveCompleted)
        assertFalse("isSaving should be false after save", state.isSaving)
        assertNull("errorMessageResId should be null on success", state.errorMessageResId)

        io.mockk.coVerify { alarmRepository.saveAlarm(any()) }
    }

    @Test
    fun `save with repeat days persists correctly`() = runTest {
        every { playlistRepository.getAllPlaylists() } returns flowOf(
            listOf(Playlist(10L, "Morning Mix", 0L, 0L))
        )
        io.mockk.coEvery { alarmRepository.saveAlarm(any()) } returns Result.success(1L)

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnTimeChanged(6, 30))
        viewModel.onAction(AlarmEditAction.OnPlaylistSelected(10L))
        viewModel.onAction(AlarmEditAction.OnRepeatDaysChanged(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        ))

        viewModel.onAction(AlarmEditAction.OnSave)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("saveCompleted should be true", state.saveCompleted)
        assertEquals("Repeat days should be preserved",
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), state.repeatDays)
    }

    @Test
    fun `volume ramp duration is loaded from repository into uiState`() {
        every { alarmRingSettingsRepository.getVolumeRampDurationSeconds() } returns flowOf(10)
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        assertEquals(
            "volumeRampDurationSeconds should match repository value",
            10,
            viewModel.uiState.value.volumeRampDurationSeconds
        )
    }

    @Test
    fun `volume ramp duration falls back to zero on repository error`() {
        every { alarmRingSettingsRepository.getVolumeRampDurationSeconds() } returns
            flow { throw RuntimeException("DataStore error") }
        every { playlistRepository.getAllPlaylists() } returns flowOf(emptyList())

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        assertEquals(
            "volumeRampDurationSeconds should fall back to 0 on error",
            0,
            viewModel.uiState.value.volumeRampDurationSeconds
        )
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle): AlarmEditViewModel {
        return AlarmEditViewModel(
            context = context,
            savedStateHandle = savedStateHandle,
            alarmRepository = alarmRepository,
            playlistRepository = playlistRepository,
            alarmScheduler = alarmScheduler,
            alarmRingSettingsRepository = alarmRingSettingsRepository
        )
    }
}
