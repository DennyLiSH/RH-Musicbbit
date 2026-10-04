package com.rabbithole.musicbbit.service.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.di.MainDispatcher
import com.rabbithole.musicbbit.service.MainActivityIntentFactory
import com.rabbithole.musicbbit.service.MusicNotificationManager
import com.rabbithole.musicbbit.service.MusicPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the music playback foreground notification lifecycle.
 *
 * Per ADR 0008, this controller is the single home for:
 *   - State collection from [UserPlaybackSession]
 *   - Building the [android.app.Notification] from a [ForegroundNotificationSpec]
 *   - Calling `MusicPlaybackService.startForeground` / `stopForeground` directly
 *
 * Unscoped: each [MusicPlaybackService] instance receives its own controller via
 * field injection. The Service registers itself via [attach] in `onCreate` (after
 * `super.onCreate()`, before any state collection drives a notification) and
 * unregisters via [detach] in `onDestroy`. [detach] cancels the state-collection
 * Job so post-detach state emissions cannot reach a destroyed Service.
 *
 * On any unexpected error in the state collection loop the controller hides the
 * notification, stops the service, and ends this coroutine's collection by
 * throwing [CancellationException]. The original error is logged via Timber
 * before the coroutine is cancelled.
 */
class ForegroundNotificationController @Inject constructor(
    private val playbackSession: UserPlaybackSession,
    private val musicNotificationPort: MusicNotificationPort,
    private val serviceStarter: ServiceStarter,
    private val mainActivityIntentFactory: MainActivityIntentFactory,
    @param:ApplicationContext private val context: Context,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {

    private val controllerJob = SupervisorJob()
    private val controllerScope = CoroutineScope(controllerJob + mainDispatcher)
    private var stateCollectionJob: Job? = null
    private var service: MusicPlaybackService? = null

    fun attach(service: MusicPlaybackService) {
        this.service = service
    }

    fun detach() {
        stateCollectionJob?.cancel()
        stateCollectionJob = null
        service = null
    }

    fun onCreate() {
        Timber.i("ForegroundNotificationController created")
        musicNotificationPort.ensureChannelExists()
        stateCollectionJob = controllerScope.launch {
            playbackSession.playbackState.collect { state ->
                try {
                    if (state.currentSong != null) {
                        val spec = musicNotificationPort.buildSpec(state)
                        val notification = buildNotification(spec)
                        service?.startForeground(NOTIFICATION_ID, notification)
                    } else {
                        service?.stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Timber.e(t, "ForegroundNotificationController state collection failed")
                    service?.stopForeground(STOP_FOREGROUND_REMOVE)
                    serviceStarter.stopService()
                    throw CancellationException("ForegroundNotificationController stop collection after error")
                }
            }
        }
    }

    fun onStartCommand() {
        Timber.i("ForegroundNotificationController onStartCommand")
        // Unconditional: every onStartCommand must satisfy the startForegroundService
        // obligation (AlarmReceiver uses it for cold-process alarm triggers). buildSpec
        // falls back to the app name when the user session has no song — the alarm
        // session's own notification (posted via NotificationManager) is separate.
        val state = playbackSession.playbackState.value
        val spec = musicNotificationPort.buildSpec(state)
        val notification = buildNotification(spec)
        service?.startForeground(NOTIFICATION_ID, notification)
    }

    fun onDestroy() {
        Timber.i("ForegroundNotificationController destroyed")
        service?.stopForeground(STOP_FOREGROUND_REMOVE)
        stateCollectionJob?.cancel()
        controllerJob.cancel()
    }

    /**
     * Build the foreground notification from [spec]. Lives in the controller
     * (not the adapter) per ADR 0008.
     */
    private fun buildNotification(spec: ForegroundNotificationSpec): Notification {
        val contentIntent = mainActivityIntentFactory.create()

        val builder = NotificationCompat.Builder(context, MusicNotificationManager.CHANNEL_ID)
            .setSmallIcon(spec.smallIconResId)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        builder.addAction(
            R.drawable.ic_notification_skip_previous,
            spec.previousLabel,
            createActionPendingIntent(MusicPlaybackService.ACTION_PREVIOUS)
        )
        builder.addAction(
            spec.playPauseIconResId,
            spec.playPauseLabel,
            createActionPendingIntent(MusicPlaybackService.ACTION_TOGGLE_PLAY_PAUSE)
        )
        builder.addAction(
            R.drawable.ic_notification_skip_next,
            spec.nextLabel,
            createActionPendingIntent(MusicPlaybackService.ACTION_NEXT)
        )

        return builder.build()
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, MusicPlaybackService::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val NOTIFICATION_ID = 1
        private const val STOP_FOREGROUND_REMOVE = android.app.Service.STOP_FOREGROUND_REMOVE
    }
}
