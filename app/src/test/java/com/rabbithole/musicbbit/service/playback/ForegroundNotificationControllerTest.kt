package com.rabbithole.musicbbit.service.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.MusicPlaybackService
import com.rabbithole.musicbbit.service.PlaybackState
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [ForegroundNotificationController] (ADR 0008 spec pattern).
 *
 * The controller now owns the notification lifecycle end-to-end: it builds the
 * Notification from a pure [ForegroundNotificationSpec] (returned by the mocked
 * [MusicNotificationPort]) and calls `MusicPlaybackService.startForeground` /
 * `stopForeground` directly. The Bridge singleton is no longer in the loop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class ForegroundNotificationControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val playbackState = MutableStateFlow(PlaybackState())
    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var playbackSession: UserPlaybackSession
    private lateinit var musicNotificationPort: MusicNotificationPort
    private lateinit var serviceStarter: ServiceStarter
    private lateinit var service: MusicPlaybackService
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

    private fun specFor(state: PlaybackState) = ForegroundNotificationSpec(
        title = state.currentSong?.title ?: "App",
        text = state.currentSong?.artist ?: "Unknown",
        isPlaying = state.isPlaying,
        smallIconResId = android.R.drawable.ic_media_play,
        playPauseIconResId = android.R.drawable.ic_media_pause,
        playPauseLabel = if (state.isPlaying) "Pause" else "Play",
        previousLabel = "Prev",
        nextLabel = "Next",
    )

    @Before
    fun setUp() {
        playbackSession = mock()
        whenever(playbackSession.playbackState).thenReturn(playbackState)
        musicNotificationPort = mock()
        whenever(musicNotificationPort.buildSpec(any())).thenAnswer { specFor(it.getArgument(0)) }
        serviceStarter = mock()
        service = mock()
        controller = ForegroundNotificationController(
            playbackSession = playbackSession,
            musicNotificationPort = musicNotificationPort,
            serviceStarter = serviceStarter,
            context = context,
            mainDispatcher = testDispatcher,
        )
        controller.attach(service)
    }

    @Test
    fun `onCreate ensures channel exists`() {
        controller.onCreate()
        verify(musicNotificationPort).ensureChannelExists()
    }

    @Test
    fun `state with song calls service startForeground`() {
        controller.onCreate()
        playbackState.value = PlaybackState(currentSong = song, isPlaying = true)

        verify(service, atLeastOnce()).startForeground(eq(ForegroundNotificationController.NOTIFICATION_ID), any())
    }

    @Test
    fun `state without song calls service stopForeground`() {
        controller.onCreate()
        playbackState.value = PlaybackState(currentSong = song)
        playbackState.value = PlaybackState()

        verify(service, atLeastOnce()).stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
    }

    @Test
    fun `onStartCommand builds notification from current state`() {
        val state = PlaybackState(currentSong = song)
        playbackState.value = state

        controller.onStartCommand()

        verify(musicNotificationPort).buildSpec(state)
        verify(service).startForeground(eq(ForegroundNotificationController.NOTIFICATION_ID), any())
    }

    @Test
    fun `detach cancels state collection — no further service calls`() {
        controller.onCreate()
        controller.detach()

        playbackState.value = PlaybackState(currentSong = song)

        verify(service, never()).startForeground(any(), any())
    }

    @Test
    fun `onStartCommand satisfies foreground obligation with empty user state`() {
        // Cold-process alarm trigger: startForegroundService was used, but the user
        // session has never played (playbackState.currentSong == null).
        // The obligation must be satisfied even with no song — fallback spec (app name).
        val state = PlaybackState()
        playbackState.value = state

        controller.onStartCommand()

        verify(service).startForeground(eq(ForegroundNotificationController.NOTIFICATION_ID), any())
    }

    @Test
    fun `state emitted before attach is ignored (service == null)`() {
        // Detach first (service == null), then trigger state collection via onCreate
        controller.detach()
        controller.onCreate()

        playbackState.value = PlaybackState(currentSong = song)

        verify(service, never()).startForeground(any(), any())
    }

    @Test
    fun `attach before state collect — service ref available on first emit`() {
        // Controller was attached in setUp(); re-attach is also fine.
        controller.attach(service)
        controller.onCreate()
        playbackState.value = PlaybackState(currentSong = song)

        verify(service).startForeground(eq(ForegroundNotificationController.NOTIFICATION_ID), any())
    }

    @Test
    fun `onDestroy stops foreground and cancels collection`() {
        controller.onCreate()
        controller.onDestroy()

        // After destroy, state emissions must not reach the service
        playbackState.value = PlaybackState(currentSong = song)
        verify(service, never()).startForeground(any(), any())
    }

    @Test
    fun `service recreation — old controller collection cancelled`() {
        // In production, Service recreation gets a fresh ForegroundNotificationController
        // (unscoped, per-Service). Here we simulate just the teardown half: after detach,
        // the controller must not interact with the service on subsequent state emissions.
        controller.onCreate()
        controller.detach()

        val newService: MusicPlaybackService = mock()
        controller.attach(newService)
        playbackState.value = PlaybackState(currentSong = song)

        verify(newService, never()).startForeground(any(), any())
    }
}
