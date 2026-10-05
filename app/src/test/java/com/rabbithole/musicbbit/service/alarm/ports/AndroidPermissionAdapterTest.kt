package com.rabbithole.musicbbit.service.alarm.ports

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
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

    @Test
    fun `createExactAlarmSettingsIntent targets request-schedule-exact-alarm page`() {
        val intent = adapter.createExactAlarmSettingsIntent()

        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }

    @Test
    @Config(sdk = [30])
    fun `createFullScreenIntentSettingsIntent is empty below API 34 - nothing to open`() {
        // Below API 34 the permission is install-time granted; the empty intent
        // encodes "no settings page exists". UI gates hide the entry point there.
        assertEquals(null, adapter.createFullScreenIntentSettingsIntent().action)
    }

    @Test
    @Config(sdk = [34])
    fun `createFullScreenIntentSettingsIntent targets manage page on API 34+`() {
        val intent = adapter.createFullScreenIntentSettingsIntent()

        assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }
}
