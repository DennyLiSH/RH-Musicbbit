package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.AlarmRingMode
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.domain.model.Playlist
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.domain.repository.AlarmRingSettingsRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.navigation.AlarmEdit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
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
 *   - One-time events (permission dialogs, autostart guide)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmEditViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var alarmRingSettingsRepository: AlarmRingSettingsRepository
    private lateinit var permissionOrchestrator: AlarmEditPermissionOrchestrator

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // WorkManager is initialized in @Before so any leaked AlarmStartupReconciler
        // coroutine from a previous test class (e.g., HiltTestApplication-induced)
        // does not throw "WorkManager is not initialized" — preventing test pollution
        // that would otherwise surface as UncaughtExceptionsBeforeTest.
        try {
            WorkManager.initialize(
                ApplicationProvider.getApplicationContext(),
                Configuration.Builder().build()
            )
        } catch (e: IllegalStateException) {
            // Already initialized by a previous test class — safe to ignore.
        }
        alarmRepository = mock()
        playlistRepository = mock()
        alarmRingSettingsRepository = mock()
        permissionOrchestrator = mock()
        whenever(permissionOrchestrator.checkPermissions(any())).thenReturn(
            AlarmEditPermissionOrchestrator.PermissionCheckResult.AllGranted
        )
        whenever(permissionOrchestrator.checkAutostartGuide()).thenReturn(
            AlarmEditPermissionOrchestrator.AutostartGuideResult.NotApplicable
        )
    }

    @Test
    fun `new alarm has default state`() {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        val state = viewModel.uiState.value
        val form = state.form
        assertEquals("Default hour should be 7", 7, form.hour)
        assertEquals("Default minute should be 30", 30, form.minute)
        assertTrue("Repeat days should be empty", form.repeatDays.isEmpty())
        assertFalse("Exclude holidays should be false", form.excludeHolidays)
        assertEquals("PlaylistId should be 0 (none selected)", 0L, form.playlistId)
        assertEquals("Label should be empty", "", form.label)
        assertNull("AutoStop should be null", form.autoStop)
        assertTrue("isEnabled should be true", form.isEnabled)
        assertTrue("resumePlayback should default to true", form.resumePlayback)
        assertEquals("ringMode should default to Normal", AlarmRingMode.Normal, form.ringMode)
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
            lastTriggeredAt = 1_700_000_000_000L,
            resumePlayback = false,
            ringMode = AlarmRingMode.FullScreen
        )

        wheneverBlocking { alarmRepository.getAlarmById(1L) } doReturn existingAlarm
        whenever(playlistRepository.getAllPlaylists()).thenReturn(
            flowOf(listOf(Playlist(10L, "Work Mix", 0L, 0L)))
        )

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 1L))
        val viewModel = createViewModel(savedStateHandle)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        val form = state.form
        assertFalse("isNewAlarm should be false", state.isNewAlarm)
        assertFalse("isLoading should be false after load", state.isLoading)
        assertEquals("Hour should match alarm", 8, form.hour)
        assertEquals("Minute should match alarm", 15, form.minute)
        assertEquals("Repeat days should match alarm",
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), form.repeatDays)
        assertTrue("Exclude holidays should match alarm", form.excludeHolidays)
        assertEquals("PlaylistId should match alarm", 10L, form.playlistId)
        assertEquals("Label should match alarm", "Work Alarm", form.label)
        assertEquals("AutoStop should match alarm", AutoStop.ByMinutes(20), form.autoStop)
        assertTrue("isEnabled should match alarm", form.isEnabled)
        assertFalse("resumePlayback should match alarm", form.resumePlayback)
        assertEquals("ringMode should match alarm", AlarmRingMode.FullScreen, form.ringMode)
    }

    @Test
    fun `OnResumePlaybackChanged updates form`() {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnResumePlaybackChanged(false))

        assertFalse(viewModel.uiState.value.form.resumePlayback)
    }

    @Test
    fun `OnRingModeChanged updates form`() {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnRingModeChanged(AlarmRingMode.FullScreen))

        assertEquals(AlarmRingMode.FullScreen, viewModel.uiState.value.form.ringMode)
    }

    @Test
    fun `saveAlarm carries resumePlayback and ringMode to repository`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(
            flowOf(listOf(Playlist(10L, "Morning Mix", 0L, 0L)))
        )
        wheneverBlocking { alarmRepository.saveAlarm(any()) } doReturn Result.success(1L)

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnPlaylistSelected(10L))
        viewModel.onAction(AlarmEditAction.OnResumePlaybackChanged(false))
        viewModel.onAction(AlarmEditAction.OnRingModeChanged(AlarmRingMode.FullScreen))
        viewModel.onAction(AlarmEditAction.OnSave)

        advanceUntilIdle()

        val captor = argumentCaptor<Alarm>()
        verifyBlocking(alarmRepository) { saveAlarm(captor.capture()) }
        val savedAlarm = captor.firstValue
        assertFalse(savedAlarm.resumePlayback)
        assertEquals(AlarmRingMode.FullScreen, savedAlarm.ringMode)
    }

    @Test
    fun `load non-existent alarm shows error state`() = runTest {
        wheneverBlocking { alarmRepository.getAlarmById(999L) } doReturn null
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

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
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        // Ensure playlistId is 0 (no playlist selected)
        val beforeState = viewModel.uiState.value
        assertEquals("PlaylistId should be 0", 0L, beforeState.form.playlistId)

        viewModel.onAction(AlarmEditAction.OnSave)

        val state = viewModel.uiState.value
        assertFalse("saveCompleted should be false", state.saveCompleted)
        assertNotNull("errorMessageResId should be set", state.errorMessageResId)
        assertEquals(
            "errorMessageResId should resolve to the playlist-required key",
            R.string.alarm_edit_error_select_playlist,
            state.errorMessageResId
        )
    }

    @Test
    fun `clearError clears error message resId after a prior validation failure`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        // Trigger a validation error first
        viewModel.onAction(AlarmEditAction.OnSave)
        assertNotNull(
            "errorMessageResId should be set after save with no playlist",
            viewModel.uiState.value.errorMessageResId
        )

        viewModel.clearError()

        assertNull(
            "errorMessageResId should be null after clearError",
            viewModel.uiState.value.errorMessageResId
        )
    }

    @Test
    fun `screen first shown has no error message (no proactive validation)`() {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        assertNull(
            "errorMessageResId should be null on initial state",
            viewModel.uiState.value.errorMessageResId
        )
        assertNull(
            "saveFailedMessageResId should be null on initial state",
            viewModel.uiState.value.saveFailedMessageResId
        )
    }

    @Test
    fun `save failure sets saveFailedMessageResId and stays on page`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(
            flowOf(listOf(Playlist(10L, "Morning Mix", 0L, 0L)))
        )
        wheneverBlocking { alarmRepository.saveAlarm(any()) } doReturn
            Result.failure(RuntimeException("DB write failed"))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnPlaylistSelected(10L))
        viewModel.onAction(AlarmEditAction.OnSave)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            "saveFailedMessageResId should be set to alarm_save_failed",
            R.string.alarm_save_failed,
            state.saveFailedMessageResId
        )
        assertFalse("saveCompleted should be false on save failure", state.saveCompleted)
        assertFalse("isSaving should be false after save completes", state.isSaving)
    }

    @Test
    fun `save success sets saveCompleted true`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(
            flowOf(listOf(Playlist(10L, "Morning Mix", 0L, 0L)))
        )
        wheneverBlocking { alarmRepository.saveAlarm(any()) } doReturn Result.success(1L)

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

        verifyBlocking(alarmRepository) { saveAlarm(any()) }
    }

    @Test
    fun `save sets dialogState=Permission when exact alarm permission needed`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(
            flowOf(listOf(Playlist(10L, "Morning Mix", 0L, 0L)))
        )
        whenever(permissionOrchestrator.checkPermissions(any())).thenReturn(
            AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsExactAlarm
        )

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnPlaylistSelected(10L))
        viewModel.onAction(AlarmEditAction.OnSave)
        advanceUntilIdle()

        assertEquals(
            "dialogState should be Permission when exact alarm permission needed",
            AlarmEditDialogState.Permission,
            viewModel.uiState.value.dialogState
        )
        assertFalse("saveCompleted should be false", viewModel.uiState.value.saveCompleted)
    }

    @Test
    fun `save sets dialogState=FullScreenIntent when fsi permission needed`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(
            flowOf(listOf(Playlist(10L, "Morning Mix", 0L, 0L)))
        )
        whenever(permissionOrchestrator.checkPermissions(any())).thenReturn(
            AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsFullScreenIntent
        )

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnPlaylistSelected(10L))
        viewModel.onAction(AlarmEditAction.OnSave)
        advanceUntilIdle()

        assertEquals(
            "dialogState should be FullScreenIntent when fsi permission needed",
            AlarmEditDialogState.FullScreenIntent,
            viewModel.uiState.value.dialogState
        )
        assertFalse("saveCompleted should be false", viewModel.uiState.value.saveCompleted)
    }

    @Test
    fun `save sets dialogState=DndAccess when dnd access needed`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(
            flowOf(listOf(Playlist(10L, "Morning Mix", 0L, 0L)))
        )
        whenever(permissionOrchestrator.checkPermissions(any())).thenReturn(
            AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsDndAccess
        )

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnPlaylistSelected(10L))
        viewModel.onAction(AlarmEditAction.OnSave)
        advanceUntilIdle()

        assertEquals(
            "dialogState should be DndAccess when dnd access needed",
            AlarmEditDialogState.DndAccess,
            viewModel.uiState.value.dialogState
        )
        assertFalse("saveCompleted should be false", viewModel.uiState.value.saveCompleted)
    }

    @Test
    fun `save sets dialogState=AutostartGuide with intent when resolved`() = runTest {
        val mockIntent = mock<android.content.Intent>()
        whenever(playlistRepository.getAllPlaylists()).thenReturn(
            flowOf(listOf(Playlist(10L, "Morning Mix", 0L, 0L)))
        )
        wheneverBlocking { alarmRepository.saveAlarm(any()) } doReturn Result.success(1L)
        whenever(permissionOrchestrator.checkAutostartGuide()).thenReturn(
            AlarmEditPermissionOrchestrator.AutostartGuideResult.Resolved(mockIntent)
        )

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnPlaylistSelected(10L))
        viewModel.onAction(AlarmEditAction.OnSave)

        advanceUntilIdle()

        val dialogState = viewModel.uiState.value.dialogState
        assertTrue("dialogState should be AutostartGuide", dialogState is AlarmEditDialogState.AutostartGuide)
        assertEquals(mockIntent, (dialogState as AlarmEditDialogState.AutostartGuide).intent)
        assertFalse("saveCompleted should be false when guide shown", viewModel.uiState.value.saveCompleted)
    }

    @Test
    fun `save sets dialogState=AutostartManualGuide when manual guide needed`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(
            flowOf(listOf(Playlist(10L, "Morning Mix", 0L, 0L)))
        )
        wheneverBlocking { alarmRepository.saveAlarm(any()) } doReturn Result.success(1L)
        whenever(permissionOrchestrator.checkAutostartGuide()).thenReturn(
            AlarmEditPermissionOrchestrator.AutostartGuideResult.NeedsManualGuide
        )

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnPlaylistSelected(10L))
        viewModel.onAction(AlarmEditAction.OnSave)

        advanceUntilIdle()

        assertEquals(
            "dialogState should be AutostartManualGuide",
            AlarmEditDialogState.AutostartManualGuide,
            viewModel.uiState.value.dialogState
        )
        assertFalse("saveCompleted should be false when guide shown", viewModel.uiState.value.saveCompleted)
    }

    @Test
    fun `onShowTimePicker sets dialogState=TimePicker`() {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.showTimePicker()

        assertEquals(AlarmEditDialogState.TimePicker, viewModel.uiState.value.dialogState)
    }

    @Test
    fun `showDiscardDialog sets dialogState=Discard`() {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.showDiscardDialog()

        assertEquals(AlarmEditDialogState.Discard, viewModel.uiState.value.dialogState)
    }

    @Test
    fun `dismissDialog clears dialogState to null`() {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.showTimePicker()
        viewModel.dismissDialog()

        assertNull(viewModel.uiState.value.dialogState)
    }

    @Test
    fun `dialog state follows LWW semantics`() {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.showTimePicker()
        viewModel.showDiscardDialog()

        assertEquals(
            "Second show should overwrite the first (last-writer-wins)",
            AlarmEditDialogState.Discard,
            viewModel.uiState.value.dialogState
        )
    }

    @Test
    fun `onAction sets hasUnsavedChanges=true for form edits but not for OnSave`() {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("alarmId" to 0L))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.onAction(AlarmEditAction.OnTimeChanged(8, 0))
        assertTrue("Form edit sets hasUnsavedChanges", viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onAction(AlarmEditAction.OnSave)
        assertTrue(
            "OnSave does NOT clear hasUnsavedChanges itself; the screen clears it via navigation",
            viewModel.uiState.value.hasUnsavedChanges
        )
    }

    @Test
    fun `save with repeat days persists correctly`() = runTest {
        whenever(playlistRepository.getAllPlaylists()).thenReturn(
            flowOf(listOf(Playlist(10L, "Morning Mix", 0L, 0L)))
        )
        wheneverBlocking { alarmRepository.saveAlarm(any()) } doReturn Result.success(1L)

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
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), state.form.repeatDays)
    }

    @Test
    fun `volume ramp duration is loaded from repository into uiState`() {
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(flowOf(10))
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

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
        whenever(alarmRingSettingsRepository.getVolumeRampDurationSeconds()).thenReturn(
            flow { throw RuntimeException("DataStore error") }
        )
        whenever(playlistRepository.getAllPlaylists()).thenReturn(flowOf(emptyList()))

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
            alarmRingSettingsRepository = alarmRingSettingsRepository,
            permissionOrchestrator = permissionOrchestrator
        )
    }
}
