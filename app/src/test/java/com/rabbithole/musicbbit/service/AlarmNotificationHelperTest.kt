package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import dagger.hilt.android.testing.HiltTestApplication
import org.robolectric.annotation.Config

/**
 * Verifies [AlarmNotificationHelper] routes notifications to the DND-bypass channel when
 * the caller passes bypassDnd=true. The bypass decision itself (ignoreQuietMode + DND
 * access) lives in QuietModeBypassResolver and is covered by its own test — this helper
 * only renders what it is told.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class AlarmNotificationHelperTest {

    private companion object {
        private const val NORMAL_CHANNEL = "alarm_channel"
        private const val BYPASS_CHANNEL = "alarm_channel_bypass_dnd"

        private val SONG = Song(
            id = 1L,
            path = "/tmp/song1.mp3",
            title = "Song One",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            dateAdded = 0L,
            coverUri = null,
        )
    }

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var helper: AlarmNotificationHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val resources = NotificationResources(context)
        helper = AlarmNotificationHelper(
            context = context,
            resources = resources,
            channelFactory = NotificationChannelFactory(context, resources),
            mainActivityIntentFactory = MainActivityIntentFactory(context),
        )
    }

    private fun alarm(ignoreQuietMode: Boolean): Alarm = Alarm(
        id = 7L,
        hour = 7,
        minute = 0,
        repeatDays = emptySet(),
        playlistId = 1L,
        isEnabled = true,
        label = null,
        autoStop = null,
        lastTriggeredAt = null,
        ignoreQuietMode = ignoreQuietMode,
    )

    private fun latestNotificationChannelId(): String =
        shadowOf(notificationManager).allNotifications.last().channelId

    @Test
    fun `playing with bypassDnd true posts to bypass channel`() {
        helper.showAlarmPlaying(alarm(ignoreQuietMode = true), SONG, bypassDnd = true)

        assertEquals(BYPASS_CHANNEL, latestNotificationChannelId())
        assertEquals(true, notificationManager.getNotificationChannel(BYPASS_CHANNEL).canBypassDnd())
    }

    @Test
    fun `playing with bypassDnd false posts to normal channel`() {
        helper.showAlarmPlaying(alarm(ignoreQuietMode = true), SONG, bypassDnd = false)

        assertEquals(NORMAL_CHANNEL, latestNotificationChannelId())
    }

    @Test
    fun `paused notification reuses the channel chosen by playing`() {
        helper.showAlarmPlaying(alarm(ignoreQuietMode = true), SONG, bypassDnd = true)

        helper.showAlarmPaused(alarmId = 7L, bypassDnd = true)

        assertEquals(BYPASS_CHANNEL, latestNotificationChannelId())
    }

    @Test
    fun `cancel resets channel memory and showError always uses normal channel`() {
        helper.showAlarmPlaying(alarm(ignoreQuietMode = true), SONG, bypassDnd = true)
        helper.cancel(alarmId = 7L)

        helper.showError(notificationId = 99, title = "Error", message = "Playback failed")

        assertEquals(NORMAL_CHANNEL, latestNotificationChannelId())
    }

    @Test
    fun `showAlarmPaused uses bypassDnd parameter without prior showAlarmPlaying`() {
        // New explicit-contract behavior: channel is chosen from bypassDnd directly,
        // no helper-mutable lastChannelId requirement.
        helper.showAlarmPaused(alarmId = 7L, bypassDnd = true)
        assertEquals(BYPASS_CHANNEL, latestNotificationChannelId())

        // Calling again with bypassDnd=false after a true call lands on the normal channel
        // — no shared mutable state across calls.
        helper.showAlarmPaused(alarmId = 8L, bypassDnd = false)
        assertEquals(NORMAL_CHANNEL, latestNotificationChannelId())
    }
}
