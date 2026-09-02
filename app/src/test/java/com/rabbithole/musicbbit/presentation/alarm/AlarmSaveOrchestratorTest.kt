package com.rabbithole.musicbbit.presentation.alarm

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/**
 * Unit tests for [AlarmSaveOrchestrator] covering all five [SaveOutcome] branches
 * (MissingPlaylist, NeedsExactAlarmPermission, NeedsFullScreenIntentPermission,
 * Success, Failure).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmSaveOrchestratorTest {

    private val alarmRepository: AlarmRepository = mock()
    private val permissionOrchestrator: AlarmEditPermissionOrchestrator = mock()
    private lateinit var orchestrator: AlarmSaveOrchestrator

    private val validAlarm = Alarm(
        id = 0L,
        hour = 7,
        minute = 30,
        repeatDays = emptySet<DayOfWeek>(),
        excludeHolidays = false,
        playlistId = 5L,
        isEnabled = true,
        label = "Test",
        autoStop = null,
        lastTriggeredAt = null,
        resumePlayback = true,
        ringMode = com.rabbithole.musicbbit.domain.model.AlarmRingMode.Normal,
    )

    @Before
    fun setUp() {
        // WorkManager initialization prevents "WorkManager is not initialized"
        // UncaughtExceptionsBeforeTest when shared HiltTestApplication state leaks
        // across test classes.
        try {
            WorkManager.initialize(
                ApplicationProvider.getApplicationContext(),
                Configuration.Builder().build()
            )
        } catch (e: IllegalStateException) {
            // Already initialized — safe to ignore.
        }
        orchestrator = AlarmSaveOrchestrator(alarmRepository, permissionOrchestrator)
        whenever(permissionOrchestrator.checkPermissions(any())).thenReturn(
            AlarmEditPermissionOrchestrator.PermissionCheckResult.AllGranted
        )
    }

    @Test
    fun `playlistId le zero returns MissingPlaylist`() = runTest {
        val outcome = orchestrator.save(validAlarm, playlistId = 0L)
        assertTrue(outcome is AlarmSaveOrchestrator.SaveOutcome.MissingPlaylist)
    }

    @Test
    fun `negative playlistId also returns MissingPlaylist`() = runTest {
        val outcome = orchestrator.save(validAlarm, playlistId = -1L)
        assertTrue(outcome is AlarmSaveOrchestrator.SaveOutcome.MissingPlaylist)
    }

    @Test
    fun `needs exact alarm permission short-circuits`() = runTest {
        whenever(permissionOrchestrator.checkPermissions(any())).thenReturn(
            AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsExactAlarm
        )

        val outcome = orchestrator.save(validAlarm, playlistId = 5L)

        assertTrue(outcome is AlarmSaveOrchestrator.SaveOutcome.NeedsExactAlarmPermission)
    }

    @Test
    fun `needs full screen intent permission short-circuits`() = runTest {
        whenever(permissionOrchestrator.checkPermissions(any())).thenReturn(
            AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsFullScreenIntent
        )

        val outcome = orchestrator.save(validAlarm, playlistId = 5L)

        assertTrue(outcome is AlarmSaveOrchestrator.SaveOutcome.NeedsFullScreenIntentPermission)
    }

    @Test
    fun `saveAlarm success with autostart Resolved returns Success with Resolved outcome`() = runTest {
        wheneverBlocking { alarmRepository.saveAlarm(any()) } doReturn Result.success(42L)
        whenever(permissionOrchestrator.checkAutostartGuide()).thenReturn(
            AlarmEditPermissionOrchestrator.AutostartGuideResult.Resolved(Intent())
        )

        val outcome = orchestrator.save(validAlarm, playlistId = 5L)

        assertTrue(outcome is AlarmSaveOrchestrator.SaveOutcome.Success)
        val autostart = (outcome as AlarmSaveOrchestrator.SaveOutcome.Success).autostart
        assertTrue("Expected Resolved autostart", autostart is AlarmSaveOrchestrator.AutostartOutcome.Resolved)
        assertNotNull("Resolved intent should be carried through", (autostart as AlarmSaveOrchestrator.AutostartOutcome.Resolved).intent)
    }

    @Test
    fun `saveAlarm success with autostart NeedsManualGuide returns Success with NeedsManualGuide`() = runTest {
        wheneverBlocking { alarmRepository.saveAlarm(any()) } doReturn Result.success(42L)
        whenever(permissionOrchestrator.checkAutostartGuide()).thenReturn(
            AlarmEditPermissionOrchestrator.AutostartGuideResult.NeedsManualGuide
        )

        val outcome = orchestrator.save(validAlarm, playlistId = 5L)

        assertTrue(outcome is AlarmSaveOrchestrator.SaveOutcome.Success)
        assertTrue(
            (outcome as AlarmSaveOrchestrator.SaveOutcome.Success).autostart ==
                AlarmSaveOrchestrator.AutostartOutcome.NeedsManualGuide
        )
    }

    @Test
    fun `saveAlarm success with autostart NotApplicable returns Success with NotApplicable`() = runTest {
        wheneverBlocking { alarmRepository.saveAlarm(any()) } doReturn Result.success(42L)
        whenever(permissionOrchestrator.checkAutostartGuide()).thenReturn(
            AlarmEditPermissionOrchestrator.AutostartGuideResult.NotApplicable
        )

        val outcome = orchestrator.save(validAlarm, playlistId = 5L)

        assertTrue(outcome is AlarmSaveOrchestrator.SaveOutcome.Success)
        assertTrue(
            (outcome as AlarmSaveOrchestrator.SaveOutcome.Success).autostart ==
                AlarmSaveOrchestrator.AutostartOutcome.NotApplicable
        )
    }

    @Test
    fun `saveAlarm failure returns Failure with error resource id`() = runTest {
        wheneverBlocking { alarmRepository.saveAlarm(any()) } doReturn Result.failure(
            RuntimeException("DB write failed")
        )

        val outcome = orchestrator.save(validAlarm, playlistId = 5L)

        assertTrue(outcome is AlarmSaveOrchestrator.SaveOutcome.Failure)
        assertEquals(
            R.string.alarm_edit_error_save_failed,
            (outcome as AlarmSaveOrchestrator.SaveOutcome.Failure).errorResId
        )
    }
}
