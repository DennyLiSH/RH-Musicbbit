package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import android.content.Intent
import com.rabbithole.musicbbit.service.AlarmScheduler
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmEditPermissionOrchestratorTest {

    private lateinit var context: Context
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var orchestrator: AlarmEditPermissionOrchestrator

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        alarmScheduler = mockk(relaxed = true)
        orchestrator = AlarmEditPermissionOrchestrator(context, alarmScheduler)

        mockkObject(FullScreenIntentPermissionHelper)
        mockkObject(AutostartHelper)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `checkPermissions returns AllGranted when both permissions granted`() {
        every { alarmScheduler.canScheduleExactAlarms() } returns true
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val result = orchestrator.checkPermissions()

        assertTrue(result is AlarmEditPermissionOrchestrator.PermissionCheckResult.AllGranted)
    }

    @Test
    fun `checkPermissions returns NeedsExactAlarm when exact alarm not granted`() {
        every { alarmScheduler.canScheduleExactAlarms() } returns false
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns true

        val result = orchestrator.checkPermissions()

        assertTrue(result is AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsExactAlarm)
    }

    @Test
    fun `checkPermissions returns NeedsFullScreenIntent when fsi not granted`() {
        every { alarmScheduler.canScheduleExactAlarms() } returns true
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns false

        val result = orchestrator.checkPermissions()

        assertTrue(result is AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsFullScreenIntent)
    }

    @Test
    fun `checkPermissions checks exact alarm before fsi`() {
        every { alarmScheduler.canScheduleExactAlarms() } returns false
        every { FullScreenIntentPermissionHelper.isGranted(any()) } returns false

        val result = orchestrator.checkPermissions()

        assertTrue(result is AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsExactAlarm)
    }

    @Test
    fun `checkAutostartGuide returns NotApplicable on non-Chinese OEM`() {
        every { AutostartHelper.isChineseOem() } returns false

        val result = orchestrator.checkAutostartGuide()

        assertTrue(result is AlarmEditPermissionOrchestrator.AutostartGuideResult.NotApplicable)
    }

    @Test
    fun `checkAutostartGuide returns Resolved when intent is resolved`() {
        every { AutostartHelper.isChineseOem() } returns true
        val mockIntent = mockk<Intent>(relaxed = true)
        every { AutostartHelper.getAutostartResult(any()) } returns AutostartResult.Resolved(mockIntent)

        val result = orchestrator.checkAutostartGuide()

        assertTrue(result is AlarmEditPermissionOrchestrator.AutostartGuideResult.Resolved)
        val resolved = result as AlarmEditPermissionOrchestrator.AutostartGuideResult.Resolved
        assertEquals(mockIntent, resolved.intent)
    }

    @Test
    fun `checkAutostartGuide returns NeedsManualGuide when no intent resolved`() {
        every { AutostartHelper.isChineseOem() } returns true
        every { AutostartHelper.getAutostartResult(any()) } returns AutostartResult.NeedsManualGuide

        val result = orchestrator.checkAutostartGuide()

        assertTrue(result is AlarmEditPermissionOrchestrator.AutostartGuideResult.NeedsManualGuide)
    }
}
