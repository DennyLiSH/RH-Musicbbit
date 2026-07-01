package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.di.MainDispatcher
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackState
import javax.inject.Inject
import javax.inject.Singleton
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
 * Deep module that owns alarm playback.
 *
 * Logically independent from [PlaybackSession] but shares the single [PlayerPort] instance.
 * Events are routed through [PlaybackCoordinator] so only the active session reacts.
 */
@Singleton
class AlarmPlaybackSession @Inject constructor(
    private val playerPort: PlayerPort,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val audioFocusPort: AudioFocusPort,
    private val serviceStarter: ServiceStarter,
    private val playbackCoordinator: PlaybackCoordinator,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : PlaybackCoordinator.PlaybackConsumer {

    private val sessionJob = SupervisorJob()
    private val sessionScope = CoroutineScope(sessionJob + mainDispatcher)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _playbackTransitions = MutableSharedFlow<PlaybackTransition>(extraBufferCapacity = 1)
    val playbackTransitions: Flow<PlaybackTransition> = _playbackTransitions.asSharedFlow()

    private val progressTracker = PlaybackProgressTracker(
        scope = sessionScope,
        playbackProgressRepository = playbackProgressRepository,
        playerPort = playerPort,
        getState = { _playbackState.value }
    )

    private var wasPausedByFocusLoss = false

    /**
     * Set by [handleQueueEnded] to signal that the subsequent [stop] call (driven by
     * AlarmFireSession upon receiving QueueEnded) should skip saveProgress — at this
     * point the queue has ended naturally and any save would write the just-finished
     * song's end position. Cancels the saveProgress branch in [stop] without changing
     * its `currentSong != null` guard semantics.
     */
    private var queueEndedPending = false

    init {
        Timber.i("AlarmPlaybackSession created")
    }

    /**
     * Start playing [songs] as an alarm queue.
     *
     * @param songs Non-empty list of songs to play.
     * @param startIndex Initial item index, coerced to `[0, songs.lastIndex]`.
     * @param playlistId Playlist identifier. Must be positive.
     */
    fun playAlarmQueue(songs: List<Song>, startIndex: Int, playlistId: Long) {
        if (songs.isEmpty()) {
            Timber.w("playAlarmQueue called with empty list")
            return
        }
        if (playlistId <= 0) {
            Timber.w("playAlarmQueue called with invalid playlistId=$playlistId")
            return
        }
        if (!audioFocusPort.requestFocus()) {
            Timber.w("Failed to gain audio focus for alarm playback")
            return
        }

        playbackCoordinator.activate(this)

        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        val startSong = songs[safeIndex]

        Timber.i(
            "Playing alarm queue of ${songs.size} songs, startIndex=$safeIndex, playlistId=$playlistId"
        )
        playerPort.configureForAlarmPlayback(true)
        serviceStarter.startService()

        val mediaItems = songs.map { song ->
            PlayItem(uri = song.path, tag = song)
        }
        playerPort.setQueue(items = mediaItems, startIndex = safeIndex, startPositionMs = 0)
        playerPort.play()

        _playbackState.update {
            it.copy(
                currentSong = startSong,
                currentPlaylistId = playlistId,
                queue = songs,
                queueIndex = safeIndex,
                positionMs = 0,
                durationMs = startSong.durationMs,
                playMode = PlayMode.SEQUENTIAL,
                isPlaying = true,
            )
        }
        // Note: tick/save loops are started by handleIsPlayingChanged(true) — aligned
        // with PlaybackSession. The loops are idempotent (cancel-then-launch) so the
        // IsPlayingChanged event re-starting them is safe.
    }

    fun pause() {
        Timber.i("Pausing alarm playback")
        wasPausedByFocusLoss = false
        playerPort.pause()
        progressTracker.saveProgress()
    }

    fun resume() {
        Timber.i("Resuming alarm playback")
        if (!audioFocusPort.requestFocus()) {
            Timber.w("Failed to gain audio focus, cannot resume alarm playback")
            return
        }
        if (!playerPort.isPlaying()) {
            playerPort.play()
        }
    }

    fun stop() {
        Timber.i("Stopping alarm playback")
        audioFocusPort.abandonFocus()
        val skipSave = queueEndedPending
        queueEndedPending = false
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

    fun preloadFirstSong(uri: String) {
        playerPort.setQueue(
            items = listOf(PlayItem(uri = uri)),
            startIndex = 0,
            startPositionMs = 0,
        )
        Timber.d("Preloaded first song for alarm: $uri")
    }

    fun seekTo(positionMs: Long) {
        Timber.d("Seeking alarm playback to $positionMs ms")
        playerPort.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    /**
     * Gracefully shuts down the session.
     *
     * Cancels internal coroutines, stops progress tracking, and releases resources.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun close() {
        Timber.i("AlarmPlaybackSession closing")
        sessionJob.cancel()
        progressTracker.stopSaveLoop()
        progressTracker.stopTickLoop()
    }

    // -------------------------------------------------------------------------
    // PlaybackCoordinator.PlaybackConsumer
    // -------------------------------------------------------------------------

    override fun onPlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.IsPlayingChanged -> handleIsPlayingChanged(event.isPlaying)
            is PlayerEvent.MediaItemTransition -> handleMediaItemTransition(event)
            is PlayerEvent.PlaybackReady -> _playbackState.update { it.copy(durationMs = event.durationMs) }
            is PlayerEvent.PositionDiscontinuity -> handlePositionDiscontinuity(event)
            is PlayerEvent.QueueEnded -> handleQueueEnded()
        }
    }

    override fun onFocusLoss() {
        Timber.i("Audio focus lost during alarm playback: pausing")
        if (_playbackState.value.isPlaying) {
            pause()
            wasPausedByFocusLoss = true
        }
    }

    override fun onFocusLossTransient() {
        Timber.i("Audio focus lost transiently during alarm playback: pausing")
        if (_playbackState.value.isPlaying) {
            pause()
            wasPausedByFocusLoss = true
        }
    }

    override fun onFocusGain() {
        Timber.i("Audio focus gained during alarm playback")
        if (wasPausedByFocusLoss &&
            !playerPort.isPlaying() &&
            _playbackState.value.currentSong != null
        ) {
            wasPausedByFocusLoss = false
            resume()
        }
    }

    override fun onDeactivated() {
        Timber.i("AlarmPlaybackSession deactivated by coordinator handoff (defensive)")
        progressTracker.stopTickLoop()
        progressTracker.stopSaveLoop()
        // Do NOT call saveProgress — reverse handoff (user manually plays during alarm)
        // means playerPort has been reconfigured by the incoming PlaybackSession.
        _playbackState.update { it.copy(isPlaying = false) }
        wasPausedByFocusLoss = false
    }

    private fun handleIsPlayingChanged(isPlaying: Boolean) {
        _playbackState.update { it.copy(isPlaying = isPlaying) }
        if (isPlaying) {
            progressTracker.startTickLoop(PROGRESS_TICK_INTERVAL_MS) { pos ->
                _playbackState.update { it.copy(positionMs = pos) }
            }
            progressTracker.startSaveLoop(PROGRESS_SAVE_INTERVAL_MS)
        } else {
            progressTracker.stopTickLoop()
            progressTracker.stopSaveLoop()
            progressTracker.saveProgress()
        }
    }

    private fun handleMediaItemTransition(event: PlayerEvent.MediaItemTransition) {
        val song = event.itemTag as? Song
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

    private fun handleQueueEnded() {
        Timber.i("Alarm queue ended")
        val playlistId = _playbackState.value.currentPlaylistId
        queueEndedPending = true
        progressTracker.stopSaveLoop()
        progressTracker.stopTickLoop()
        playbackCoordinator.deactivate(this)
        sessionScope.launch {
            // cancelAndAwaitPendingSave MUST complete before emit QueueEnded — ensures
            // AlarmFireSession's deletePlaylistProgressIfMatches runs after any in-flight
            // save completes (or is cancelled). See commit 20daaa5: prevents pending save
            // from racing past delete.
            progressTracker.cancelAndAwaitPendingSave()
            _playbackTransitions.tryEmit(PlaybackTransition.QueueEnded(playlistId))
        }
    }

    companion object {
        private const val PROGRESS_SAVE_INTERVAL_MS = 5000L
        private const val PROGRESS_TICK_INTERVAL_MS = 500L
    }
}
