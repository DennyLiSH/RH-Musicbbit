package com.rabbithole.musicbbit.service.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

/**
 * [PlayerPort] adapter backed by androidx.media3 ExoPlayer.
 *
 * Singleton — the underlying ExoPlayer instance lives for the application's lifetime,
 * shared between MusicPlaybackService and AlarmFireSession. The OS reclaims native
 * resources on process death; explicit [release] is supported but not required.
 */
@Singleton
class ExoPlayerAdapter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PlayerPort {

    private val _events = MutableSharedFlow<PlayerEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            tryEmit(PlayerEvent.IsPlayingChanged(isPlaying))
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            tryEmit(
                PlayerEvent.MediaItemTransition(
                    itemTag = mediaItem?.localConfiguration?.tag,
                    itemIndex = exoPlayer.currentMediaItemIndex,
                    reason = mapTransitionReason(reason),
                )
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                tryEmit(
                    PlayerEvent.PlaybackReady(
                        durationMs = exoPlayer.duration.coerceAtLeast(0),
                    )
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                tryEmit(PlayerEvent.QueueEnded)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            tryEmit(
                PlayerEvent.PositionDiscontinuity(
                    newPositionMs = newPosition.positionMs,
                    itemIndex = exoPlayer.currentMediaItemIndex,
                )
            )
        }
    }

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(listener)
    }

    private var released: Boolean = false

    override fun setQueue(items: List<PlayItem>, startIndex: Int, startPositionMs: Long) {
        ensureAlive()
        val mediaItems = items.map { item ->
            MediaItem.Builder()
                .setUri(item.uri)
                .apply { item.tag?.let { setTag(it) } }
                .build()
        }
        exoPlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
        exoPlayer.prepare()
    }

    override fun play() {
        ensureAlive()
        exoPlayer.play()
    }

    override fun pause() {
        ensureAlive()
        exoPlayer.pause()
    }

    override fun stop() {
        ensureAlive()
        exoPlayer.stop()
    }

    override fun clearQueue() {
        ensureAlive()
        exoPlayer.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) {
        ensureAlive()
        exoPlayer.seekTo(positionMs)
    }

    override fun next() {
        ensureAlive()
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        }
    }

    override fun previous() {
        ensureAlive()
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        }
    }

    override fun isPlaying(): Boolean = if (released) false else exoPlayer.isPlaying

    override fun hasNext(): Boolean = if (released) false else exoPlayer.hasNextMediaItem()

    override fun hasPrevious(): Boolean = if (released) false else exoPlayer.hasPreviousMediaItem()

    override fun currentItemIndex(): Int =
        if (released) -1 else exoPlayer.currentMediaItemIndex

    override fun currentPositionMs(): Long =
        if (released) 0 else exoPlayer.currentPosition.coerceAtLeast(0)

    override fun durationMs(): Long =
        if (released) 0 else exoPlayer.duration.coerceAtLeast(0)

    override fun setShuffleEnabled(enabled: Boolean) {
        ensureAlive()
        exoPlayer.shuffleModeEnabled = enabled
    }

    override fun setRepeatMode(mode: PlayerRepeatMode) {
        ensureAlive()
        exoPlayer.repeatMode = when (mode) {
            PlayerRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            PlayerRepeatMode.ONE -> Player.REPEAT_MODE_ONE
            PlayerRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    override fun release() {
        if (released) return
        released = true
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }

    private fun ensureAlive() {
        check(!released) { "ExoPlayerAdapter has been released" }
    }

    private fun tryEmit(event: PlayerEvent) {
        if (!_events.tryEmit(event)) {
            Timber.w("PlayerEvent dropped due to backpressure: $event")
        }
    }

    private fun mapTransitionReason(reason: Int): TransitionReason = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> TransitionReason.AUTO
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> TransitionReason.SEEK
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> TransitionReason.REPEAT
        else -> TransitionReason.OTHER
    }
}
