package com.rabbithole.musicbbit.presentation.alarm

import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AlarmWithPlaylistName
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.HolidayRepository
import com.rabbithole.musicbbit.presentation.components.ListUiState
import com.rabbithole.musicbbit.presentation.components.UserMessage
import com.rabbithole.musicbbit.presentation.permissions.PermissionStatusMonitor
import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var permissionPort: PermissionPort
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var holidayRepository: HolidayRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        permissionPort = mock {
            whenever(it.isFullScreenIntentGranted()).thenReturn(true)
            whenever(it.isIgnoringBatteryOptimizations()).thenReturn(true)
            whenever(it.isNotificationPolicyAccessGranted()).thenReturn(true)
        }
        alarmRepository = mock {
            whenever(it.getAlarmsWithPlaylistName()).thenReturn(flowOf(emptyList()))
        }
        holidayRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun alarmEntity(
        id: Long = 1L,
        playlistId: Long = 10L,
        label: String = "Test",
        hour: Int = 7,
    ) = Alarm(
        id = id,
        hour = hour,
        minute = 0,
        repeatDays = setOf(DayOfWeek.MONDAY),
        excludeHolidays = false,
        playlistId = playlistId,
        isEnabled = true,
        label = label,
        autoStop = null,
        lastTriggeredAt = null,
    )

    // -------- FSI permission tests (existing) --------------------------------

    @Test
    fun `fullScreenIntentGranted is true when permission port returns true`() {
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()

        assertTrue(viewModel.permissionStatus.value.isFullScreenIntentGranted)
    }

    @Test
    fun `fullScreenIntentGranted reflects permission port result granted`() {
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()

        assertTrue(viewModel.permissionStatus.value.isFullScreenIntentGranted)
    }

    @Test
    fun `fullScreenIntentGranted reflects permission port result denied`() {
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(false)

        val viewModel = createViewModel()

        assertFalse(viewModel.permissionStatus.value.isFullScreenIntentGranted)
    }

    @Test
    fun `refreshFullScreenIntentStatus updates state`() {
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(false)

        val viewModel = createViewModel()
        assertFalse(viewModel.permissionStatus.value.isFullScreenIntentGranted)

        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)
        viewModel.refreshPermissionStatus()

        assertTrue(viewModel.permissionStatus.value.isFullScreenIntentGranted)
    }

    @Test
    fun `isDndAccessGranted reflects permission port result`() {
        whenever(permissionPort.isNotificationPolicyAccessGranted()).thenReturn(false)

        val viewModel = createViewModel()

        assertFalse(viewModel.permissionStatus.value.isDndAccessGranted)
    }

    @Test
    fun `refreshDndAccessStatus updates state after grant`() {
        whenever(permissionPort.isNotificationPolicyAccessGranted()).thenReturn(false)
        val viewModel = createViewModel()
        assertFalse(viewModel.permissionStatus.value.isDndAccessGranted)

        whenever(permissionPort.isNotificationPolicyAccessGranted()).thenReturn(true)
        viewModel.refreshPermissionStatus()

        assertTrue(viewModel.permissionStatus.value.isDndAccessGranted)
    }

    // -------- Alarm list loading tests ---------------------------------------

    @Test
    fun `alarm list loading emits Content with joined playlist names`() = runTest {
        val alarm1 = alarmEntity(id = 1L, playlistId = 10L, label = "Morning Alarm", hour = 7)
        val alarm2 = alarmEntity(id = 2L, playlistId = 20L, label = "Bedtime", hour = 22)

        val items = listOf(
            AlarmWithPlaylistName(alarm = alarm1, playlistName = "Workout Mix"),
            AlarmWithPlaylistName(alarm = alarm2, playlistName = "Sleep Sounds"),
        )
        whenever(alarmRepository.getAlarmsWithPlaylistName()).thenReturn(flowOf(items))
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue("Expected Content state", uiState is ListUiState.Content)
        val content = uiState as ListUiState.Content
        assertEquals("Should have 2 alarm items", 2, content.data.size)

        val item1 = content.data[0]
        assertEquals(alarm1, item1.alarm)
        assertEquals("Workout Mix", item1.playlistName)

        val item2 = content.data[1]
        assertEquals(alarm2, item2.alarm)
        assertEquals("Sleep Sounds", item2.playlistName)
    }

    @Test
    fun `empty alarm list emits Content with empty list`() = runTest {
        whenever(alarmRepository.getAlarmsWithPlaylistName()).thenReturn(flowOf(emptyList()))
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue("Expected Content state", uiState is ListUiState.Content)
        val content = uiState as ListUiState.Content
        assertTrue("Alarm list should be empty", content.data.isEmpty())
    }

    @Test
    fun `playlistName null when underlying playlist deleted`() = runTest {
        val alarm = alarmEntity(id = 1L, playlistId = 999L)
        // Repository signals "playlist deleted" by returning null for that playlist.
        val items = listOf(AlarmWithPlaylistName(alarm = alarm, playlistName = null))
        whenever(alarmRepository.getAlarmsWithPlaylistName()).thenReturn(flowOf(items))
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val content = viewModel.uiState.value as ListUiState.Content
        assertNull("playlistName must be null when repository signals deleted playlist", content.data[0].playlistName)
    }

    // -------- Delete alarm test ----------------------------------------------

    @Test
    fun `onAction OnDeleteAlarm calls repository deleteAlarm`() = runTest {
        val alarm = alarmEntity()
        whenever(alarmRepository.getAlarmsWithPlaylistName()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { alarmRepository.deleteAlarm(alarm) } doReturn Result.success(Unit)
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        viewModel.onAction(AlarmListAction.OnDeleteAlarm(alarm))

        verifyBlocking(alarmRepository) { deleteAlarm(alarm) }
    }

    // -------- Toggle enabled test --------------------------------------------

    @Test
    fun `onAction OnToggleEnabled calls repository enableAlarm`() = runTest {
        val alarm = alarmEntity()
        whenever(alarmRepository.getAlarmsWithPlaylistName()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { alarmRepository.enableAlarm(1L, false) } doReturn Result.success(Unit)
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        viewModel.onAction(AlarmListAction.OnToggleEnabled(alarmId = 1L, enabled = false))

        verifyBlocking(alarmRepository) { enableAlarm(1L, false) }
    }

    // -------- Toggle enabled failure emits UserMessage ------------------------

    @Test
    fun `onAction OnToggleEnabled failure emits UserMessage with enable failed`() = runTest {
        val alarm = alarmEntity()
        whenever(alarmRepository.getAlarmsWithPlaylistName()).thenReturn(flowOf(emptyList()))
        wheneverBlocking { alarmRepository.enableAlarm(1L, false) } doReturn Result.failure(RuntimeException("DB error"))
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        viewModel.onAction(AlarmListAction.OnToggleEnabled(alarmId = 1L, enabled = false))
        advanceUntilIdle()

        val message = viewModel.messages.first()
        assertEquals(UserMessage(R.string.alarm_error_enable_failed), message)
    }

    // -------- Retry test -----------------------------------------------------

    @Test
    fun `retry reloads alarms after error`() = runTest {
        val errorFlow = kotlinx.coroutines.flow.flow<List<AlarmWithPlaylistName>> {
            throw RuntimeException("DB error")
        }
        whenever(alarmRepository.getAlarmsWithPlaylistName()).thenReturn(errorFlow)
        whenever(permissionPort.isFullScreenIntentGranted()).thenReturn(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Replace stub with success flow before retry.
        whenever(alarmRepository.getAlarmsWithPlaylistName()).thenReturn(flowOf(emptyList()))
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(
            "after retry with success stub, uiState should be Content (was: ${viewModel.uiState.value})",
            viewModel.uiState.value is ListUiState.Content
        )
    }

    private fun createViewModel(): AlarmListViewModel {
        return AlarmListViewModel(
            alarmRepository = alarmRepository,
            holidayRepository = holidayRepository,
            permissionMonitor = PermissionStatusMonitor(permissionPort),
        )
    }
}