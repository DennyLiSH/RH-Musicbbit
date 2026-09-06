package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the single audibility decision: ignoreQuietMode + DND access →
 * [AlarmBypassPlan]. Replaces the mockStatic-based channel tests that used to live in
 * AlarmNotificationHelperTest.
 */
class QuietModeBypassResolverTest {

    private class FakePermissionPort(
        var dndAccessGranted: Boolean = false,
    ) : PermissionPort {
        override fun isIgnoringBatteryOptimizations() = false
        override fun createBatteryOptimizationIntent() = android.content.Intent()
        override fun isFullScreenIntentGranted() = false
        override fun checkPermission(permission: String) = false
        override fun canScheduleExactAlarms() = false
        override fun isNotificationPolicyAccessGranted() = dndAccessGranted
    }

    private fun alarm(ignoreQuietMode: Boolean, isEnabled: Boolean = true) = Alarm(
        id = 7L,
        hour = 7,
        minute = 0,
        repeatDays = emptySet(),
        playlistId = 1L,
        isEnabled = isEnabled,
        label = null,
        autoStop = null,
        lastTriggeredAt = null,
        ignoreQuietMode = ignoreQuietMode,
    )

    @Test
    fun `ignoreQuietMode with dnd access granted yields alarm stream and bypass channel`() {
        val resolver = QuietModeBypassResolver(FakePermissionPort(dndAccessGranted = true))

        val plan = resolver.resolve(alarm(ignoreQuietMode = true))

        assertEquals(AlarmBypassPlan(useAlarmStream = true, useBypassNotificationChannel = true), plan)
    }

    @Test
    fun `ignoreQuietMode without dnd access keeps alarm stream but falls back to normal channel`() {
        val resolver = QuietModeBypassResolver(FakePermissionPort(dndAccessGranted = false))

        val plan = resolver.resolve(alarm(ignoreQuietMode = true))

        assertEquals(AlarmBypassPlan(useAlarmStream = true, useBypassNotificationChannel = false), plan)
    }

    @Test
    fun `without ignoreQuietMode the plan uses media stream and normal channel`() {
        val resolver = QuietModeBypassResolver(FakePermissionPort(dndAccessGranted = true))

        val plan = resolver.resolve(alarm(ignoreQuietMode = false))

        assertEquals(AlarmBypassPlan(useAlarmStream = false, useBypassNotificationChannel = false), plan)
    }

    @Test
    fun `banner needed when any enabled alarm ignores quiet mode`() {
        assertTrue(
            QuietModeBypassResolver.needsDndAccessBanner(
                listOf(alarm(ignoreQuietMode = false), alarm(ignoreQuietMode = true))
            )
        )
    }

    @Test
    fun `banner not needed when the ignoreQuietMode alarm is disabled`() {
        assertFalse(
            QuietModeBypassResolver.needsDndAccessBanner(
                listOf(alarm(ignoreQuietMode = true, isEnabled = false))
            )
        )
    }

    @Test
    fun `banner not needed when no alarm ignores quiet mode`() {
        assertFalse(
            QuietModeBypassResolver.needsDndAccessBanner(
                listOf(alarm(ignoreQuietMode = false))
            )
        )
    }
}
