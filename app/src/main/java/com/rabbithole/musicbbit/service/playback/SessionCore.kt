package com.rabbithole.musicbbit.service.playback

import androidx.annotation.VisibleForTesting
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
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Shared implementation behind [UserPlaybackSession] and [AlarmPlaybackSession]: state
 * reduction, progress tracking, audio-focus handling, and the stop sequence.
 *
 * The two concrete sessions differ only in how a queue starts ([UserPlaybackSession.playQueue]
 * restores progress, [AlarmPlaybackSession.playAlarmQueue] routes the alarm stream) and in
 * their queue-ended policy ([UserPlaybackSession] stops immediately; [AlarmPlaybackSession]
 * defers the stop via [stopDeferred] and emits the [PlaybackTransition] as the single
 * terminal transition). That policy is the one [handleQueueEnded] hook; everything else
 * lives here so ordering contracts (save/tick/deactivate/emit) are maintained in one place.
 *
 * Subclasses expose the public session interface; [close] must be called by whoever owns
 * the session lifetime.
 */
abstract class SessionCore protected constructor(
    private val playerPort: PlayerPort,
    protected val playbackProgressRepository: PlaybackProgressRepository,
    protected val audioFocusPort: AudioFocusPort,
    protected val audioStreamPort: AudioStreamPort,
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
        // Do NOT call saveProgress here — this contract is defensive and must not depend
        // on the caller-side activate/setQueue ordering: any future consumer that
        // reconfigures playerPort before (or while) triggering this callback would make
        // reading playerPort.currentPositionMs() return the new consumer's position,
        // corrupting progress. Rely on the last periodic save (<=5s stale) as restore point.
        _playbackState.update { it.copy(isPlaying = false) }
        wasPausedByFocusLoss = false
    }

    // -------------------------------------------------------------------------
    // Shared session behaviour
    // -------------------------------------------------------------------------

    /**
     * Single choke point for player commands. While another session owns the shared
     * player, commands from this session are dropped (they would act on the owning
     * session's playback). All public command entry points must route through this —
     * new SessionCore commands should be guarded here, not at call sites.
     *
     * @return true when the command was issued, false when it was dropped.
     */
    protected fun issueCommand(command: () -> Unit): Boolean {
        if (playbackCoordinator.isOwnedByAnother(this)) {
            Timber.w("$logTag command dropped: another session owns the player")
            return false
        }
        command()
        return true
    }

    fun pause() {
        issueCommand {
            Timber.i("Pausing playback")
            wasPausedByFocusLoss = false
            playerPort.pause()
            progressTracker.saveProgress()
        }
    }

    fun resume() {
        issueCommand {
            Timber.i("Resuming playback")
            if (!audioFocusPort.requestFocus()) {
                Timber.w("Failed to gain audio focus, cannot resume")
                return@issueCommand
            }
            if (!playerPort.isPlaying()) {
                playerPort.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        issueCommand {
            Timber.d("Seeking to $positionMs ms")
            playerPort.seekTo(positionMs)
            _playbackState.update { it.copy(positionMs = positionMs) }
        }
    }

    /**
     * Full stop sequence: abandon focus, save (unless [skipSave]), stop the player,
     * reset state, emit [PlaybackTransition.PlaybackStopped], and deactivate.
     */
    protected fun coreStop(skipSave: Boolean) {
        Timber.i("Stopping playback")
        teardownPlayer(skipSave)
        _playbackTransitions.tryEmit(PlaybackTransition.PlaybackStopped)
    }

    /**
     * Deferred stop for natural queue end: hold ownership until teardown completes,
     * await any in-flight save (suppressed — the finished song's end position must
     * not be written), then emit [terminal] as the single terminal transition.
     *
     * Ownership is kept for the whole window, so a user session cannot start playing
     * between "queue ended" and "player stopped" — that race is structurally closed.
     */
    protected fun stopDeferred(terminal: PlaybackTransition) {
        sessionScope.launch {
            try {
                progressTracker.stopSaveLoop()
                progressTracker.stopTickLoop()
                progressTracker.cancelAndAwaitPendingSave()
                teardownPlayer(skipSave = true)
                _playbackTransitions.tryEmit(terminal)
            } finally {
                // Ownership must be released even if teardown throws — otherwise the
                // alarm session keeps "owning" the player forever and user playback
                // stays blocked. deactivate is idempotent (identity check).
                playbackCoordinator.deactivate(this@SessionCore)
            }
        }
    }

    /**
     * Shared queue-start sequence — the counterpart of [coreStop]. Ordering contract,
     * maintained here only:
     *
     *   1. audio focus must be gained BEFORE activation (a session that cannot hold
     *      focus must not become the coordinator's active consumer);
     *   2. the audio stream is routed BEFORE the queue is set (stream attributes
     *      apply to the session, not per-item);
     *   3. the foreground service starts BEFORE play (playback must never outrun
     *      its foreground host);
     *   4. state is applied LAST (UI never observes a queue that is not yet started).
     *
     * @param startPlayback how playback actually begins. Default: play immediately.
     *   [UserPlaybackSession.playQueue] passes a coroutine that restores progress
     *   first, then plays.
     * @return false when audio focus was refused — nothing has been activated or
     *   queued; the caller logs its context-specific failure.
     */
    protected fun coreStartQueue(
        items: List<PlayItem>,
        startIndex: Int,
        useAlarmStream: Boolean,
        focusFailLog: String,
        startPlayback: () -> Unit = { playerPort.play() },
        applyState: (PlaybackState) -> PlaybackState,
    ) {
        if (!audioFocusPort.requestFocus()) {
            Timber.w(focusFailLog)
            return
        }
        playbackCoordinator.activate(this)
        audioStreamPort.setAlarmStream(useAlarmStream)
        serviceStarter.startService()
        playerPort.setQueue(items = items, startIndex = startIndex, startPositionMs = 0)
        startPlayback()
        updateState(applyState)
    }

    private fun teardownPlayer(skipSave: Boolean) {
        audioFocusPort.abandonFocus()
        if (!skipSave && _playbackState.value.currentSong != null) {
            progressTracker.saveProgress()
        }
        playerPort.stop()
        playerPort.clearQueue()
        _playbackState.update { PlaybackState() }
        progressTracker.stopSaveLoop()
        progressTracker.stopTickLoop()
        playbackCoordinator.deactivate(this)
        serviceStarter.stopService()
    }

    /**
     * Gracefully shuts down the session. Cancels internal coroutines and stops progress
     * tracking. Safe to call multiple times; subsequent calls are no-ops.
     *
     * Production never closes a session — they live as long as the process.
     * Test-only teardown path.
     */
    @VisibleForTesting
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
