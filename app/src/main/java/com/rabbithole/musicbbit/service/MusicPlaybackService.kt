package com.rabbithole.musicbbit.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.service.alarm.AlarmFireSession
import com.rabbithole.musicbbit.service.alarm.AlarmPlaybackHost
import com.rabbithole.musicbbit.service.alarm.ports.VolumeRampPort
import com.rabbithole.musicbbit.service.alarm.ports.WakeLockPort
import com.rabbithole.musicbbit.service.playback.PlayItem
import com.rabbithole.musicbbit.service.playback.PlayerEvent
import com.rabbithole.musicbbit.service.playback.PlayerPort
import com.rabbithole.musicbbit.service.playback.PlayerRepeatMode
import com.rabbithole.musicbbit.service.playback.PlaybackProgressTracker
import com.rabbithole.musicbbit.service.playback.TransitionReason
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground music playback service.
 *
 * Manages audio playback through [PlayerPort] (production adapter wraps ExoPlayer),
 * persists playback progress, and displays a media-style notification.
 * Playback state is exposed via [StateFlow] for UI observation.
 */
@AndroidEntryPoint
class MusicPlaybackService : Service(), AlarmPlaybackHost {

    @Inject
    lateinit var playbackProgressRepository: PlaybackProgressRepository

    @Inject
    lateinit var volumeRampPort: VolumeRampPort

    @Inject
    lateinit var playerPort: PlayerPort

    @Inject
    lateinit var wakeLockPort: WakeLockPort

    @Inject
    lateinit var alarmFireSession: AlarmFireSession

    @Inject
    lateinit var audioFocusManager: AudioFocusManager

    private var wasPausedByFocusLoss = false

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    @Inject
    lateinit var notificationManager: MusicNotificationManager

    private lateinit var progressTracker: PlaybackProgressTracker
    private var playerEventsJob: Job? = null

    private fun handleIsPlayingChanged(isPlaying: Boolean) {
        Timber.d("Player isPlaying changed: $isPlaying")
        if (isPlaying) {
            _playbackState.update {
                it.copy(
                    isPlaying = true,
                    positionMs = playerPort.currentPositionMs()
                )
            }
            progressTracker.startSaveLoop(PROGRESS_SAVE_INTERVAL_MS)
        } else {
            _playbackState.update { it.copy(isPlaying = false) }
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
        updateNotification()

        if (event.reason == TransitionReason.AUTO) {
            alarmFireSession.onSongCompleted()
        }

        // Check extend-to-end (owned by AlarmFireSession)
        if (alarmFireSession.isExtendToEnd() && event.reason == TransitionReason.AUTO) {
            Timber.i("Extend-to-end: stopping after current song")
            stop()
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

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = MusicBinder()

    override fun onCreate() {
        super.onCreate()
        Timber.i("MusicPlaybackService created")
        notificationManager.createChannel()
        progressTracker = PlaybackProgressTracker(
            scope = serviceScope,
            playbackProgressRepository = playbackProgressRepository,
            playerPort = playerPort,
            getState = { _playbackState.value }
        )
        observePlayerEvents()
        audioFocusManager.registerCallbacks(
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
                if (wasPausedByFocusLoss && !playerPort.isPlaying() && _playbackState.value.currentSong != null) {
                    wasPausedByFocusLoss = false
                    resume()
                }
            }
        )
        progressTracker.startTickLoop(PROGRESS_TICK_INTERVAL_MS) { pos ->
            _playbackState.update { it.copy(positionMs = pos) }
        }
        alarmFireSession.bindHost(this)
    }

    private fun observePlayerEvents() {
        playerEventsJob?.cancel()
        playerEventsJob = serviceScope.launch {
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
                        is PlayerEvent.QueueEnded -> alarmFireSession.onQueueEnded()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Player event collection failed")
            }
        }
    }


    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("MusicPlaybackService started, action=${intent?.action}")
        startForeground(NOTIFICATION_ID, notificationManager.buildNotification(_playbackState.value))

        when (intent?.action) {
            ACTION_PLAY_ALARM -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                val isAlarmTrigger = intent.getBooleanExtra(EXTRA_IS_ALARM_TRIGGER, false)

                if (isAlarmTrigger) {
                    wakeLockPort.acquire(ALARM_WAKE_LOCK_TIMEOUT_MS)
                }

                alarmFireSession.fire(alarmId, isAlarmTrigger)
            }
            ACTION_PREVIOUS -> previous()
            ACTION_TOGGLE_PLAY_PAUSE -> {
                if (_playbackState.value.isPlaying) pause() else resume()
            }
            ACTION_NEXT -> next()
        }

