package com.rabbithole.musicbbit.service.playback

/**
 * Narrow, alarm-domain-friendly transitions exposed by [PlaybackSession].
 *
 * Unlike [PlayerEvent] — which mirrors ExoPlayer's full callback taxonomy — this type
 * only carries the transitions the alarm layer needs to make stopping decisions.
 * It is intentionally minimal so that changes to the playback runtime's internal
 * event model do not ripple into the alarm domain.
 */
sealed class PlaybackTransition {
    /**
     * Emitted when a song completes naturally (auto-advance, not user seek).
     */
    data class SongCompleted(val songId: Long) : PlaybackTransition()

    /**
     * Emitted when the player reaches the end of the queue.
     */
    data object QueueEnded : PlaybackTransition()

    /**
     * Emitted when playback is fully stopped and the session has been reset.
     */
    data object PlaybackStopped : PlaybackTransition()
}
