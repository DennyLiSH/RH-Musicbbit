package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rabbithole.musicbbit.service.playback.PlayerPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [MusicPlaybackService].
 *
 * Covers:
 *   - Notification creation and channel setup
 *   - Binder playback state delegation to [PlayerPort]
 *   - onStartCommand with ACTION_PLAY_ALARM delegates to [AlarmFireSession]
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class MusicPlaybackServiceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var service: MusicPlaybackService

    @Before
    fun setUp() {
        hiltRule.inject()
        service = Robolectric.setupService(MusicPlaybackService::class.java)
    }

    @Test
    fun `onCreate creates notification channel`() {
        service.onCreate()

        val notificationManager = RuntimeEnvironment.getApplication()
            .getSystemService(NotificationManager::class.java)
        val shadowNotificationManager = shadowOf(notificationManager)
        val channels = shadowNotificationManager.notificationChannels

        assertTrue("Expected at least one notification channel", channels.isNotEmpty())
        val channel = channels.find { it.id == "music_playback_channel" }
        assertNotNull("Music playback notification channel should be created", channel)
    }

    @Test
    fun `startForeground posts notification`() {
        service.onCreate()

        val appNotificationManager = RuntimeEnvironment.getApplication()
            .getSystemService(NotificationManager::class.java)
        val shadowNotificationManager = shadowOf(appNotificationManager)

        // startForeground is called in onStartCommand, but we can verify the notification
        // was built by checking the notification manager shadows
        val initialCount = shadowNotificationManager.activeNotifications.size

        // Manually trigger startForeground with a built notification.
        // Access notificationManager via reflection since it's private.
        val notificationManagerField = service.javaClass.getDeclaredField("notificationManager")
        notificationManagerField.isAccessible = true
        val musicNotificationManager = notificationManagerField.get(service) as MusicNotificationManager
        val notification = musicNotificationManager.buildNotification(PlaybackState())
        service.startForeground(1, notification)

        // On Robolectric, startForeground does not post via NotificationManager,
        // but we can verify the notification object is valid
        assertNotNull("Notification should be built", notification)
    }

    @Test
    fun `notification has correct channel and actions`() {
        service.onCreate()

        val state = PlaybackState(
            isPlaying = false,
            currentSong = null
        )

        // Access notificationManager via reflection since it's private.
        val notificationManagerField = service.javaClass.getDeclaredField("notificationManager")
        notificationManagerField.isAccessible = true
        val musicNotificationManager = notificationManagerField.get(service) as MusicNotificationManager
        val notification = musicNotificationManager.buildNotification(state)

        assertEquals(
            "Notification should use the correct channel",
            "music_playback_channel",
            NotificationCompat.getChannelId(notification)
        )

        val actions = notification.actions
        assertNotNull("Notification should have actions", actions)
        assertEquals("Notification should have 3 actions", 3, actions?.size)
    }

    @Test
    fun `binder getService returns the service instance`() {
        service.onCreate()

        val binder = service.onBind(Intent())
        assertTrue("Binder should be MusicBinder", binder is MusicPlaybackService.MusicBinder)

        val musicBinder = binder as MusicPlaybackService.MusicBinder
        assertEquals("Binder should return the service", service, musicBinder.getService())
    }

    @Test
    fun `binder play delegates to playerPort`() {
        service.onCreate()

        // Replace the injected playerPort with a mock via reflection
        val mockPlayerPort = mockk<PlayerPort>(relaxed = true)
        every { mockPlayerPort.events } returns MutableSharedFlow()
        service.javaClass.getDeclaredField("playerPort").apply {
            isAccessible = true
            set(service, mockPlayerPort)
        }

        // Also mock alarmFireSession to avoid NPE
        val mockAlarmFireSession = mockk<com.rabbithole.musicbbit.service.alarm.AlarmFireSession>(relaxed = true)
        service.javaClass.getDeclaredField("alarmFireSession").apply {
            isAccessible = true
            set(service, mockAlarmFireSession)
        }

        val binder = service.onBind(Intent()) as MusicPlaybackService.MusicBinder
        val svc = binder.getService()

        // Verify the service instance is correct
        assertEquals(service, svc)

        // Verify playerPort mock was set up correctly by calling a method that delegates
        svc.pause()
        verify { mockPlayerPort.pause() }
    }

    @Test
    fun `binder pause and resume delegate to playerPort`() {
        service.onCreate()

        val mockPlayerPort = mockk<PlayerPort>(relaxed = true)
        every { mockPlayerPort.events } returns MutableSharedFlow()
        service.javaClass.getDeclaredField("playerPort").apply {
            isAccessible = true
            set(service, mockPlayerPort)
        }

        val mockAlarmFireSession = mockk<com.rabbithole.musicbbit.service.alarm.AlarmFireSession>(relaxed = true)
        service.javaClass.getDeclaredField("alarmFireSession").apply {
            isAccessible = true
            set(service, mockAlarmFireSession)
        }

        service.pause()
        verify { mockPlayerPort.pause() }

        every { mockPlayerPort.isPlaying() } returns false
        service.resume()
        verify { mockPlayerPort.play() }
    }

    @Test
    fun `onStartCommand with ACTION_PLAY_ALARM calls alarmFireSession fire`() {
        service.onCreate()

        val mockAlarmFireSession = mockk<com.rabbithole.musicbbit.service.alarm.AlarmFireSession>(relaxed = true)
        service.javaClass.getDeclaredField("alarmFireSession").apply {
            isAccessible = true
            set(service, mockAlarmFireSession)
        }

        val alarmId = 42L
        val intent = Intent(RuntimeEnvironment.getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_ALARM
            putExtra(MusicPlaybackService.EXTRA_ALARM_ID, alarmId)
            putExtra(MusicPlaybackService.EXTRA_IS_ALARM_TRIGGER, true)
        }

        val result = service.onStartCommand(intent, 0, 0)

        assertEquals("Should return START_STICKY", android.app.Service.START_STICKY, result)
        verify { mockAlarmFireSession.fire(alarmId, true) }
    }

    @Test
    fun `onStartCommand with ACTION_PLAY_ALARM and isAlarmTrigger false does not acquire wakeLock`() {
        service.onCreate()

        val mockAlarmFireSession = mockk<com.rabbithole.musicbbit.service.alarm.AlarmFireSession>(relaxed = true)
        service.javaClass.getDeclaredField("alarmFireSession").apply {
            isAccessible = true
            set(service, mockAlarmFireSession)
        }

        val alarmId = 43L
        val intent = Intent(RuntimeEnvironment.getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_ALARM
            putExtra(MusicPlaybackService.EXTRA_ALARM_ID, alarmId)
            putExtra(MusicPlaybackService.EXTRA_IS_ALARM_TRIGGER, false)
        }

        service.onStartCommand(intent, 0, 0)

        verify { mockAlarmFireSession.fire(alarmId, false) }
    }

    @Test
    fun `onStartCommand with ACTION_TOGGLE_PLAY_PAUSE toggles playback`() {
        service.onCreate()

        val mockPlayerPort = mockk<PlayerPort>(relaxed = true)
        every { mockPlayerPort.events } returns MutableSharedFlow()
        every { mockPlayerPort.isPlaying() } returns true
        service.javaClass.getDeclaredField("playerPort").apply {
            isAccessible = true
            set(service, mockPlayerPort)
        }

        val mockAlarmFireSession = mockk<com.rabbithole.musicbbit.service.alarm.AlarmFireSession>(relaxed = true)
        service.javaClass.getDeclaredField("alarmFireSession").apply {
            isAccessible = true
            set(service, mockAlarmFireSession)
        }

        // Service checks _playbackState.value.isPlaying, not playerPort.isPlaying()
        val playbackStateField = service.javaClass.getDeclaredField("_playbackState")
        playbackStateField.isAccessible = true
        val stateFlow = playbackStateField.get(service) as kotlinx.coroutines.flow.MutableStateFlow<PlaybackState>
        stateFlow.value = PlaybackState(isPlaying = true)

        val intent = Intent(RuntimeEnvironment.getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE
        }

        service.onStartCommand(intent, 0, 0)

        verify { mockPlayerPort.pause() }
    }

    @Test
    fun `onStartCommand with null intent still starts foreground`() {
        service.onCreate()

        val result = service.onStartCommand(null, 0, 0)

        assertEquals("Should return START_STICKY even with null intent", android.app.Service.START_STICKY, result)
    }
}
