package com.rabbithole.musicbbit.presentation.alarm

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import java.time.DayOfWeek

/**
 * Unit tests for [AlarmListViewModel].
 *
 * Covers:
 *   - Full-screen intent (FSI) permission state
 *   - Alarm list loading (success with data, empty list)
 *   - Delete alarm action
 *   - Toggle enabled action
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var permissionPort: PermissionPort
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var holidayRepository: HolidayRepository
    private lateinit var playlistRepository: PlaylistRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        permissionPort = mock {
            whenever(it.isFullScreenIntentGranted()).thenReturn(true)
            whenever(it.isIgnoringBatteryOptimizations()).thenReturn(true)
            whenever(it.isNotificationPolicyAccessGranted()).thenReturn(true)
        }
        alarmRepository = mock {
            whenever(it.getAllAlarms()).thenReturn(flowOf(emptyList()))
        }
        holidayRepository = mock()
        playlistRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------- FSI permission tests (existing) --------------------------------

    @Test
    fun `fullScreenIntentGranted is true when permission port returns true`() {
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()

        assertTrue(viewModel.isFullScreenIntentGranted.value)
    }

    @Test
    fun `fullScreenIntentGranted reflects permission port result granted`() {
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()

        assertTrue(viewModel.isFullScreenIntentGranted.value)
    }

    @Test
    fun `fullScreenIntentGranted reflects permission port result denied`() {
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(false)

        val viewModel = createViewModel()

        assertFalse(viewModel.isFullScreenIntentGranted.value)
    }

    @Test
    fun `refreshFullScreenIntentStatus updates state`() {
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(false)

        val viewModel = createViewModel()
        assertFalse(viewModel.isFullScreenIntentGranted.value)

        // User grants permission in settings
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)
        viewModel.refreshFullScreenIntentStatus()

        assertTrue(viewModel.isFullScreenIntentGranted.value)
    }

    @Test
    fun `isDndAccessGranted reflects permission port result`() {
        whenever(permissionPort.isNotificationPolicyAccessGranted()).thenReturn(false)

        val viewModel = createViewModel()

        assertFalse(viewModel.isDndAccessGranted.value)
    }

    @Test
    fun `refreshDndAccessStatus updates state after grant`() {
        whenever(permissionPort.isNotificationPolicyAccessGranted()).thenReturn(false)
        val viewModel = createViewModel()
        assertFalse(viewModel.isDndAccessGranted.value)

        // User grants DND access in settings, then returns to the app (ON_RESUME)
        whenever(permissionPort.isNotificationPolicyAccessGranted()).thenReturn(true)
        viewModel.refreshDndAccessStatus()

        assertTrue(viewModel.isDndAccessGranted.value)
    }

    // -------- Alarm list loading tests ---------------------------------------

    @Test
    fun `alarm list loading emits Success with correct AlarmItems`() = runTest {
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

        whenever(alarmRepository.getAllAlarms()).thenReturn(flowOf(listOf(alarm1, alarm2)))
        wheneverBlocking { playlistRepository.getPlaylistById(10L) } doReturn Playlist(10L, "Workout Mix", 0L, 0L)
        wheneverBlocking { playlistRepository.getPlaylistById(20L) } doReturn Playlist(20L, "Sleep Sounds", 0L, 0L)
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

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
    fun `empty alarm list emits Success with empty list`() = runTest {
        whenever(alarmRepository.getAllAlarms()).thenReturn(flowOf(emptyList()))
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

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
        whenever(alarmRepository.getAllAlarms()).thenReturn(flowOf(listOf(alarm)))
        wheneverBlocking { alarmRepository.deleteAlarm(alarm) } doReturn Result.success(Unit)
        wheneverBlocking { playlistRepository.getPlaylistById(10L) } doReturn Playlist(10L, "Test Playlist", 0L, 0L)
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        viewModel.onAction(AlarmListAction.OnDeleteAlarm(alarm))

        verifyBlocking(alarmRepository) { deleteAlarm(alarm) }
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
        whenever(alarmRepository.getAllAlarms()).thenReturn(flowOf(listOf(alarm)))
        wheneverBlocking { alarmRepository.enableAlarm(1L, false) } doReturn Result.success(Unit)
        wheneverBlocking { playlistRepository.getPlaylistById(10L) } doReturn Playlist(10L, "Test Playlist", 0L, 0L)
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        viewModel.onAction(AlarmListAction.OnToggleEnabled(alarmId = 1L, enabled = false))

        verifyBlocking(alarmRepository) { enableAlarm(1L, false) }
    }

    // -------- Retry test -----------------------------------------------------

    @Test
    fun `retry reloads alarms after error`() = runTest {
        val errorFlow = kotlinx.coroutines.flow.flow<List<Alarm>> { throw RuntimeException("DB error") }
        whenever(alarmRepository.getAllAlarms()).thenReturn(errorFlow)
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AlarmListUiState.Error)

        whenever(alarmRepository.getAllAlarms()).thenReturn(flowOf(emptyList()))
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AlarmListUiState.Success)
    }

    private fun createViewModel(): AlarmListViewModel {
        return AlarmListViewModel(
            alarmRepository = alarmRepository,
            holidayRepository = holidayRepository,
            playlistRepository = playlistRepository,
            permissionPort = permissionPort
        )
    }
}
