package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/**
 * Robolectric tests for [AlarmListViewModel].
 *
 * Covers:
 *   - Full-screen intent (FSI) permission state
 *   - Alarm list loading (success with data, empty list)
 *   - Delete alarm action
 *   - Toggle enabled action
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var holidayRepository: HolidayRepository
    private lateinit var playlistRepository: PlaylistRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        alarmRepository = mockk {
            every { getAllAlarms() } returns flowOf(emptyList())
        }
        holidayRepository = mockk(relaxed = true)
        playlistRepository = mockk(relaxed = true)
        mockkObject(FullScreenIntentPermissionHelper)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------- FSI permission tests (existing) --------------------------------

    @Test
    fun `fullScreenIntentGranted is true on API below 34`() {
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val viewModel = createViewModel()

        assertTrue(viewModel.isFullScreenIntentGranted.value)
    }

    @Test
    fun `fullScreenIntentGranted reflects helper result granted on API 34+`() {
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val viewModel = createViewModel()

        assertTrue(viewModel.isFullScreenIntentGranted.value)
    }

    @Test
    fun `fullScreenIntentGranted reflects helper result denied on API 34+`() {
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns false

        val viewModel = createViewModel()

        assertFalse(viewModel.isFullScreenIntentGranted.value)
    }

    @Test
    fun `refreshFullScreenIntentStatus updates state`() {
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns false

        val viewModel = createViewModel()
        assertFalse(viewModel.isFullScreenIntentGranted.value)

        // User grants permission in settings
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true
        viewModel.refreshFullScreenIntentStatus()

        assertTrue(viewModel.isFullScreenIntentGranted.value)
    }

    // -------- Alarm list loading tests ---------------------------------------

    @Test
    fun `alarm list loading emits Success with correct AlarmItems`() {
        val alarm1 = Alarm(
            id = 1L,
            hour = 7,
            minute = 30,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            excludeHolidays = false,
            playlistId = 10L,
            isEnabled = true,
            label = "Morning Alarm",
            autoStop = null,
            lastTriggeredAt = null
        )
        val alarm2 = Alarm(
            id = 2L,
            hour = 22,
            minute = 0,
            repeatDays = emptySet(),
            excludeHolidays = false,
            playlistId = 20L,
            isEnabled = false,
            label = "Bedtime",
            autoStop = AutoStop.ByMinutes(30),
            lastTriggeredAt = 1_700_000_000_000L
        )

        every { alarmRepository.getAllAlarms() } returns flowOf(listOf(alarm1, alarm2))
        io.mockk.coEvery { playlistRepository.getPlaylistById(10L) } returns Playlist(10L, "Workout Mix", 0L, 0L)
        io.mockk.coEvery { playlistRepository.getPlaylistById(20L) } returns Playlist(20L, "Sleep Sounds", 0L, 0L)
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val viewModel = createViewModel()

        val uiState = viewModel.uiState.value
        assertTrue("Expected Success state", uiState is AlarmListUiState.Success)
        val success = uiState as AlarmListUiState.Success
        assertEquals("Should have 2 alarm items", 2, success.alarms.size)

        val item1 = success.alarms[0]
        assertEquals(alarm1, item1.alarm)
        assertEquals("Workout Mix", item1.playlistName)

        val item2 = success.alarms[1]
        assertEquals(alarm2, item2.alarm)
        assertEquals("Sleep Sounds", item2.playlistName)
    }

    @Test
    fun `empty alarm list emits Success with empty list`() {
        every { alarmRepository.getAllAlarms() } returns flowOf(emptyList())
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val viewModel = createViewModel()

        val uiState = viewModel.uiState.value
        assertTrue("Expected Success state", uiState is AlarmListUiState.Success)
        val success = uiState as AlarmListUiState.Success
        assertTrue("Alarm list should be empty", success.alarms.isEmpty())
    }

    // -------- Delete alarm test ----------------------------------------------

    @Test
    fun `onAction OnDeleteAlarm calls repository deleteAlarm`() = runTest {
        val alarm = Alarm(
            id = 1L,
            hour = 7,
            minute = 0,
            repeatDays = setOf(DayOfWeek.MONDAY),
            excludeHolidays = false,
            playlistId = 10L,
            isEnabled = true,
            label = "Test",
            autoStop = null,
            lastTriggeredAt = null
        )
        every { alarmRepository.getAllAlarms() } returns flowOf(listOf(alarm))
        io.mockk.coEvery { alarmRepository.deleteAlarm(alarm) } returns Result.success(Unit)
        io.mockk.coEvery { playlistRepository.getPlaylistById(10L) } returns Playlist(10L, "Test Playlist", 0L, 0L)
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val viewModel = createViewModel()
        viewModel.onAction(AlarmListAction.OnDeleteAlarm(alarm))

        io.mockk.coVerify { alarmRepository.deleteAlarm(alarm) }
    }

    // -------- Toggle enabled test --------------------------------------------

    @Test
    fun `onAction OnToggleEnabled calls repository enableAlarm`() = runTest {
        val alarm = Alarm(
            id = 1L,
            hour = 7,
            minute = 0,
            repeatDays = setOf(DayOfWeek.MONDAY),
            excludeHolidays = false,
            playlistId = 10L,
            isEnabled = true,
            label = "Test",
            autoStop = null,
            lastTriggeredAt = null
        )
        every { alarmRepository.getAllAlarms() } returns flowOf(listOf(alarm))
        io.mockk.coEvery { alarmRepository.enableAlarm(1L, false) } returns Result.success(Unit)
        io.mockk.coEvery { playlistRepository.getPlaylistById(10L) } returns Playlist(10L, "Test Playlist", 0L, 0L)
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val viewModel = createViewModel()
        viewModel.onAction(AlarmListAction.OnToggleEnabled(alarmId = 1L, enabled = false))

        io.mockk.coVerify { alarmRepository.enableAlarm(1L, false) }
    }

    // -------- Retry test -----------------------------------------------------

    @Test
    fun `retry reloads alarms after error`() = runTest {
        val errorFlow = kotlinx.coroutines.flow.flow { throw RuntimeException("DB error") }
        every { alarmRepository.getAllAlarms() } returns errorFlow
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AlarmListUiState.Error)

        every { alarmRepository.getAllAlarms() } returns flowOf(emptyList())
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AlarmListUiState.Success)
    }

    private fun createViewModel(): AlarmListViewModel {
        return AlarmListViewModel(
            alarmRepository = alarmRepository,
            holidayRepository = holidayRepository,
            playlistRepository = playlistRepository,
            context = context
        )
    }
}
