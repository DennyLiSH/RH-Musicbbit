package com.rabbithole.musicbbit.service.playback

import kotlinx.coroutines.flow.SharedFlow

/**
 * Abstraction for an audio playback runtime with queue support.
 *
 * Production adapter: ExoPlayerAdapter (wraps androidx.media3.exoplayer.ExoPlayer).
 * Test adapter: a fake implementation can replay scripted [PlayerEvent]s.
 *
 * The interface is the test surface — callers (services, sessions) interact only with this
 * port and the [events] stream, never with ExoPlayer directly. This allows alarm fire
 * orchestration to be JVM-unit-testable without Robolectric.
 *
 * Threading: implementations should accept calls from the main thread. Internal
 * synchronisation is the adapter's responsibility.
 */
interface PlayerPort {

    /**
     * Hot stream of player events. Subscribe via [SharedFlow.collect].
     */
    val events: SharedFlow<PlayerEvent>

    /**
     * Replace the playback queue and prepare for playback.
     *
     * Does not start playback automatically — call [play] afterwards.
     *
     * @param items Ordered list of items to enqueue.
     * @param startIndex Initial item index in [items]. Coerced to [0, items.lastIndex] by callers.
     * @param startPositionMs Initial position within the start item, in milliseconds.
     */
    fun setQueue(items: List<PlayItem>, startIndex: Int, startPositionMs: Long)

    fun play()
    fun pause()
    fun stop()
    fun clearQueue()

    fun seekTo(positionMs: Long)
    fun next()
    fun previous()

    fun isPlaying(): Boolean
    fun hasNext(): Boolean
    fun hasPrevious(): Boolean
    fun currentItemIndex(): Int
    fun currentPositionMs(): Long
    fun durationMs(): Long

    fun setShuffleEnabled(enabled: Boolean)
    fun setRepeatMode(mode: PlayerRepeatMode)

    /**
     * Release underlying resources. Idempotent — safe to call multiple times.
     */
    fun release()
}

/**
 * Which audio output stream the playback runtime targets: USAGE_ALARM (bypasses silent
 * mode and most DND filters) or USAGE_MEDIA. Kept off [PlayerPort] — stream selection is
 * an alarm-specific concern, not queue-player semantics.
 *
 * Production adapter: the same [ExoPlayerAdapter] instance that fills [PlayerPort].
 * Test adapter: a fake recording the last stream choice.
 */
interface AudioStreamPort {

    /**
     * @param alarmStream true routes audio through the alarm stream; false restores the
     *   media stream. Must be called before playback starts on the new stream.
     */
    fun setAlarmStream(alarmStream: Boolean)
}

/**
 * A single item in a [PlayerPort] queue.
 *
 * @param uri Source URI (file path or content URI).
 * @param tag Opaque payload propagated back through [PlayerEvent.MediaItemTransition.itemTag].
 *            Callers typically attach domain objects (e.g. Song) here.
 */
data class PlayItem(
    val uri: String,
    val tag: Any? = null,
)

enum class PlayerRepeatMode { OFF, ONE, ALL }
