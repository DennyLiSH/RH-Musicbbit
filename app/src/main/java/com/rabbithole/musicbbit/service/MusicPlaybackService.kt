package com.rabbithole.musicbbit.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.rabbithole.musicbbit.service.alarm.AlarmFireSession
import com.rabbithole.musicbbit.service.alarm.ports.WakeLockPort
import com.rabbithole.musicbbit.service.playback.MusicNotificationPort
import com.rabbithole.musicbbit.service.playback.PlaybackSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground music playback service — thin shell.
 *
 * Responsibilities:
 * 1. Android Service lifecycle (onCreate/onBind/onStartCommand/onDestroy)
 * 2. Foreground notification management via [startForeground]
 * 3. Notification button click handling (Previous / PlayPause / Next)
 * 4. Forwarding [ACTION_PLAY_ALARM] intent for [AlarmFireSession]
 */
@AndroidEntryPoint
class MusicPlaybackService : Service() {

    @Inject
    lateinit var playbackSession: PlaybackSession

    @Inject
    lateinit var musicNotificationPort: MusicNotificationPort

    @Inject
    lateinit var wakeLockPort: WakeLockPort

    @Inject
    lateinit var alarmFireSession: AlarmFireSession

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)
    private var stateJob: Job? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = MusicBinder()

    override fun onCreate() {
        super.onCreate()
        Timber.i("MusicPlaybackService created")
        musicNotificationPort.createChannel()
        observePlaybackState()
    }

    private fun observePlaybackState() {
        stateJob = serviceScope.launch {
            playbackSession.playbackState.collect { state ->
                val notification = musicNotificationPort.buildNotification(state)
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("MusicPlaybackService started, action=${intent?.action}")

        val state = playbackSession.playbackState.value
        val notification = musicNotificationPort.buildNotification(state)
        startForeground(NOTIFICATION_ID, notification)

        when (intent?.action) {
            ACTION_PLAY_ALARM -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                val isAlarmTrigger = intent.getBooleanExtra(EXTRA_IS_ALARM_TRIGGER, false)
                if (isAlarmTrigger) {
                    wakeLockPort.acquire(ALARM_WAKE_LOCK_TIMEOUT_MS)
                }
                alarmFireSession.fire(alarmId, isAlarmTrigger)
            }

            ACTION_PREVIOUS -> playbackSession.previous()
            ACTION_TOGGLE_PLAY_PAUSE -> {
                if (playbackSession.playbackState.value.isPlaying) {
                    playbackSession.pause()
                } else {
                    playbackSession.resume()
                }
            }

            ACTION_NEXT -> playbackSession.next()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Timber.i("MusicPlaybackService destroyed")
        stateJob?.cancel()
        wakeLockPort.release()
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ALARM_WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        const val ACTION_PLAY_ALARM = "com.rabbithole.musicbbit.action.PLAY_ALARM"
        const val ACTION_PREVIOUS = "com.rabbithole.musicbbit.action.PREVIOUS"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.rabbithole.musicbbit.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.rabbithole.musicbbit.action.NEXT"
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
