package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.service.playback.PlaybackTransition
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmFireTransitionRouterTest {

    private val testScope = TestScope()
    private val playingState = AlarmFireState.Playing(alarmId = 1L, currentSong = null)

    private fun router(autoStopController: AutoStopController = AutoStopController(testScope)) =
        AlarmFireTransitionRouter(autoStopController)

    @Test
    fun `QueueEnded always returns StopPlayback`() = runTest {
        val router = router()
        val result = router.route(playingState, PlaybackTransition.QueueEnded)
        assertEquals(AlarmFireTransitionRouter.Action.StopPlayback, result)
    }

    @Test
    fun `QueueEnded returns StopPlayback even with no auto-stop configured`() = runTest {
        val controller = AutoStopController(testScope)
        // No auto-stop started — songsRemaining = 0
        val router = router(controller)
        val result = router.route(playingState, PlaybackTransition.QueueEnded)
        assertEquals(AlarmFireTransitionRouter.Action.StopPlayback, result)
    }

    @Test
    fun `SongCompleted with song counter reaching zero returns StopPlayback`() = runTest {
        val controller = AutoStopController(testScope)
        controller.start(com.rabbithole.musicbbit.domain.model.AutoStop.BySongCount(1)) {}
        val router = router(controller)

        val result = router.route(playingState, PlaybackTransition.SongCompleted(songId = 1L))
        assertEquals(AlarmFireTransitionRouter.Action.StopPlayback, result)
    }

    @Test
    fun `SongCompleted with remaining counter returns Ignore`() = runTest {
        val controller = AutoStopController(testScope)
        controller.start(com.rabbithole.musicbbit.domain.model.AutoStop.BySongCount(3)) {}
        val router = router(controller)

        val result = router.route(playingState, PlaybackTransition.SongCompleted(songId = 1L))
        assertEquals(AlarmFireTransitionRouter.Action.Ignore, result)
    }

    @Test
    fun `SongCompleted with no counter and no extendToEnd returns Ignore`() = runTest {
        val router = router()
        val result = router.route(playingState, PlaybackTransition.SongCompleted(songId = 1L))
        assertEquals(AlarmFireTransitionRouter.Action.Ignore, result)
    }

    @Test
    fun `SongCompleted with extendToEnd returns StopPlayback`() = runTest {
        val controller = AutoStopController(testScope)
        controller.setExtendToEnd(true)
        val router = router(controller)

        val result = router.route(playingState, PlaybackTransition.SongCompleted(songId = 1L))
        assertEquals(AlarmFireTransitionRouter.Action.StopPlayback, result)
    }

    @Test
    fun `PlaybackStopped returns PlaybackFullyStopped`() = runTest {
        val router = router()
        val result = router.route(playingState, PlaybackTransition.PlaybackStopped)
        assertEquals(AlarmFireTransitionRouter.Action.PlaybackFullyStopped, result)
    }

    @Test
    fun `Idle state ignores all transitions`() = runTest {
        val router = router()
        val transitions = listOf(
            PlaybackTransition.SongCompleted(1L),
            PlaybackTransition.QueueEnded,
            PlaybackTransition.PlaybackStopped,
        )
        for (transition in transitions) {
            assertEquals(
                "Expected Ignore for $transition in Idle state",
                AlarmFireTransitionRouter.Action.Ignore,
                router.route(AlarmFireState.Idle, transition),
            )
        }
    }

    @Test
    fun `Loading state ignores all transitions`() = runTest {
        val router = router()
        val result = router.route(
            AlarmFireState.Loading(alarmId = 1L),
            PlaybackTransition.QueueEnded,
        )
        assertEquals(AlarmFireTransitionRouter.Action.Ignore, result)
    }

    @Test
    fun `Paused state ignores all transitions`() = runTest {
        val router = router()
        val result = router.route(
            AlarmFireState.Paused(alarmId = 1L, currentSong = null, positionMs = 0L),
            PlaybackTransition.QueueEnded,
        )
        assertEquals(AlarmFireTransitionRouter.Action.Ignore, result)
    }

    @Test
    fun `Stopped state ignores all transitions`() = runTest {
        val router = router()
        val result = router.route(AlarmFireState.Stopped, PlaybackTransition.QueueEnded)
        assertEquals(AlarmFireTransitionRouter.Action.Ignore, result)
    }
}
