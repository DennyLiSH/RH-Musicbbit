package com.rabbithole.musicbbit.service.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Test fake for [PlayerPort].
 *
 * Records queue/play/pause/stop/seek calls and allows tests to emit scripted [PlayerEvent]s.
 * Synchronous event delivery is achieved by using the fake with an [UnconfinedTestDispatcher]
 * collector.
 */
class FakePlayerPort : PlayerPort {

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    var isPlayingValue: Boolean = false
    var hasNextValue: Boolean = false
    var hasPreviousValue: Boolean = false
    var currentItemIndexValue: Int = -1
    var currentPositionMsValue: Long = 0
    var durationMsValue: Long = 0

    var lastShuffleEnabled: Boolean = false
        private set
    var lastRepeatMode: PlayerRepeatMode = PlayerRepeatMode.OFF
        private set

    data class QueueCall(
        val items: List<PlayItem>,
        val startIndex: Int,
        val startPositionMs: Long,
    )

    val queueCalls = mutableListOf<QueueCall>()
    val playCalls = mutableListOf<Unit>()
    val pauseCalls = mutableListOf<Unit>()
    val stopCalls = mutableListOf<Unit>()
    val clearQueueCalls = mutableListOf<Unit>()
    val seekCalls = mutableListOf<Long>()
    val nextCalls = mutableListOf<Unit>()
    val previousCalls = mutableListOf<Unit>()
    val releaseCalls = mutableListOf<Unit>()

    override fun setQueue(items: List<PlayItem>, startIndex: Int, startPositionMs: Long) {
        queueCalls.add(QueueCall(items, startIndex, startPositionMs))
    }

    override fun play() {
        playCalls.add(Unit)
        isPlayingValue = true
    }

    override fun pause() {
        pauseCalls.add(Unit)
        isPlayingValue = false
    }

    override fun stop() {
        stopCalls.add(Unit)
        isPlayingValue = false
    }

    override fun clearQueue() {
        clearQueueCalls.add(Unit)
    }

    override fun seekTo(positionMs: Long) {
        seekCalls.add(positionMs)
        currentPositionMsValue = positionMs
    }

    override fun next() {
        nextCalls.add(Unit)
    }

    override fun previous() {
        previousCalls.add(Unit)
    }

    override fun isPlaying(): Boolean = isPlayingValue

    override fun hasNext(): Boolean = hasNextValue

    override fun hasPrevious(): Boolean = hasPreviousValue

    override fun currentItemIndex(): Int = currentItemIndexValue

    override fun currentPositionMs(): Long = currentPositionMsValue

    override fun durationMs(): Long = durationMsValue

    override fun setShuffleEnabled(enabled: Boolean) {
        lastShuffleEnabled = enabled
    }

    override fun setRepeatMode(mode: PlayerRepeatMode) {
        lastRepeatMode = mode
    }

    override fun release() {
        releaseCalls.add(Unit)
    }

    fun emitEvent(event: PlayerEvent): Boolean = _events.tryEmit(event)
}
