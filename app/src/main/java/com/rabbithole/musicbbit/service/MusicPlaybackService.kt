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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.rabbithole.musicbbit.MainActivity
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.data.local.dao.AlarmDao
import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.domain.model.PlaybackProgress
import com.rabbithole.musicbbit.domain.model.Song
import com.rabbithole.musicbbit.domain.repository.PlaybackProgressRepository
import com.rabbithole.musicbbit.domain.repository.PlaylistRepository
import com.rabbithole.musicbbit.domain.usecase.GetPlaybackProgressUseCase
import com.rabbithole.musicbbit.domain.usecase.SavePlaybackProgressUseCase
import com.rabbithole.musicbbit.service.playback.PlayItem
import com.rabbithole.musicbbit.service.playback.PlayerEvent
import com.rabbithole.musicbbit.service.playback.PlayerPort
import com.rabbithole.musicbbit.service.playback.PlayerRepeatMode
import com.rabbithole.musicbbit.service.playback.TransitionReason
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Foreground music playback service.
 *
 * Manages audio playback through [PlayerPort] (production adapter wraps ExoPlayer),
 * persists playback progress, and displays a media-style notification.
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

    @Inject
    lateinit var alarmDao: AlarmDao

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    @Inject
    lateinit var playbackProgressRepository: PlaybackProgressRepository

    @Inject
    lateinit var playerPort: PlayerPort

    private lateinit var audioFocusManager: AudioFocusManager
    private var wasPausedByFocusLoss = false

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var progressSaveJob: Job? = null
    private var progressTickJob: Job? = null
    private var playerEventsJob: Job? = null

    private val autoStopHandler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null
    private var currentAlarmId: Long = -1
    private var extendToEnd: Boolean = false
    private var alarmWakeLock: PowerManager.WakeLock? = null

    private fun handleIsPlayingChanged(isPlaying: Boolean) {
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

        // Check extend-to-end
        if (extendToEnd && event.reason == TransitionReason.AUTO) {
            Timber.i("Extend-to-end: stopping after current song")
            stop()
        }
    }

    private fun handlePlaybackReady(durationMs: Long) {
        _playbackState.update { it.copy(durationMs = durationMs) }
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

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = MusicBinder()

    override fun onCreate() {
        super.onCreate()
        Timber.i("MusicPlaybackService created")
        createNotificationChannel()
        observePlayerEvents()
        initAudioFocusManager()
        startProgressTickLoop()
    }

    private fun observePlayerEvents() {
        playerEventsJob?.cancel()
        playerEventsJob = serviceScope.launch {
            playerPort.events.collect { event ->
                when (event) {
                    is PlayerEvent.IsPlayingChanged -> handleIsPlayingChanged(event.isPlaying)
                    is PlayerEvent.MediaItemTransition -> handleMediaItemTransition(event)
                    is PlayerEvent.PlaybackReady -> handlePlaybackReady(event.durationMs)
                    is PlayerEvent.PositionDiscontinuity -> handlePositionDiscontinuity(event)
                }
            }
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
                if (wasPausedByFocusLoss && !playerPort.isPlaying() && _playbackState.value.currentSong != null) {
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
     * Acquire a partial wake lock for alarm playback.
     * The lock is released when playback stops.
     */
    private fun acquireAlarmWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            alarmWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RH-Musicbbit::AlarmPlaybackWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(ALARM_WAKE_LOCK_TIMEOUT_MS)
            }
            Timber.d("Alarm wake lock acquired")
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire alarm wake lock")
        }
    }

    /**
     * Release the alarm wake lock if held.
     */
    private fun releaseAlarmWakeLock() {
        alarmWakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.d("Alarm wake lock released")
            }
        }
        alarmWakeLock = null
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
            val progressResult = getPlaybackProgressUseCase(startSong.id, playlistId)
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
        alarmVolumeController.restoreVolume()
        playerPort.pause()
        saveCurrentProgress()
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
            saveCurrentProgress()
            playerPort.next()
        } else {
            Timber.d("No next media item")
        }
    }

    /** Play the previous song in the queue. */
    fun previous() {
        Timber.i("Skipping to previous")
        if (playerPort.hasPrevious()) {
            saveCurrentProgress()
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
        alarmVolumeController.restoreVolume()
        saveCurrentProgress()
        playerPort.stop()
        playerPort.clearQueue()
        _playbackState.update { PlaybackState() }
        progressSaveJob?.cancel()
        cancelProgressTickLoop()
        cancelAutoStop()
        extendToEnd = false
        releaseAlarmWakeLock()
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

    private fun startProgressSaveLoop() {
        progressSaveJob?.cancel()
        progressSaveJob = serviceScope.launch {
            while (true) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                saveCurrentProgress()
            }
        }
    }

    /**
     * Start a periodic loop that pulls [PlayerPort.currentPositionMs] into [_playbackState]
     * every [PROGRESS_TICK_INTERVAL_MS] milliseconds while playback is active.
     *
     * The loop runs unconditionally once started; it short-circuits internally when
     * [_playbackState.value.isPlaying] is false to avoid races with seek/transition
     * events that already update positionMs through the player listener.
     */
    private fun startProgressTickLoop() {
        progressTickJob?.cancel()
        progressTickJob = serviceScope.launch {
            while (isActive) {
                delay(PROGRESS_TICK_INTERVAL_MS)
                if (_playbackState.value.isPlaying) {
                    val currentPos = playerPort.currentPositionMs()
                    _playbackState.update { it.copy(positionMs = currentPos) }
                }
            }
        }
    }

    private fun cancelProgressTickLoop() {
        progressTickJob?.cancel()
        progressTickJob = null
    }

    private fun saveCurrentProgress() {
        val state = _playbackState.value
        val song = state.currentSong ?: return
        val position = playerPort.currentPositionMs()

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
        cancelProgressTickLoop()
        playerEventsJob?.cancel()
        // PlayerPort is a Singleton — its underlying ExoPlayer outlives this service.
        // We do not release it here.
        releaseAlarmWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun preloadFirstSong(uri: android.net.Uri) {
        playerPort.setQueue(
            items = listOf(PlayItem(uri = uri.toString())),
            startIndex = 0,
            startPositionMs = 0,
        )
        Timber.d("Preloaded first song: $uri")
    }

    private fun handlePlayAlarm(intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        val isAlarmTrigger = intent.getBooleanExtra(EXTRA_IS_ALARM_TRIGGER, false)

        if (alarmId == -1L) {
            Timber.w("Invalid alarm play intent: no alarmId")
            return
        }

        // Acquire wake lock for alarm-triggered playback
        if (isAlarmTrigger) {
            acquireAlarmWakeLock()
        }

        serviceScope.launch(Dispatchers.IO) {
            val alarm = alarmDao.getById(alarmId)
            if (alarm == null || !alarm.isEnabled) {
                Timber.w("Alarm id=$alarmId not found or disabled")
                return@launch
            }

            val playlistWithSongs = playlistRepository.getPlaylistWithSongs(alarm.playlistId).first()
            if (playlistWithSongs == null || playlistWithSongs.songs.isEmpty()) {
                Timber.w("Playlist id=${alarm.playlistId} is empty or not found for alarm id=$alarmId")
                AlarmNotificationHelper.showErrorNotification(
                    this@MusicPlaybackService,
                    alarmId.toInt(),
                    alarm.label ?: "Music Alarm",
                    "Playlist is empty"
                )
                return@launch
            }

            val songs = playlistWithSongs.songs
            val startIndex = resolveStartIndex(songs, alarm)
            val startSong = songs[startIndex]

            // Reset playback progress for the starting song to beginning
            resetPlaybackProgress(startSong, alarm)

            withContext(Dispatchers.Main) {
                // Preload first song if triggered by alarm
                if (isAlarmTrigger && songs.isNotEmpty()) {
                    val firstSongUri = android.net.Uri.parse(songs.first().path)
                    preloadFirstSong(firstSongUri)
                }

                playQueue(songs, startIndex, alarm.playlistId)
                _playbackState.update { it.copy(alarmId = alarmId) }

                // Start volume ramp for alarm-triggered playback
                if (isAlarmTrigger) {
                    alarmVolumeController.startVolumeRamp(serviceScope)
                    Timber.i("Started volume ramp for alarm playback")
                }

                // Schedule auto-stop
                val autoStopMinutes = alarm.autoStopMinutes
                if (autoStopMinutes != null && autoStopMinutes > 0) {
                    scheduleAutoStop(autoStopMinutes, alarmId)
                }

                // Reset extend-to-end flag
                extendToEnd = false

                // Show alarm notification
                AlarmNotificationHelper.show(this@MusicPlaybackService, alarm, startSong)
                Timber.i("Alarm notification shown for alarm id=$alarmId, song=${startSong.title}")
            }
        }
    }

    /**
     * Resolve the starting song index based on saved playback progress.
     */
    private suspend fun resolveStartIndex(songs: List<Song>, alarm: AlarmEntity): Int {
        val progressList = playbackProgressRepository.getProgressForPlaylist(alarm.playlistId).getOrNull()
        return if (!progressList.isNullOrEmpty()) {
            val latestProgress = progressList.maxByOrNull { it.updatedAt }
            val index = latestProgress?.let { progress ->
                songs.indexOfFirst { it.id == progress.songId }
            } ?: 0
            index.coerceIn(0, songs.lastIndex)
        } else {
            0
        }
    }

    /**
     * Reset playback progress for the starting song to the beginning.
     */
    private suspend fun resetPlaybackProgress(startSong: Song, alarm: AlarmEntity) {
        val progress = PlaybackProgress(
            songId = startSong.id,
            positionMs = 0,
            updatedAt = System.currentTimeMillis(),
            playlistId = alarm.playlistId
        )
        playbackProgressRepository.saveProgress(progress).onSuccess {
            Timber.d("Reset progress for song id=${startSong.id}")
        }.onFailure { error ->
            Timber.e(error, "Failed to reset progress for song id=${startSong.id}")
        }
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
        private const val PROGRESS_TICK_INTERVAL_MS = 500L
        private const val ALARM_WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes

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
