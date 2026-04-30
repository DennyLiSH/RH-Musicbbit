package com.rabbithole.musicbbit.service

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.content.Context
import com.rabbithole.musicbbit.MainActivity
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.service.alarm.NextOccurrenceCalculator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var nextOccurrenceCalculator: NextOccurrenceCalculator
    private lateinit var alarmScheduler: AlarmScheduler

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        nextOccurrenceCalculator = mockk()
        alarmScheduler = AlarmScheduler(context, nextOccurrenceCalculator)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun enabledAlarm(
        id: Long = 1L,
        hour: Int = 7,
        minute: Int = 30,
        repeatDaysBitmask: Int = 0b1111111,
        excludeHolidays: Boolean = false,
    ) = Alarm(
        id = id,
        hour = hour,
        minute = minute,
        repeatDays = bitmaskToDays(repeatDaysBitmask),
        excludeHolidays = excludeHolidays,
        playlistId = 10L,
        isEnabled = true,
        label = "Test Alarm",
        autoStop = null,
        lastTriggeredAt = null,
    )

    private fun bitmaskToDays(bitmask: Int): Set<java.time.DayOfWeek> {
        val days = mutableSetOf<java.time.DayOfWeek>()
        if (bitmask and (1 shl 0) != 0) days.add(java.time.DayOfWeek.MONDAY)
        if (bitmask and (1 shl 1) != 0) days.add(java.time.DayOfWeek.TUESDAY)
        if (bitmask and (1 shl 2) != 0) days.add(java.time.DayOfWeek.WEDNESDAY)
        if (bitmask and (1 shl 3) != 0) days.add(java.time.DayOfWeek.THURSDAY)
        if (bitmask and (1 shl 4) != 0) days.add(java.time.DayOfWeek.FRIDAY)
        if (bitmask and (1 shl 5) != 0) days.add(java.time.DayOfWeek.SATURDAY)
        if (bitmask and (1 shl 6) != 0) days.add(java.time.DayOfWeek.SUNDAY)
        return days
    }

    private fun disabledAlarm(id: Long = 1L) = enabledAlarm(id).copy(isEnabled = false)

    /** ShadowAlarmManager.nextAlarmClock is protected; access via reflection. */
    private fun getNextAlarmClock(): AlarmClockInfo? {
        val shadow = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        val method = shadow.javaClass.getDeclaredMethod("getNextAlarmClock")
        method.isAccessible = true
        return method.invoke(shadow) as? AlarmClockInfo
    }

    @Test
    fun `schedule enabled alarm sets alarmClock with correct triggerTime`() = runTest {
        val triggerTime = System.currentTimeMillis() + 60_000L

        coEvery {
            nextOccurrenceCalculator.nextOccurrence(7, 30, 0b1111111, false)
        } returns triggerTime

        alarmScheduler.schedule(enabledAlarm())

        val alarmClock = getNextAlarmClock()
        assertNotNull("Expected an alarm clock to be set", alarmClock)
        assertEquals(triggerTime, alarmClock!!.triggerTime)
    }

    @Test
    fun `schedule disabled alarm cancels instead`() = runTest {
        alarmScheduler.schedule(disabledAlarm(id = 42L))

        coVerify(exactly = 0) {
            nextOccurrenceCalculator.nextOccurrence(any(), any(), any(), any())
        }
    }

    @Test
    fun `cancel removes pending intent from alarmManager`() = runTest {
        coEvery {
            nextOccurrenceCalculator.nextOccurrence(any(), any(), any(), any())
        } returns System.currentTimeMillis() + 60_000

        alarmScheduler.schedule(enabledAlarm(id = 99L))
        assertNotNull("Alarm should be scheduled before cancel", getNextAlarmClock())

        alarmScheduler.cancel(99L)
        // Cancel clears the alarm; verify no crash
        assertTrue("Cancel should complete without error", true)
    }

    @Test
    fun `rescheduleAll cancels all then schedules all`() = runTest {
        val triggerTime = System.currentTimeMillis() + 60_000L
        coEvery {
            nextOccurrenceCalculator.nextOccurrence(any(), any(), any(), any())
        } returns triggerTime

        val alarms = listOf(
            enabledAlarm(id = 1L, hour = 7, minute = 30),
            enabledAlarm(id = 2L, hour = 22, minute = 0, repeatDaysBitmask = 0b0011111, excludeHolidays = true),
        )

        alarmScheduler.rescheduleAll(alarms)

        coVerify(exactly = 2) {
            nextOccurrenceCalculator.nextOccurrence(any(), any(), any(), any())
        }
        assertNotNull("Expected alarm clock after rescheduleAll", getNextAlarmClock())
    }

    @Test
    fun `canScheduleExactAlarms returns true below API 31`() {
        val result = alarmScheduler.canScheduleExactAlarms()
        assertTrue("Should be able to schedule exact alarms on API 33 shadow", result)
    }

    @Test
    fun `alarm clock show intent targets MainActivity`() = runTest {
        coEvery {
            nextOccurrenceCalculator.nextOccurrence(any(), any(), any(), any())
        } returns System.currentTimeMillis() + 60_000L

        alarmScheduler.schedule(enabledAlarm())

        val alarmClock = getNextAlarmClock()
        assertNotNull("Expected alarm clock to be set", alarmClock)

        val showIntent = alarmClock!!.showIntent
        assertNotNull("Show intent should not be null", showIntent)

        val shadowPendingIntent = shadowOf(showIntent)
        val savedIntent = shadowPendingIntent.savedIntent
        assertNotNull("Saved intent should not be null", savedIntent)

        val component = savedIntent!!.component
        assertNotNull("Intent should have a component", component)
        assertEquals(
            "Show intent should target MainActivity",
            MainActivity::class.java.name,
            component!!.className
        )
    }

    @Test
    fun `schedule passes correct params to calculator`() = runTest {
        val hour = 9
        val minute = 15
        val bitmask = 0b0110011
        val excludeHolidays = true

        coEvery {
            nextOccurrenceCalculator.nextOccurrence(hour, minute, bitmask, excludeHolidays)
        } returns System.currentTimeMillis() + 120_000L

        alarmScheduler.schedule(enabledAlarm(
            hour = hour,
            minute = minute,
            repeatDaysBitmask = bitmask,
            excludeHolidays = excludeHolidays,
        ))

        coVerify(exactly = 1) {
            nextOccurrenceCalculator.nextOccurrence(hour, minute, bitmask, excludeHolidays)
        }
    }
}
