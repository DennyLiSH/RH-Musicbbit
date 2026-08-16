package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rabbithole.musicbbit.domain.model.Alarm
import com.rabbithole.musicbbit.domain.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Verifies [AlarmNotificationHelper] routes notifications to the DND-bypass channel only
 * when the alarm ignores quiet mode AND Do Not Disturb access is actually granted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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

    private var dndMock: MockedStatic<DndAccessPermissionHelper>? = null

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
        dndMock = mockStatic(DndAccessPermissionHelper::class.java)
    }

    @After
    fun tearDown() {
        dndMock?.close()
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
    fun `playing with ignoreQuietMode and dnd granted posts to bypass channel`() {
        dndMock!!.`when`<Boolean> { DndAccessPermissionHelper.isGranted(any()) }.thenReturn(true)

        helper.showAlarmPlaying(alarm(ignoreQuietMode = true), SONG)

        assertEquals(BYPASS_CHANNEL, latestNotificationChannelId())
        assertEquals(true, notificationManager.getNotificationChannel(BYPASS_CHANNEL).canBypassDnd())
    }

    @Test
    fun `playing with ignoreQuietMode but dnd not granted falls back to normal channel`() {
        dndMock!!.`when`<Boolean> { DndAccessPermissionHelper.isGranted(any()) }.thenReturn(false)

        helper.showAlarmPlaying(alarm(ignoreQuietMode = true), SONG)

        assertEquals(NORMAL_CHANNEL, latestNotificationChannelId())
    }

    @Test
    fun `playing without ignoreQuietMode posts to normal channel`() {
        dndMock!!.`when`<Boolean> { DndAccessPermissionHelper.isGranted(any()) }.thenReturn(true)

        helper.showAlarmPlaying(alarm(ignoreQuietMode = false), SONG)

        assertEquals(NORMAL_CHANNEL, latestNotificationChannelId())
    }

    @Test
    fun `paused notification reuses the channel chosen by playing`() {
        dndMock!!.`when`<Boolean> { DndAccessPermissionHelper.isGranted(any()) }.thenReturn(true)
        helper.showAlarmPlaying(alarm(ignoreQuietMode = true), SONG)

        helper.showAlarmPaused(alarmId = 7L)

        assertEquals(BYPASS_CHANNEL, latestNotificationChannelId())
    }

    @Test
    fun `cancel resets channel memory and showError always uses normal channel`() {
        dndMock!!.`when`<Boolean> { DndAccessPermissionHelper.isGranted(any()) }.thenReturn(true)
        helper.showAlarmPlaying(alarm(ignoreQuietMode = true), SONG)
        helper.cancel(alarmId = 7L)

        helper.showError(notificationId = 99, title = "Error", message = "Playback failed")

        assertEquals(NORMAL_CHANNEL, latestNotificationChannelId())
    }
}
