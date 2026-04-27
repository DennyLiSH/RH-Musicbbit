package com.rabbithole.musicbbit.service.playback

/**
 * Events emitted by [PlayerPort].
 *
 * Mirrors the relevant subset of ExoPlayer's Player.Listener callbacks. Callers should treat
 * unknown subtypes as no-ops to remain forward-compatible.
 */
sealed class PlayerEvent {

    /**
     * Emitted when [PlayerPort.isPlaying] transitions.
     */
    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvent()

    /**
     * Emitted when the current item in the queue changes (auto-advance, seek, repeat, etc.).
     *
     * @param itemTag The opaque tag attached to the now-current [PlayItem].
     * @param itemIndex Index in the queue of the current item.
     * @param reason Why the transition happened.
     */
    data class MediaItemTransition(
        val itemTag: Any?,
        val itemIndex: Int,
        val reason: TransitionReason,
    ) : PlayerEvent()

    /**
     * Emitted once the player buffers the current item enough to know its duration.
     */
    data class PlaybackReady(val durationMs: Long) : PlayerEvent()

    /**
     * Emitted on seek/discontinuity transitions within the current item.
     */
    data class PositionDiscontinuity(
        val newPositionMs: Long,
        val itemIndex: Int,
    ) : PlayerEvent()

    /**
     * Emitted when the player reaches the end of the queue (STATE_ENDED).
     */
    data object QueueEnded : PlayerEvent()
}

enum class TransitionReason { AUTO, SEEK, REPEAT, OTHER }
