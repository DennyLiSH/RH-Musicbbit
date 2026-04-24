package com.rabbithole.musicbbit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.rabbithole.musicbbit.MainActivity
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.usecase.GetPlaybackProgressUseCase
import com.rabbithole.musicbbit.domain.usecase.SavePlaybackProgressUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground music playback service using ExoPlayer.
 *
 * Manages audio playback, persists playback progress, and displays a media-style notification.
 * Playback state is exposed via [StateFlow] for UI observation.
 */
@AndroidEntryPoint
class MusicPlaybackService : Service() {

    @Inject
    lateinit var savePlaybackProgressUseCase: SavePlaybackProgressUseCase

    @Inject
    lateinit var getPlaybackProgressUseCase: GetPlaybackProgressUseCase

    @Inject
    lateinit var alarmVolumeController: AlarmVolumeController

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var audioFocusManager: AudioFocusManager
    private var wasPausedByFocusLoss = false

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var progressSaveJob: Job? = null

    private val autoStopHandler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null
    private var currentAlarmId: Long = -1
    private var extendToEnd: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Timber.d("Player isPlaying changed: $isPlaying")
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            updateNotification()
            if (isPlaying) {
                startProgressSaveLoop()
            } else {
                progressSaveJob?.cancel()
                saveCurrentProgress()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val song = mediaItem?.localConfiguration?.tag as? Song
            Timber.d("Media item transitioned to: ${song?.title}")
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    positionMs = 0,
                    durationMs = song?.durationMs ?: 0,
                    queueIndex = exoPlayer.currentMediaItemIndex
                )
            }
            updateNotification()

            // Check extend-to-end
            if (extendToEnd && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                Timber.i("Extend-to-end: stopping after current song")
                stop()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val duration = if (playbackState == Player.STATE_READY) {
                exoPlayer.duration.coerceAtLeast(0)
            } else {
                _playbackState.value.durationMs
            }
            _playbackState.update { it.copy(durationMs = duration) }
            updateNotification()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _playbackState.update {
                it.copy(
                    positionMs = newPosition.positionMs,
                    queueIndex = exoPlayer.currentMediaItemIndex
                )
            }
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = MusicBinder()

    override fun onCreate() {
        super.onCreate()
        Timber.i("MusicPlaybackService created")
        createNotificationChannel()
        initExoPlayer()
        initAudioFocusManager()
    }

    private fun initExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(playerListener)
        }
    }

    private fun initAudioFocusManager() {
        audioFocusManager = AudioFocusManager(
            context = this,
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
                if (wasPausedByFocusLoss && !exoPlayer.isPlaying && _playbackState.value.currentSong != null) {
                    wasPausedByFocusLoss = false
                    resume()
                }
            }
        )
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("MusicPlaybackService started, action=${intent?.action}")
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_PLAY_ALARM -> handlePlayAlarm(intent)
            AlarmActionReceiver.ACTION_SERVICE_STOP -> stop()
            AlarmActionReceiver.ACTION_SERVICE_PAUSE -> pause()
            AlarmActionReceiver.ACTION_SERVICE_RESUME -> resume()
            AlarmActionReceiver.ACTION_SERVICE_EXTEND_MINUTES -> {
                val minutes = intent.getIntExtra(AlarmActionReceiver.EXTRA_MINUTES, 0)
                if (minutes > 0) extendAutoStop(minutes)
            }
            AlarmActionReceiver.ACTION_SERVICE_EXTEND_TO_END -> setExtendToEnd(true)
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
        val mediaItem = MediaItem.Builder()
            .setUri(song.path)
            .setTag(song)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

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
            MediaItem.Builder()
                .setUri(song.path)
                .setTag(song)
                .build()
        }

        exoPlayer.setMediaItems(mediaItems, safeIndex, 0)
        exoPlayer.prepare()

        serviceScope.launch {
            val progressResult = getPlaybackProgressUseCase(startSong.id, playlistId)
            progressResult.getOrNull()?.let { progress ->
                Timber.i("Restoring progress for song ${startSong.id}: ${progress.positionMs}ms")
                exoPlayer.seekTo(progress.positionMs)
            }
            exoPlayer.play()
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
        alarmVolumeController.restoreVolume()
        exoPlayer.pause()
        saveCurrentProgress()
    }

    /** Resume playback. */
    fun resume() {
        Timber.i("Resuming playback")
        if (!audioFocusManager.requestFocus()) {
            Timber.w("Failed to gain audio focus, cannot resume")
            return
        }
        if (!exoPlayer.isPlaying) {
            exoPlayer.play()
        }
    }

    /** Play the next song in the queue. */
    fun next() {
        Timber.i("Skipping to next")
        if (exoPlayer.hasNextMediaItem()) {
            saveCurrentProgress()
            exoPlayer.seekToNextMediaItem()
        } else {
            Timber.d("No next media item")
        }
    }

    /** Play the previous song in the queue. */
    fun previous() {
        Timber.i("Skipping to previous")
        if (exoPlayer.hasPreviousMediaItem()) {
            saveCurrentProgress()
            exoPlayer.seekToPreviousMediaItem()
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
        exoPlayer.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    /** Stop playback and clear state. */
    fun stop() {
        Timber.i("Stopping playback")
        audioFocusManager.abandonFocus()
        alarmVolumeController.restoreVolume()
        saveCurrentProgress()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _playbackState.update { PlaybackState() }
        progressSaveJob?.cancel()
        cancelAutoStop()
        extendToEnd = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Set the play mode (sequential, random, repeat one). */
    fun setPlayMode(playMode: PlayMode) {
        Timber.i("Setting play mode: $playMode")
        exoPlayer.shuffleModeEnabled = playMode == PlayMode.RANDOM
        exoPlayer.repeatMode = when (playMode) {
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _playbackState.update { it.copy(playMode = playMode) }
    }

    private fun startProgressSaveLoop() {
        progressSaveJob?.cancel()
        progressSaveJob = serviceScope.launch {
            while (true) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                saveCurrentProgress()
            }
        }
    }

    private fun saveCurrentProgress() {
        val state = _playbackState.value
        val song = state.currentSong ?: return
        val position = exoPlayer.currentPosition.coerceAtLeast(0)

        serviceScope.launch {
            val result = savePlaybackProgressUseCase(
                songId = song.id,
                positionMs = position,
                playlistId = state.currentPlaylistId
            )
            result.onSuccess {
                Timber.d("Progress saved: songId=${song.id}, position=$position")
            }.onFailure { error ->
                Timber.e(error, "Failed to save playback progress")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback notification"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Timber.i("Notification channel created")
        }
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, MusicPlaybackService::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(): Notification {
        val state = _playbackState.value
        val song = state.currentSong

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(song?.title ?: getString(R.string.app_name))
            .setContentText(song?.artist ?: "Unknown artist")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Previous
        builder.addAction(
            R.drawable.ic_notification_skip_previous,
            "Previous",
            createActionPendingIntent(ACTION_PREVIOUS)
        )

        // Play/Pause toggle
        val isPlaying = _playbackState.value.isPlaying
        builder.addAction(
            if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
            if (isPlaying) "Pause" else "Play",
            createActionPendingIntent(ACTION_TOGGLE_PLAY_PAUSE)
        )

        // Next
        builder.addAction(
            R.drawable.ic_notification_skip_next,
            "Next",
            createActionPendingIntent(ACTION_NEXT)
        )

        return builder.build()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        Timber.i("MusicPlaybackService destroyed")
        audioFocusManager.abandonFocus()
        alarmVolumeController.restoreVolume()
        cancelAutoStop()
        autoStopHandler.removeCallbacksAndMessages(null)
        saveCurrentProgress()
        progressSaveJob?.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun preloadFirstSong(uri: android.net.Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        Timber.d("Preloaded first song: $uri")
    }

    private fun handlePlayAlarm(intent: Intent) {
        @Suppress("DEPRECATION")
        val songs = intent.getParcelableArrayListExtra<Song>(EXTRA_SONGS) ?: return
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        val playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1)
        val autoStopMinutes = intent.getIntExtra(EXTRA_AUTO_STOP_MINUTES, -1)
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        val isAlarmTrigger = intent.getBooleanExtra(EXTRA_IS_ALARM_TRIGGER, false)

        if (songs.isEmpty() || playlistId == -1L) {
            Timber.w("Invalid alarm play intent")
            return
        }

        // Preload first song if triggered by alarm
        if (isAlarmTrigger && songs.isNotEmpty()) {
            val firstSongUri = android.net.Uri.parse(songs.first().path)
            preloadFirstSong(firstSongUri)
        }

        playQueue(songs, startIndex, playlistId)

        // Start volume ramp for alarm-triggered playback
        if (isAlarmTrigger) {
            alarmVolumeController.startVolumeRamp(serviceScope)
            Timber.i("Started volume ramp for alarm playback")
        }

        // Schedule auto-stop
        if (autoStopMinutes > 0) {
            scheduleAutoStop(autoStopMinutes, alarmId)
        }

        // Reset extend-to-end flag
        extendToEnd = false
    }

    private fun scheduleAutoStop(minutes: Int, alarmId: Long) {
        cancelAutoStop()
        currentAlarmId = alarmId

        val delayMs = minutes * 60_000L
        Timber.i("Scheduling auto-stop in $minutes minutes for alarm $alarmId")

        autoStopRunnable = Runnable {
            Timber.i("Auto-stop triggered for alarm $alarmId")
            stop()
            // Send broadcast to clear alarm notification
            sendBroadcast(Intent(this, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_STOP
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            })
        }
        autoStopHandler.postDelayed(autoStopRunnable!!, delayMs)
    }

    private fun cancelAutoStop() {
        autoStopRunnable?.let {
            autoStopHandler.removeCallbacks(it)
            Timber.d("Auto-stop cancelled")
        }
        autoStopRunnable = null
        currentAlarmId = -1
    }

    /**
     * Extend the auto-stop time by the given minutes.
     * Called from AlarmActionReceiver via broadcast.
     */
    fun extendAutoStop(minutes: Int) {
        autoStopRunnable?.let { currentRunnable ->
            autoStopHandler.removeCallbacks(currentRunnable)
            autoStopHandler.postDelayed(currentRunnable, minutes * 60_000L)
            Timber.i("Auto-stop extended by $minutes minutes")
        }
    }

    /**
     * Set extend-to-end mode. When enabled, playback stops after the current song finishes.
     */
    fun setExtendToEnd(enabled: Boolean) {
        extendToEnd = enabled
        Timber.i("Extend-to-end mode: $enabled")
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1
        private const val PROGRESS_SAVE_INTERVAL_MS = 5000L

        const val ACTION_PLAY_ALARM = "com.rabbithole.musicbbit.action.PLAY_ALARM"
        const val ACTION_PREVIOUS = "com.rabbithole.musicbbit.action.PREVIOUS"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.rabbithole.musicbbit.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.rabbithole.musicbbit.action.NEXT"
        const val EXTRA_SONGS = "extra_songs"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_AUTO_STOP_MINUTES = "extra_auto_stop_minutes"
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
