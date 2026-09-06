package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.di.MainDispatcher
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.PlaybackState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * Shared implementation behind [PlaybackSession] and [AlarmPlaybackSession]: state
 * reduction, progress tracking, audio-focus handling, and the stop sequence.
 *
 * The two concrete sessions differ only in how a queue starts ([PlaybackSession.playQueue]
 * restores progress, [AlarmPlaybackSession.playAlarmQueue] routes the alarm stream) and in
 * their queue-ended policy ([PlaybackSession] stops immediately; [AlarmPlaybackSession]
 * defers the stop to AlarmFireSession and suppresses the final save via
 * `queueEndedPending`). That policy is the one [handleQueueEnded] hook; everything else
 * lives here so ordering contracts (save/tick/deactivate/emit) are maintained in one place.
 *
 * Subclasses expose the public session interface; [close] must be called by whoever owns
 * the session lifetime.
 */
abstract class SessionCore protected constructor(
    private val playerPort: PlayerPort,
    protected val playbackProgressRepository: PlaybackProgressRepository,
    protected val audioFocusPort: AudioFocusPort,
    protected val serviceStarter: ServiceStarter,
    protected val playbackCoordinator: PlaybackCoordinator,
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
    private val syncPositionOnPlayStart: Boolean,
) : PlaybackCoordinator.PlaybackConsumer {

    protected val sessionJob = SupervisorJob()
    protected val sessionScope = CoroutineScope(sessionJob + mainDispatcher)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _playbackTransitions = MutableSharedFlow<PlaybackTransition>(extraBufferCapacity = 1)
    val playbackTransitions: Flow<PlaybackTransition> = _playbackTransitions.asSharedFlow()

    protected val progressTracker = PlaybackProgressTracker(
        scope = sessionScope,
        playbackProgressRepository = playbackProgressRepository,
        playerPort = playerPort,
        getState = { _playbackState.value }
    )

    private var wasPausedByFocusLoss = false

    // -------------------------------------------------------------------------
    // PlaybackCoordinator.PlaybackConsumer
    // -------------------------------------------------------------------------

    final override fun onPlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.IsPlayingChanged -> handleIsPlayingChanged(event.isPlaying)
            is PlayerEvent.MediaItemTransition -> handleMediaItemTransition(event)
            is PlayerEvent.PlaybackReady -> _playbackState.update { it.copy(durationMs = event.durationMs) }
            is PlayerEvent.PositionDiscontinuity -> handlePositionDiscontinuity(event)
            is PlayerEvent.QueueEnded -> handleQueueEnded()
        }
    }

    final override fun onFocusLoss() {
        Timber.i("Audio focus lost: pausing playback")
        if (_playbackState.value.isPlaying) {
            pause()
            wasPausedByFocusLoss = true
        }
    }

    final override fun onFocusLossTransient() {
        Timber.i("Audio focus lost transiently: pausing playback")
        if (_playbackState.value.isPlaying) {
            pause()
            wasPausedByFocusLoss = true
        }
    }

    final override fun onFocusGain() {
        Timber.i("Audio focus gained")
        if (wasPausedByFocusLoss &&
            !playerPort.isPlaying() &&
            _playbackState.value.currentSong != null
        ) {
            wasPausedByFocusLoss = false
            resume()
        }
    }

    final override fun onDeactivated() {
        Timber.i("$logTag deactivated by coordinator handoff")
        progressTracker.stopTickLoop()
        progressTracker.stopSaveLoop()
        // Do NOT call saveProgress here — at this point playerPort may already be
        // reconfigured for the incoming consumer (e.g. AlarmFireSession.preloadFirstSong
        // runs before activate, or the reverse handoff reconfigured the stream). Reading
        // playerPort.currentPositionMs() would return the new consumer's position (~0 for
        // a freshly set alarm queue), corrupting progress. Rely on the last periodic save
        // (<=5s stale) as restore point.
        _playbackState.update { it.copy(isPlaying = false) }
        wasPausedByFocusLoss = false
    }

    // -------------------------------------------------------------------------
    // Shared session behaviour
    // -------------------------------------------------------------------------

    fun pause() {
        Timber.i("Pausing playback")
        wasPausedByFocusLoss = false
        playerPort.pause()
        progressTracker.saveProgress()
    }

    fun resume() {
        Timber.i("Resuming playback")
        if (!audioFocusPort.requestFocus()) {
            Timber.w("Failed to gain audio focus, cannot resume")
            return
        }
        if (!playerPort.isPlaying()) {
            playerPort.play()
        }
    }

    fun seekTo(positionMs: Long) {
        Timber.d("Seeking to $positionMs ms")
        playerPort.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    /**
     * Full stop sequence: abandon focus, save (unless [skipSave]), stop the player,
     * reset state, emit [PlaybackTransition.PlaybackStopped], and deactivate.
     */
    protected fun coreStop(skipSave: Boolean) {
        Timber.i("Stopping playback")
        audioFocusPort.abandonFocus()
        if (!skipSave && _playbackState.value.currentSong != null) {
            progressTracker.saveProgress()
        }
        playerPort.stop()
        playerPort.clearQueue()
        _playbackState.update { PlaybackState() }
        _playbackTransitions.tryEmit(PlaybackTransition.PlaybackStopped)
        progressTracker.stopSaveLoop()
        progressTracker.stopTickLoop()
        playbackCoordinator.deactivate(this)
        serviceStarter.stopService()
    }

    /**
     * Gracefully shuts down the session. Cancels internal coroutines and stops progress
     * tracking. Safe to call multiple times; subsequent calls are no-ops.
     */
    fun close() {
        Timber.i("$logTag closing")
        sessionJob.cancel()
        progressTracker.stopSaveLoop()
        progressTracker.stopTickLoop()
    }

    // -------------------------------------------------------------------------
    // Hooks
    // -------------------------------------------------------------------------

    /** Queue-ended policy. User playback stops immediately; alarm playback defers. */
    protected abstract fun handleQueueEnded()

    /** The only handler with a per-session delta: whether to sync position on play start. */
    private fun handleIsPlayingChanged(isPlaying: Boolean) {
        Timber.d("Player isPlaying changed: $isPlaying")
        if (isPlaying) {
            _playbackState.update {
                it.copy(
                    isPlaying = true,
                    positionMs = if (syncPositionOnPlayStart) playerPort.currentPositionMs() else it.positionMs
                )
            }
            progressTracker.startTickLoop(PROGRESS_TICK_INTERVAL_MS) { pos ->
                _playbackState.update { it.copy(positionMs = pos) }
            }
            progressTracker.startSaveLoop(PROGRESS_SAVE_INTERVAL_MS)
        } else {
            _playbackState.update { it.copy(isPlaying = false) }
            progressTracker.stopTickLoop()
            progressTracker.stopSaveLoop()
            progressTracker.saveProgress()
        }
    }

    private fun handleMediaItemTransition(event: PlayerEvent.MediaItemTransition) {
        val song = event.itemTag as? Song
        Timber.d("Media item transitioned to: ${song?.title}")
        _playbackState.update {
            it.copy(
                currentSong = song,
                positionMs = 0,
                durationMs = song?.durationMs ?: 0,
                queueIndex = event.itemIndex
            )
        }
        if (event.reason == TransitionReason.AUTO) {
            _playbackTransitions.tryEmit(PlaybackTransition.SongCompleted(song?.id ?: -1))
        }
    }

    private fun handlePositionDiscontinuity(event: PlayerEvent.PositionDiscontinuity) {
        _playbackState.update {
            it.copy(
                positionMs = event.newPositionMs,
                queueIndex = event.itemIndex
            )
        }
    }

    protected fun updateState(transform: (PlaybackState) -> PlaybackState) {
        _playbackState.update(transform)
    }

    protected fun tryEmitTransition(transition: PlaybackTransition) {
        _playbackTransitions.tryEmit(transition)
    }

    protected val logTag: String
        get() = this::class.simpleName ?: "SessionCore"

    companion object {
        protected const val PROGRESS_SAVE_INTERVAL_MS = 5000L
        protected const val PROGRESS_TICK_INTERVAL_MS = 500L
    }
}
