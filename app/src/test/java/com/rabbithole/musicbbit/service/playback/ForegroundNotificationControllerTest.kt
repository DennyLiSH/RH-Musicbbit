package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * JVM unit tests for [ForegroundNotificationController].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundNotificationControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val playbackState = MutableStateFlow(PlaybackState())

    private lateinit var playbackSession: PlaybackSession
    private lateinit var musicNotificationPort: MusicNotificationPort
    private lateinit var serviceStarter: ServiceStarter
    private lateinit var controller: ForegroundNotificationController

    private val song = Song(
        id = 1L,
        path = "/tmp/song.mp3",
        title = "Song",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        dateAdded = 0L,
        coverUri = null,
    )

    @Before
    fun setUp() {
        playbackSession = mock()
        whenever(playbackSession.playbackState).thenReturn(playbackState)
        musicNotificationPort = mock()
        serviceStarter = mock()
        controller = ForegroundNotificationController(
            playbackSession = playbackSession,
            musicNotificationPort = musicNotificationPort,
            serviceStarter = serviceStarter,
            mainDispatcher = testDispatcher,
        )
    }

    @Test
    fun `onCreate ensures channel exists`() {
        controller.onCreate()
        verify(musicNotificationPort).ensureChannelExists()
    }

    @Test
    fun `initial empty state hides foreground notification`() {
        controller.onCreate()

        verify(musicNotificationPort).hideForegroundNotification()
        verify(musicNotificationPort, never()).buildAndNotify(any())
    }

    @Test
    fun `state with song builds and notifies`() {
        controller.onCreate()

        val state = PlaybackState(currentSong = song, isPlaying = true)
        playbackState.value = state

        verify(musicNotificationPort).buildAndNotify(state)
    }

    @Test
    fun `state without song hides foreground notification`() {
        controller.onCreate()

        // Move to a state with a song first
        playbackState.value = PlaybackState(currentSong = song)
        // Then back to empty
        playbackState.value = PlaybackState()

        // Initial empty state hides once, then the transition to empty hides again
        verify(musicNotificationPort, times(2)).hideForegroundNotification()
    }

    @Test
    fun `onStartCommand builds notification with current state`() {
        val state = PlaybackState(currentSong = song)
        playbackState.value = state

        controller.onStartCommand()

        verify(musicNotificationPort).buildAndNotify(state)
    }

    @Test
    fun `onStartCommand builds placeholder notification when no current song`() {
        val state = PlaybackState()
        playbackState.value = state

        controller.onStartCommand()

        verify(musicNotificationPort).buildAndNotify(state)
    }

    @Test
    fun `onStartCommand propagates synchronous exceptions`() {
        val error = RuntimeException("buildAndNotify failed")
        whenever(musicNotificationPort.buildAndNotify(playbackState.value)).thenThrow(error)

        val thrown = assertThrows(RuntimeException::class.java) {
            controller.onStartCommand()
        }

        assertThrows("Expected the original exception", error.javaClass) { throw thrown }
    }

    @Test
    fun `onDestroy hides notification and cancels collection`() {
        controller.onCreate()
        // Initial hide on empty state
        verify(musicNotificationPort).hideForegroundNotification()

        playbackState.value = PlaybackState(currentSong = song)
        controller.onDestroy()

        // Final hide on destroy
        verify(musicNotificationPort, times(2)).hideForegroundNotification()

        // After destroy, further state changes must not trigger the port
        playbackState.value = PlaybackState(currentSong = song.copy(id = 2L))
        verify(musicNotificationPort).buildAndNotify(any())
    }

    @Test
    fun `coroutine exception hides notification and stops service`() {
        val error = RuntimeException("state collection failed")
        whenever(musicNotificationPort.buildAndNotify(any())).thenThrow(error)

        controller.onCreate()

        // Trigger collection by emitting a state with a song
        playbackState.value = PlaybackState(currentSong = song)

        verify(musicNotificationPort, times(2)).hideForegroundNotification()
        verify(serviceStarter).stopService()
    }

    @Test
    fun `onCreate onStartCommand onDestroy order`() {
        val state = PlaybackState(currentSong = song, isPlaying = true)
        playbackState.value = state

        controller.onCreate()
        controller.onStartCommand()
        controller.onDestroy()

        inOrder(musicNotificationPort) {
            verify(musicNotificationPort).ensureChannelExists()
            verify(musicNotificationPort, times(2)).buildAndNotify(state)
            verify(musicNotificationPort).hideForegroundNotification()
        }
    }

    @Test
    fun `state collection stops after onDestroy`() {
        controller.onCreate()
        controller.onDestroy()

        playbackState.value = PlaybackState(currentSong = song)

        verify(musicNotificationPort, never()).buildAndNotify(PlaybackState(currentSong = song))
    }
}
