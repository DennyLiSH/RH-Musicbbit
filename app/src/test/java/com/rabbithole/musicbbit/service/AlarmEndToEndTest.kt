package com.rabbithole.musicbbit.service

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Service-layer end-to-end smoke that verifies the receiver hands off to
 * [MusicPlaybackService] with the exact extras [com.rabbithole.musicbbit.service.alarm.AlarmFireSession.fire]
 * expects to consume.
 *
 * Pre-step-5 this file covered alarm bookkeeping; that lives in AlarmFireSession now and
 * is verified by JVM unit tests on the session directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmEndToEndTest {

    @Test
    fun `alarm trigger dispatches MusicPlaybackService with PLAY_ALARM action and alarmId`() {
        val context = RuntimeEnvironment.getApplication()
        val alarmId = 42L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(context, intent)

        Thread.sleep(300)

        val startedService = shadowOf(context).peekNextStartedService()
        assertNotNull("Expected MusicPlaybackService to be started", startedService)
        assertEquals(
            "Expected ACTION_PLAY_ALARM action",
            MusicPlaybackService.ACTION_PLAY_ALARM,
            startedService!!.action,
        )
        assertEquals(
            "Expected alarmId extra to be propagated",
            alarmId,
            startedService.getLongExtra(MusicPlaybackService.EXTRA_ALARM_ID, -1L),
        )
        assertTrue(
            "Expected EXTRA_IS_ALARM_TRIGGER to be true",
            startedService.getBooleanExtra(MusicPlaybackService.EXTRA_IS_ALARM_TRIGGER, false),
        )
    }
}
