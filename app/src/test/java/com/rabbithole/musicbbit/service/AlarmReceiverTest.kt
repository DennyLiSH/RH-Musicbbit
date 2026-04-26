package com.rabbithole.musicbbit.service

import android.content.Intent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric smoke tests for [AlarmReceiver].
 *
 * AlarmReceiver is a thin shell after step 5 of the AlarmFireSession refactor: its only
 * remaining job is to dispatch valid alarm intents to [MusicPlaybackService]. All alarm
 * bookkeeping (lastTriggeredAt, one-time isEnabled flip, repeating reschedule) is verified
 * via JVM unit tests on AlarmFireSession instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmReceiverTest {

    @Test
    fun `onReceive with valid alarmId starts foreground service`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 1L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val startedService = shadowOf(context).peekNextStartedService()
        assertNotNull("Expected service to be started", startedService)
    }

    @Test
    fun `onReceive with invalid alarmId does not start service`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(context, intent)

        Thread.sleep(100)

        val startedService = shadowOf(context).peekNextStartedService()
        assertNull("Expected no service to be started", startedService)
    }

    @Test
    fun `onReceive with missing alarmId does not crash and does not start service`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, AlarmReceiver::class.java)

        val receiver = AlarmReceiver()
        receiver.onReceive(context, intent)

        Thread.sleep(100)

        val startedService = shadowOf(context).peekNextStartedService()
        assertNull("Expected no service to be started", startedService)
    }
}
