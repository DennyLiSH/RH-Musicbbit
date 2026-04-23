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
import android.os.IBinder
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

    private lateinit var exoPlayer: ExoPlayer

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var progressSaveJob: Job? = null

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
    }

    private fun initExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(playerListener)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("MusicPlaybackService started")
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    /**
     * Play a single song.
     *
     * @param song The song to play.
     * @param playlistId Optional playlist ID for progress tracking.
     */
    fun play(song: Song, playlistId: Long?) {
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
    fun playQueue(songs: List<Song>, startIndex: Int, playlistId: Long?) {
        if (songs.isEmpty()) {
            Timber.w("playQueue called with empty list")
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
        exoPlayer.pause()
        saveCurrentProgress()
    }

    /** Resume playback. */
    fun resume() {
        Timber.i("Resuming playback")
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
        saveCurrentProgress()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _playbackState.update { PlaybackState() }
        progressSaveJob?.cancel()
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(song?.title ?: getString(R.string.app_name))
            .setContentText(song?.artist ?: "Unknown artist")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

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
        saveCurrentProgress()
        progressSaveJob?.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1
        private const val PROGRESS_SAVE_INTERVAL_MS = 5000L

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
