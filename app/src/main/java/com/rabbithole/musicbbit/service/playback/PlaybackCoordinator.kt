package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.di.MainDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Routes shared [PlayerPort] events and audio-focus callbacks to the active playback session.
 *
 * Both [PlaybackSession] and [AlarmPlaybackSession] share the single [PlayerPort] instance and
 * the single [AudioFocusPort]. The coordinator subscribes once to each and forwards events to
 * whichever consumer currently owns the player, ensuring only the active session reacts.
 */
@Singleton
class PlaybackCoordinator @Inject constructor(
    private val playerPort: PlayerPort,
    private val audioFocusPort: AudioFocusPort,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {

    /** Consumer of routed player events and focus callbacks. */
    interface PlaybackConsumer {
        fun onPlayerEvent(event: PlayerEvent)
        fun onFocusLoss()
        fun onFocusLossTransient()
        fun onFocusGain()

        /**
         * Called by [PlaybackCoordinator.activate] when another consumer is taking over
         * as the active recipient. Use to stop internal loops (tick/save) and mark the
         * session as inactive.
         *
         * **Must NOT call saveProgress or read playerPort real-time state** — by the time
         * this fires, playerPort may already be configured for the incoming consumer
         * (e.g. AlarmFireSession.preloadFirstSong runs before activate), so reading
         * currentPositionMs() would return the new consumer's position and corrupt
         * progress. Rely on the last periodic save (≤5s stale) as the restore point.
         */
        fun onDeactivated()
    }

    private val coordinatorJob = SupervisorJob()
    private val coordinatorScope = CoroutineScope(coordinatorJob + mainDispatcher)

    @Volatile
    private var activeConsumer: PlaybackConsumer? = null

    private var playerEventsJob: Job? = null

    init {
        audioFocusPort.registerCallbacks(
            onFocusLoss = { dispatchFocusCallback { onFocusLoss() } },
            onFocusLossTransient = { dispatchFocusCallback { onFocusLossTransient() } },
            onFocusGain = { dispatchFocusCallback { onFocusGain() } },
        )
        startCollectingPlayerEvents()
    }

    /**
     * Make [consumer] the active recipient of player events and focus callbacks.
     * If another consumer is currently active, that consumer's [PlaybackConsumer.onDeactivated]
     * is invoked before the swap. Must be called on the main dispatcher.
     */
    fun activate(consumer: PlaybackConsumer) {
        val previous = activeConsumer
        activeConsumer = consumer
        if (previous != null && previous !== consumer) {
            previous.onDeactivated()
            Timber.i(
                "PlaybackCoordinator handed off: ${previous::class.java.simpleName} -> ${consumer::class.java.simpleName}"
            )
        } else {
            Timber.d("PlaybackCoordinator activated: ${consumer::class.java.simpleName}")
        }
    }

    /**
     * Deactivate [consumer] if it is currently active. Idempotent and safe to call from any
     * consumer; if [consumer] is not active, this is a no-op.
     * Must be called on the main dispatcher.
     */
    fun deactivate(consumer: PlaybackConsumer) {
        if (activeConsumer === consumer) {
            activeConsumer = null
            Timber.d("PlaybackCoordinator deactivated: ${consumer::class.java.simpleName}")
        }
    }

    private fun startCollectingPlayerEvents() {
        playerEventsJob?.cancel()
        playerEventsJob = coordinatorScope.launch {
            playerPort.events.collect { event ->
                activeConsumer?.onPlayerEvent(event)
                    ?: Timber.d("Dropping player event: no active consumer ($event)")
            }
        }
    }

    private fun dispatchFocusCallback(dispatch: PlaybackConsumer.() -> Unit) {
        activeConsumer?.dispatch()
            ?: Timber.d("Dropping focus callback: no active consumer")
    }

    /**
     * Cancels internal coroutines. Intended for testing; in production the coordinator
     * lives for the application lifetime.
     */
    fun close() {
        coordinatorJob.cancel()
        playerEventsJob?.cancel()
    }
}
