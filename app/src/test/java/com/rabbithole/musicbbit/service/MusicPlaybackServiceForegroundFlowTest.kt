package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.playback.PlaybackSession
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Robolectric integration tests for the music playback foreground notification flow.
 *
 * Covers:
 *   - Starting the service produces a foreground notification when a song is active.
 *   - Clearing the active song removes the notification.
 *   - Service recreation correctly re-attaches the new service instance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [24, 33, 34])
class MusicPlaybackServiceForegroundFlowTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var playbackSession: PlaybackSession

    @Inject
    lateinit var bridge: MusicPlaybackServiceForegroundBridge

    private val song = Song(
        id = 1L,
        path = "/tmp/song.mp3",
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        durationMs = 180_000L,
        dateAdded = 0L,
        coverUri = null,
    )

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `service start with active song shows foreground notification`() {
        val service = Robolectric.setupService(MusicPlaybackService::class.java)
        service.onCreate()

        setPlaybackState(
            PlaybackState(currentSong = song, isPlaying = true)
        )

        val intent = Intent(RuntimeEnvironment.getApplication(), MusicPlaybackService::class.java)
        service.onStartCommand(intent, 0, 0)

        val notificationManager = RuntimeEnvironment.getApplication()
            .getSystemService(NotificationManager::class.java)
        val shadowNotificationManager = shadowOf(notificationManager)
        val notification = shadowNotificationManager.getNotification(
            MusicPlaybackService.NOTIFICATION_ID
        )

        assertNotNull("Expected foreground notification to be posted", notification)
        assertEquals("Test Song", notification!!.extras.getString(android.app.Notification.EXTRA_TITLE))
    }

    @Test
    fun `clearing active song removes foreground notification`() {
        val service = Robolectric.setupService(MusicPlaybackService::class.java)
        service.onCreate()

        setPlaybackState(PlaybackState(currentSong = song, isPlaying = true))

        val intent = Intent(RuntimeEnvironment.getApplication(), MusicPlaybackService::class.java)
        service.onStartCommand(intent, 0, 0)

        val notificationManager = RuntimeEnvironment.getApplication()
            .getSystemService(NotificationManager::class.java)
        val shadowNotificationManager = shadowOf(notificationManager)

        assertNotNull(
            "Expected notification before clearing song",
            shadowNotificationManager.getNotification(MusicPlaybackService.NOTIFICATION_ID)
        )

        setPlaybackState(PlaybackState())

        assertNull(
            "Expected notification to be removed after clearing song",
            shadowNotificationManager.getNotification(MusicPlaybackService.NOTIFICATION_ID)
        )
    }

    @Test
    fun `service recreation re-attaches new instance`() {
        val service1 = Robolectric.setupService(MusicPlaybackService::class.java)
        service1.onCreate()

        assertEquals("First service should be attached", service1, attachedService())

        val service2 = Robolectric.setupService(MusicPlaybackService::class.java)
        service2.onCreate()

        assertEquals("Second service should replace first in bridge", service2, attachedService())
        assertNotEquals("Attached service should not be the old instance", service1, attachedService())
    }

    private fun setPlaybackState(state: PlaybackState) {
        val stateField = playbackSession.javaClass.getDeclaredField("_playbackState").apply {
            isAccessible = true
        }
        val mutableStateFlow = stateField.get(playbackSession) as MutableStateFlow<PlaybackState>
        mutableStateFlow.value = state
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun attachedService(): MusicPlaybackService? {
        val serviceField = bridge.javaClass.getDeclaredField("service").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        return serviceField.get(bridge) as? MusicPlaybackService
    }
}