        return START_STICKY
    }

    /**
     * Play a single song.
     *
     * @param song The song to play.
     * @param playlistId Optional playlist ID for progress tracking.
     */
    fun play(song: Song, playlistId: Long) {
        if (!audioFocusManager.requestFocus()) {
            Timber.w("Failed to gain audio focus")
            return
        }
        Timber.i("Playing single song: ${song.title}, playlistId=$playlistId")
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
                durationMs = song.durationMs
            )
        }
    }

    /**
     * Play a queue of songs, optionally restoring progress for the starting song.
     *
     * @param songs The list of songs to play.
     * @param startIndex The index in the queue to start from.
     * @param playlistId Optional playlist ID for progress tracking.
     */
    fun playQueue(songs: List<Song>, startIndex: Int, playlistId: Long) {
        if (songs.isEmpty()) {
            Timber.w("playQueue called with empty list")
            return
        }
        if (!audioFocusManager.requestFocus()) {
            Timber.w("Failed to gain audio focus")
            return
        }
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        val startSong = songs[safeIndex]

        Timber.i(
            "Playing queue of ${songs.size} songs, startIndex=$safeIndex, " +
                "playlistId=$playlistId"
        )

        val mediaItems = songs.map { song ->
            PlayItem(uri = song.path, tag = song)
        }

        playerPort.setQueue(items = mediaItems, startIndex = safeIndex, startPositionMs = 0)

        serviceScope.launch {
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
                durationMs = startSong.durationMs
            )
        }
    }

    /** Pause playback. */
    fun pause() {
        Timber.i("Pausing playback")
        wasPausedByFocusLoss = false
        volumeRampPort.restoreVolume()
        playerPort.pause()
        progressTracker.saveProgress()
    }

    /** Resume playback. */
    fun resume() {
        Timber.i("Resuming playback")
        if (!audioFocusManager.requestFocus()) {
            Timber.w("Failed to gain audio focus, cannot resume")
            return
        }
        if (!playerPort.isPlaying()) {
            playerPort.play()
        }
    }

    /** Play the next song in the queue. */
    fun next() {
        Timber.i("Skipping to next")
        if (playerPort.hasNext()) {
            progressTracker.saveProgress()
            playerPort.next()
        } else {
            Timber.d("No next media item")
        }
    }

    /** Play the previous song in the queue. */
    fun previous() {
        Timber.i("Skipping to previous")
        if (playerPort.hasPrevious()) {
            progressTracker.saveProgress()
            playerPort.previous()
        } else {
            Timber.d("No previous media item")
        }
    }

    /**
     * Seek to a specific position in the current song.
     *
     * @param positionMs Position in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        Timber.d("Seeking to $positionMs ms")
        playerPort.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    /** Stop playback and clear state. */
    fun stop() {
        Timber.i("Stopping playback")
        audioFocusManager.abandonFocus()
        volumeRampPort.restoreVolume()
        progressTracker.saveProgress()
        playerPort.stop()
        playerPort.clearQueue()
        _playbackState.update { PlaybackState() }
        progressTracker.stopSaveLoop()
        progressTracker.stopTickLoop()
        alarmFireSession.onPlaybackStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Set the play mode (sequential, random, repeat one). */
    fun setPlayMode(playMode: PlayMode) {
        Timber.i("Setting play mode: $playMode")
        playerPort.setShuffleEnabled(playMode == PlayMode.RANDOM)
        playerPort.setRepeatMode(
            when (playMode) {
                PlayMode.REPEAT_ONE -> PlayerRepeatMode.ONE
                else -> PlayerRepeatMode.OFF
            }
        )
        _playbackState.update { it.copy(playMode = playMode) }
    }

    private fun updateNotification() {
        val notification = notificationManager.buildNotification(_playbackState.value)
        notificationManager.notify(notification)
    }

    override fun onDestroy() {
        Timber.i("MusicPlaybackService destroyed")
        audioFocusManager.abandonFocus()
        volumeRampPort.restoreVolume()
        progressTracker.saveProgress()
        progressTracker.stopSaveLoop()
        progressTracker.stopTickLoop()
        playerEventsJob?.cancel()
        // PlayerPort is a Singleton — its underlying ExoPlayer outlives this service.
        // We do not release it here.
        alarmFireSession.unbindHost(this)
        wakeLockPort.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ----- AlarmPlaybackHost --------------------------------------------------

    /** Pre-warm the player with the first alarm song to reduce alarm-time latency. */
    override fun preloadFirstSong(uri: String) {
        playerPort.setQueue(
            items = listOf(PlayItem(uri = uri)),
            startIndex = 0,
            startPositionMs = 0,
        )
        Timber.d("Preloaded first song: $uri")
    }

    /** Drive playback for the alarm queue. Tags the playback state with [alarmId]. */
    override fun playAlarmQueue(
        songs: List<Song>,
        startIndex: Int,
        playlistId: Long,
        alarmId: Long,
    ) {
        playQueue(songs, startIndex, playlistId)
        _playbackState.update { it.copy(alarmId = alarmId) }
    }

    /** Pause playback driven by the alarm session. */
    override fun pauseAlarm() {
        pause()
    }

    /** Resume playback driven by the alarm session. */
    override fun resumeAlarm() {
        resume()
    }

    /** Used by [AlarmFireSession.scheduleAutoStop] to terminate playback after the timer. */
    override fun stopPlayback() {
        stop()
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1
        private const val PROGRESS_SAVE_INTERVAL_MS = 5000L
        private const val PROGRESS_TICK_INTERVAL_MS = 500L
        private const val ALARM_WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        const val ACTION_PLAY_ALARM = "com.rabbithole.musicbbit.action.PLAY_ALARM"
        const val ACTION_PREVIOUS = "com.rabbithole.musicbbit.action.PREVIOUS"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.rabbithole.musicbbit.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.rabbithole.musicbbit.action.NEXT"
        const val EXTRA_SONGS = "extra_songs"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_IS_ALARM_TRIGGER = "extra_is_alarm_trigger"

        /**
         * Create an [Intent] to start the playback service.
         *
         * @param context The context to use.
         * @return An intent that can be passed to [Context.startForegroundService].
         */
        fun createIntent(context: Context): Intent {
            return Intent(context, MusicPlaybackService::class.java)
        }
    }
}
