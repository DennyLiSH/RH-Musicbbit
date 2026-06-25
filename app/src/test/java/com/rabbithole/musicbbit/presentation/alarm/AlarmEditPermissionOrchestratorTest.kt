package com.rabbithole.musicbbit.presentation.alarm

import android.content.Context
import android.content.Intent
import com.rabbithole.musicbbit.service.AlarmScheduler
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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

    private var fsiMock: MockedStatic<FullScreenIntentPermissionHelper>? = null
    private var autostartMock: MockedStatic<AutostartHelper>? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        alarmScheduler = mock()
        orchestrator = AlarmEditPermissionOrchestrator(context, alarmScheduler)

        fsiMock = mockStatic(FullScreenIntentPermissionHelper::class.java)
        autostartMock = mockStatic(AutostartHelper::class.java)
    }

    @After
    fun tearDown() {
        fsiMock?.close()
        autostartMock?.close()
    }

    @Test
    fun `checkPermissions returns AllGranted when both permissions granted`() {
        whenever(alarmScheduler.canScheduleExactAlarms()).thenReturn(true)
        fsiMock!!.`when`<Boolean> { FullScreenIntentPermissionHelper.isGranted(any()) }.thenReturn(true)

        val result = orchestrator.checkPermissions()

        assertTrue(result is AlarmEditPermissionOrchestrator.PermissionCheckResult.AllGranted)
    }

    @Test
    fun `checkPermissions returns NeedsExactAlarm when exact alarm not granted`() {
        whenever(alarmScheduler.canScheduleExactAlarms()).thenReturn(false)
        fsiMock!!.`when`<Boolean> { FullScreenIntentPermissionHelper.isGranted(any()) }.thenReturn(true)

        val result = orchestrator.checkPermissions()

        assertTrue(result is AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsExactAlarm)
    }

    @Test
    fun `checkPermissions returns NeedsFullScreenIntent when fsi not granted`() {
        whenever(alarmScheduler.canScheduleExactAlarms()).thenReturn(true)
        fsiMock!!.`when`<Boolean> { FullScreenIntentPermissionHelper.isGranted(any()) }.thenReturn(false)

        val result = orchestrator.checkPermissions()

        assertTrue(result is AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsFullScreenIntent)
    }

    @Test
    fun `checkPermissions checks exact alarm before fsi`() {
        whenever(alarmScheduler.canScheduleExactAlarms()).thenReturn(false)
        fsiMock!!.`when`<Boolean> { FullScreenIntentPermissionHelper.isGranted(any()) }.thenReturn(false)

        val result = orchestrator.checkPermissions()

        assertTrue(result is AlarmEditPermissionOrchestrator.PermissionCheckResult.NeedsExactAlarm)
    }

    @Test
    fun `checkAutostartGuide returns NotApplicable on non-Chinese OEM`() {
        autostartMock!!.`when`<Boolean> { AutostartHelper.isChineseOem() }.thenReturn(false)

        val result = orchestrator.checkAutostartGuide()

        assertTrue(result is AlarmEditPermissionOrchestrator.AutostartGuideResult.NotApplicable)
    }

    @Test
    fun `checkAutostartGuide returns Resolved when intent is resolved`() {
        autostartMock!!.`when`<Boolean> { AutostartHelper.isChineseOem() }.thenReturn(true)
        val mockIntent = mock<Intent>()
        autostartMock!!.`when`<AutostartResult> { AutostartHelper.getAutostartResult(any()) }
            .thenReturn(AutostartResult.Resolved(mockIntent))

        val result = orchestrator.checkAutostartGuide()

        assertTrue(result is AlarmEditPermissionOrchestrator.AutostartGuideResult.Resolved)
        val resolved = result as AlarmEditPermissionOrchestrator.AutostartGuideResult.Resolved
        assertEquals(mockIntent, resolved.intent)
    }

    @Test
    fun `checkAutostartGuide returns NeedsManualGuide when no intent resolved`() {
        autostartMock!!.`when`<Boolean> { AutostartHelper.isChineseOem() }.thenReturn(true)
        autostartMock!!.`when`<AutostartResult> { AutostartHelper.getAutostartResult(any()) }
            .thenReturn(AutostartResult.NeedsManualGuide)

        val result = orchestrator.checkAutostartGuide()

        assertTrue(result is AlarmEditPermissionOrchestrator.AutostartGuideResult.NeedsManualGuide)
    }
}
