package com.rabbithole.musicbbit.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.rabbithole.musicbbit.service.alarm.AlarmFireSession
import com.rabbithole.musicbbit.di.MainDispatcher
import com.rabbithole.musicbbit.service.playback.ForegroundNotificationController
import com.rabbithole.musicbbit.service.playback.PlaybackSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
 * 2. Foreground notification management via [startForeground] / [stopForeground]
 * 3. Notification button click handling (Previous / PlayPause / Next)
 * 4. Forwarding [ACTION_PLAY_ALARM] intent for [AlarmFireSession]
 *
 * The actual notification state coordination lives in
 * [ForegroundNotificationController]; this class only executes the Android
 * foreground lifecycle calls through [ForegroundServicePort].
 */
@AndroidEntryPoint
class MusicPlaybackService : Service(), ForegroundServicePort {

    @Inject
    lateinit var playbackSession: PlaybackSession

    @Inject
    lateinit var alarmFireSession: AlarmFireSession

    @Inject
    lateinit var foregroundNotificationController: ForegroundNotificationController

    @Inject
    lateinit var foregroundServiceBridge: MusicPlaybackServiceForegroundBridge

    @MainDispatcher
    @Inject
    lateinit var mainDispatcher: CoroutineDispatcher

    private val serviceJob = SupervisorJob()
    private val serviceScope by lazy { CoroutineScope(serviceJob + mainDispatcher) }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = MusicBinder()

    override fun onCreate() {
        super.onCreate()
        Timber.i("MusicPlaybackService created")
        foregroundServiceBridge.attach(this)
        foregroundNotificationController.onCreate()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("MusicPlaybackService started, action=${intent?.action}")

        if (intent == null) {
            // System restarted the service (START_STICKY) after process death.
            // We must still call startForeground to satisfy the 5-second window
            // imposed by ContextCompat.startForegroundService() from the original
            // launch (e.g. AlarmReceiver). Then stop immediately — no active playback.
            foregroundNotificationController.onStartCommand()
            stopSelf()
            return START_NOT_STICKY
        }

        foregroundNotificationController.onStartCommand()

        when (intent.action) {
            ACTION_PLAY_ALARM -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                val isAlarmTrigger = intent.getBooleanExtra(EXTRA_IS_ALARM_TRIGGER, false)
                alarmFireSession.fire(alarmId, isAlarmTrigger)
            }

            ACTION_PREVIOUS -> playbackSession.previous()
            ACTION_TOGGLE_PLAY_PAUSE -> playbackSession.togglePlayPause()

            ACTION_NEXT -> playbackSession.next()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Timber.i("MusicPlaybackService destroyed")
        foregroundNotificationController.onDestroy()
        foregroundServiceBridge.detach(this)
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun startForeground(notification: Notification) {
        Timber.i("MusicPlaybackService entering foreground")
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun stopForeground() {
        Timber.i("MusicPlaybackService leaving foreground")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        internal const val NOTIFICATION_ID = 1

        const val ACTION_PLAY_ALARM = "com.rabbithole.musicbbit.action.PLAY_ALARM"
        const val ACTION_PREVIOUS = "com.rabbithole.musicbbit.action.PREVIOUS"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.rabbithole.musicbbit.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.rabbithole.musicbbit.action.NEXT"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_IS_ALARM_TRIGGER = "extra_alarm_trigger"

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
