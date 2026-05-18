package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.di.MainDispatcher
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.PlayMode
import com.rabbithole.musicbbit.service.PlaybackSource
import com.rabbithole.musicbbit.service.PlaybackState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Deep module that encapsulates all playback logic extracted from [MusicPlaybackService].
 *
 * Responsibilities:
 *   - Managing [PlaybackState] via a [StateFlow]
 *   - Driving [PlayerPort] (queue, play, pause, seek, etc.)
 *   - Handling [PlayerEvent]s and reflecting them into state
 *   - Coordinating audio focus, service lifecycle, volume ramp, and progress tracking
 *   - Distinguishing user-initiated vs alarm-initiated playback via [PlaybackSource]
 *
 * This class is a [@Singleton] with application-level lifetime. It does **not**
 * interact with Android Service specifics such as [startForeground] / [stopForeground];
 * those remain in [MusicPlaybackService].
 */
@Singleton
class PlaybackSession @Inject constructor(
    private val playerPort: PlayerPort,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val musicNotificationPort: MusicNotificationPort,
    private val serviceStarter: ServiceStarter,
    private val audioFocusPort: AudioFocusPort,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {

    private val sessionJob = SupervisorJob()
    private val sessionScope = CoroutineScope(sessionJob + mainDispatcher)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    val playerEvents: SharedFlow<PlayerEvent> = playerPort.events

    private val _playbackTransitions = MutableSharedFlow<PlaybackTransition>(extraBufferCapacity = 1)
    val playbackTransitions: Flow<PlaybackTransition> = _playbackTransitions.asSharedFlow()

    private lateinit var progressTracker: PlaybackProgressTracker
    private var playerEventsJob: Job? = null

    private var wasPausedByFocusLoss = false

    init {
        Timber.i("PlaybackSession created")

        musicNotificationPort.ensureChannelExists()

        progressTracker = PlaybackProgressTracker(
            scope = sessionScope,
            playbackProgressRepository = playbackProgressRepository,
            playerPort = playerPort,
            getState = { _playbackState.value }
        )

        audioFocusPort.registerCallbacks(
            onFocusLoss = {
                Timber.i("Audio focus lost: pausing playback")
                if (_playbackState.value.isPlaying) {
                    wasPausedByFocusLoss = true
                    pause()
                }
            },
            onFocusLossTransient = {
                Timber.i("Audio focus lost transiently: pausing playback")
                if (_playbackState.value.isPlaying) {
                    wasPausedByFocusLoss = true
                    pause()
                }
            },
            onFocusGain = {
                Timber.i("Audio focus gained")
                if (wasPausedByFocusLoss &&
                    !playerPort.isPlaying() &&
                    _playbackState.value.currentSong != null
                ) {
                    wasPausedByFocusLoss = false
                    resume()
                }
            }
        )

        observePlayerEvents()
    }

    private fun observePlayerEvents() {
        playerEventsJob?.cancel()
        playerEventsJob = sessionScope.launch {
            try {
                playerPort.events.collect { event ->
                    when (event) {
                        is PlayerEvent.IsPlayingChanged -> handleIsPlayingChanged(event.isPlaying)
                        is PlayerEvent.MediaItemTransition -> handleMediaItemTransition(event)
                        is PlayerEvent.PlaybackReady -> {
                            _playbackState.update { it.copy(durationMs = event.durationMs) }
                            updateNotification()
                        }
                        is PlayerEvent.PositionDiscontinuity -> handlePositionDiscontinuity(event)
                        is PlayerEvent.QueueEnded -> handleQueueEnded()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Player event collection failed")
            }
        }
    }

    private fun handleIsPlayingChanged(isPlaying: Boolean) {
        Timber.d("Player isPlaying changed: $isPlaying")
        if (isPlaying) {
            _playbackState.update {
                it.copy(
                    isPlaying = true,
                    positionMs = playerPort.currentPositionMs()
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
        updateNotification()
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
        updateNotification()
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
        val state = _playbackState.value
        if (state.source == PlaybackSource.USER) {
            Timber.i("Queue ended for USER source, stopping playback")
            stop()
        } else {
            Timber.i("Queue ended for ALARM source, emitting QueueEnded")
            _playbackTransitions.tryEmit(PlaybackTransition.QueueEnded)
        }
    }

    private fun updateNotification() {
        musicNotificationPort.buildAndNotify(_playbackState.value)
    }

    // -------- Public playback API --------------------------------------------

    fun play(song: Song, playlistId: Long) {
        if (!audioFocusPort.requestFocus()) {
            Timber.w("Failed to gain audio focus")
            return
        }
        Timber.i("Playing single song: ${song.title}, playlistId=$playlistId")
        playerPort.configureForAlarmPlayback(false)
        serviceStarter.startService()

        playerPort.setQueue(
            items = listOf(PlayItem(uri = song.path, tag = song)),
            startIndex = 0,
            startPositionMs = 0,
        )
        playerPort.play()

        _playbackState.update {
            it.copy(
                currentSong = song,
                currentPlaylistId = playlistId,
                queue = listOf(song),
                queueIndex = 0,
                positionMs = 0,
                durationMs = song.durationMs,
                source = PlaybackSource.USER,
                alarmId = null,
                alarmLabel = null,
            )
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int, playlistId: Long) {
        if (songs.isEmpty()) {
            Timber.w("playQueue called with empty list")
            return
        }
        if (!audioFocusPort.requestFocus()) {
            Timber.w("Failed to gain audio focus")
            return
        }
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        val startSong = songs[safeIndex]

        Timber.i(
            "Playing queue of ${songs.size} songs, startIndex=$safeIndex, playlistId=$playlistId"
        )
        playerPort.configureForAlarmPlayback(false)
        serviceStarter.startService()

        val mediaItems = songs.map { song ->
            PlayItem(uri = song.path, tag = song)
        }

        playerPort.setQueue(items = mediaItems, startIndex = safeIndex, startPositionMs = 0)

        sessionScope.launch {
            val progressResult = playbackProgressRepository.getProgress(startSong.id, playlistId)
            progressResult.getOrNull()?.let { progress ->
                Timber.i("Restoring progress for song ${startSong.id}: ${progress.positionMs}ms")
                playerPort.seekTo(progress.positionMs)
            }
            playerPort.play()
        }

        _playbackState.update {
            it.copy(
                currentSong = startSong,
                currentPlaylistId = playlistId,
                queue = songs,
                queueIndex = safeIndex,
                positionMs = 0,
                durationMs = startSong.durationMs,
                source = PlaybackSource.USER,
                alarmId = null,
                alarmLabel = null,
            )
        }
    }

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

    fun next() {
        Timber.i("Skipping to next")
        if (playerPort.hasNext()) {
            progressTracker.saveProgress()
            playerPort.next()
        } else {
            Timber.d("No next media item")
        }
    }

    fun previous() {
        Timber.i("Skipping to previous")
        if (playerPort.hasPrevious()) {
            progressTracker.saveProgress()
            playerPort.previous()
        } else {
            Timber.d("No previous media item")
        }
    }

    fun seekTo(positionMs: Long) {
        Timber.d("Seeking to $positionMs ms")
        playerPort.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    fun stop() {
        Timber.i("Stopping playback")
        audioFocusPort.abandonFocus()
        progressTracker.saveProgress()
        playerPort.stop()
        playerPort.clearQueue()
        _playbackState.update { PlaybackState() }
        _playbackTransitions.tryEmit(PlaybackTransition.PlaybackStopped)
        progressTracker.stopSaveLoop()
        progressTracker.stopTickLoop()
        serviceStarter.stopService()
    }

    fun setPlayMode(mode: PlayMode) {
        Timber.i("Setting play mode: $mode")
        playerPort.setShuffleEnabled(mode == PlayMode.RANDOM)
        playerPort.setRepeatMode(
            when (mode) {
                PlayMode.REPEAT_ONE -> PlayerRepeatMode.ONE
                else -> PlayerRepeatMode.OFF
            }
        )
        _playbackState.update { it.copy(playMode = mode) }
    }

    fun preloadFirstSong(uri: String) {
        playerPort.setQueue(
            items = listOf(PlayItem(uri = uri)),
            startIndex = 0,
            startPositionMs = 0,
        )
        Timber.d("Preloaded first song: $uri")
    }

    fun playAlarmQueue(
        songs: List<Song>,
        startIndex: Int,
        playlistId: Long,
        alarmId: Long,
        alarmLabel: String? = null,
    ) {
        playerPort.configureForAlarmPlayback(true)
        playQueue(songs, startIndex, playlistId)
        _playbackState.update {
            it.copy(
                alarmId = alarmId,
                alarmLabel = alarmLabel,
                source = PlaybackSource.ALARM
            )
        }
    }

    companion object {
        private const val PROGRESS_SAVE_INTERVAL_MS = 5000L
        private const val PROGRESS_TICK_INTERVAL_MS = 500L
    }
}
