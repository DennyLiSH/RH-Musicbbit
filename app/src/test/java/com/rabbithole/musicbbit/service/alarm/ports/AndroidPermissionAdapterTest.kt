package com.rabbithole.musicbbit.service.alarm.ports

import android.content.Context
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Single source of truth for the exact-alarm permission query: the API-level
 * branch lives in this adapter (moved from AlarmScheduler, which now delegates).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class AndroidPermissionAdapterTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val adapter = AndroidPermissionAdapter(context)

    @Test
    @Config(sdk = [30])
    fun `canScheduleExactAlarms returns true below API 31`() {
        assertTrue(
            "Permission is not required below API 31",
            adapter.canScheduleExactAlarms(),
        )
    }

    @Test
    fun `canScheduleExactAlarms reflects alarmManager state on API 31+`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(adapter.canScheduleExactAlarms())

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(adapter.canScheduleExactAlarms())
    }
}
