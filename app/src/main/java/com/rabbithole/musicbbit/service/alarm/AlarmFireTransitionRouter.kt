package com.rabbithole.musicbbit.service.alarm

import com.rabbithole.musicbbit.service.playback.PlaybackTransition

/**
 * Routes playback transitions to actions for the alarm fire session.
 *
 * Encapsulates the decision matrix: given the current [AlarmFireState] and a
 * [PlaybackTransition], what should the session do? This keeps the collect loop
 * in [AlarmFireSession] shallow and makes the transition rules independently
 * testable.
 */
internal class AlarmFireTransitionRouter(
    private val autoStopController: AutoStopController,
) {

    sealed interface Action {
        /** Ask the session to stop playback (auto-stop or extend-to-end). */
        data object StopPlayback : Action
        /** Playback has fully stopped; session should release resources and reset state. */
        data object PlaybackFullyStopped : Action
        /** No action required for this transition in the current state. */
        data object Ignore : Action
    }

    /**
     * Given the current alarm state and a playback transition, return the action
     * the session should take.
     */
    fun route(
        currentState: AlarmFireState,
        transition: PlaybackTransition,
    ): Action = when {
        currentState !is AlarmFireState.Playing -> Action.Ignore
        transition is PlaybackTransition.SongCompleted -> handleSongCompleted()
        transition is PlaybackTransition.QueueEnded -> handleQueueEnded()
        transition is PlaybackTransition.PlaybackStopped -> Action.PlaybackFullyStopped
        else -> Action.Ignore
    }

    private fun handleSongCompleted(): Action {
        val shouldStop = autoStopController.onSongCompleted() ||
                autoStopController.isExtendToEnd()
        return if (shouldStop) Action.StopPlayback else Action.Ignore
    }

    private fun handleQueueEnded(): Action {
        return if (autoStopController.onQueueEnded()) Action.StopPlayback else Action.Ignore
    }
}
