package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import com.rabbithole.musicbbit.service.alarm.AlarmFireSession
import com.rabbithole.musicbbit.service.playback.ForegroundNotificationController
import com.rabbithole.musicbbit.service.playback.PlaybackSession
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 *   - Notification channel setup
 *   - onBind returns MusicBinder
 *   - onStartCommand returns START_STICKY
 *   - Intent action delegation to [PlaybackSession] and [AlarmFireSession]
 *   - ForegroundNotificationController and MusicPlaybackServiceForegroundBridge lifecycle
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
        service = Robolectric.buildService(MusicPlaybackService::class.java).get()
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
    fun `onCreate attaches service to bridge`() {
        service.onCreate()

        val bridge = service.javaClass.getDeclaredField("foregroundServiceBridge").apply {
            isAccessible = true
        }.get(service) as MusicPlaybackServiceForegroundBridge

        val attached = bridge.javaClass.getDeclaredField("service").apply {
            isAccessible = true
        }.get(bridge)

        assertEquals("Bridge should attach the created service", service, attached)
    }

    @Test
    fun `onBind returns MusicBinder`() {
        service.onCreate()

        val binder = service.onBind(Intent())
        assertTrue("Binder should be MusicBinder", binder is MusicPlaybackService.MusicBinder)

        val musicBinder = binder as MusicPlaybackService.MusicBinder
        assertEquals("Binder should return the service", service, musicBinder.getService())
    }

    @Test
    fun `onStartCommand with null intent returns START_NOT_STICKY and stops self`() {
        service.onCreate()

        val result = service.onStartCommand(null, 0, 0)
        assertEquals(
            "Should return START_NOT_STICKY for system restart with no active playback",
            Service.START_NOT_STICKY,
            result,
        )
    }

    @Test
    fun `onStartCommand calls foregroundNotificationController onStartCommand`() {
        service.onCreate()

        val mockController = mock<ForegroundNotificationController>()
        service.javaClass.getDeclaredField("foregroundNotificationController").apply {
            isAccessible = true
            set(service, mockController)
        }

        val intent = Intent(RuntimeEnvironment.getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_NEXT
        }
        val result = service.onStartCommand(intent, 0, 0)
        assertEquals("Should return START_STICKY", Service.START_STICKY, result)
        verify(mockController).onStartCommand()
    }

    @Test
    fun `onStartCommand with ACTION_PLAY_ALARM calls alarmFireSession fire`() {
        service.onCreate()

        val mockAlarmFireSession = mock<AlarmFireSession>()
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
        assertEquals("Should return START_STICKY", Service.START_STICKY, result)
        verify(mockAlarmFireSession).fire(alarmId, true)
    }

    @Test
    fun `onStartCommand with ACTION_PREVIOUS calls playbackSession previous`() {
        service.onCreate()

        val mockPlaybackSession = mock<PlaybackSession>()
        whenever(mockPlaybackSession.playbackState).thenReturn(MutableStateFlow(PlaybackState()))
        service.javaClass.getDeclaredField("playbackSession").apply {
            isAccessible = true
            set(service, mockPlaybackSession)
        }

        val intent = Intent(RuntimeEnvironment.getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PREVIOUS
        }
        service.onStartCommand(intent, 0, 0)

        verify(mockPlaybackSession).previous()
    }

    @Test
    fun `onStartCommand with ACTION_NEXT calls playbackSession next`() {
        service.onCreate()

        val mockPlaybackSession = mock<PlaybackSession>()
        whenever(mockPlaybackSession.playbackState).thenReturn(MutableStateFlow(PlaybackState()))
        service.javaClass.getDeclaredField("playbackSession").apply {
            isAccessible = true
            set(service, mockPlaybackSession)
        }

        val intent = Intent(RuntimeEnvironment.getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_NEXT
        }
        service.onStartCommand(intent, 0, 0)

        verify(mockPlaybackSession).next()
    }

    @Test
    fun `onStartCommand with ACTION_TOGGLE_PLAY_PAUSE toggles playback`() {
        service.onCreate()

        val mockPlaybackSession = mock<PlaybackSession>()
        val stateFlow = MutableStateFlow(PlaybackState(isPlaying = true))
        whenever(mockPlaybackSession.playbackState).thenReturn(stateFlow)
        service.javaClass.getDeclaredField("playbackSession").apply {
            isAccessible = true
            set(service, mockPlaybackSession)
        }

        val intent = Intent(RuntimeEnvironment.getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE
        }
        service.onStartCommand(intent, 0, 0)

        verify(mockPlaybackSession).pause()
    }

    @Test
    fun `onStartCommand with ACTION_TOGGLE_PLAY_PAUSE resumes when paused`() {
        service.onCreate()

        val mockPlaybackSession = mock<PlaybackSession>()
        val stateFlow = MutableStateFlow(PlaybackState(isPlaying = false))
        whenever(mockPlaybackSession.playbackState).thenReturn(stateFlow)
        service.javaClass.getDeclaredField("playbackSession").apply {
            isAccessible = true
            set(service, mockPlaybackSession)
        }

        val intent = Intent(RuntimeEnvironment.getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE
        }
        service.onStartCommand(intent, 0, 0)

        verify(mockPlaybackSession).resume()
    }

    @Test
    fun `onDestroy calls controller onDestroy and detaches from bridge`() {
        service.onCreate()

        val mockController = mock<ForegroundNotificationController>()
        service.javaClass.getDeclaredField("foregroundNotificationController").apply {
            isAccessible = true
            set(service, mockController)
        }

        service.onDestroy()

        verify(mockController).onDestroy()

        val bridge = service.javaClass.getDeclaredField("foregroundServiceBridge").apply {
            isAccessible = true
        }.get(service) as MusicPlaybackServiceForegroundBridge

        val attached = bridge.javaClass.getDeclaredField("service").apply {
            isAccessible = true
        }.get(bridge)

        assertNull("Bridge should detach the destroyed service", attached)
    }

    @Test
    fun `onDestroy does not crash`() {
        service.onCreate()
        service.onDestroy()
    }
}
