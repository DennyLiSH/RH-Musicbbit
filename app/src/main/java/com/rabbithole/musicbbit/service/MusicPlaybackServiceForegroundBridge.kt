package com.rabbithole.musicbbit.service

import android.app.Notification
import android.app.Service
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Singleton bridge that forwards [ForegroundServicePort] calls to the
 * currently attached [MusicPlaybackService] instance.
 *
 * This is necessary because [MusicPlaybackService] is created by Android, not
 * Hilt, so it cannot be directly injected into [MusicNotificationManager]. The
 * service registers itself with this bridge in [MusicPlaybackService.onCreate]
 * and unregisters in [MusicPlaybackService.onDestroy].
 */
@Singleton
class MusicPlaybackServiceForegroundBridge @Inject constructor() : ForegroundServicePort {

    private var service: MusicPlaybackService? = null

    fun attach(service: MusicPlaybackService) {
        Timber.i("Attaching MusicPlaybackService to foreground bridge: %s", service)
        this.service = service
    }

    fun detach(service: MusicPlaybackService) {
        if (this.service === service) {
            Timber.i("Detaching MusicPlaybackService from foreground bridge: %s", service)
            this.service = null
        }
    }

    override fun startForeground(notification: Notification) {
        val attached = service
        if (attached == null) {
            Timber.w("startForeground called with no attached service; dropping notification")
            return
        }
        Timber.i("Forwarding startForeground to attached service")
        attached.startForeground(MusicPlaybackService.NOTIFICATION_ID, notification)
    }

    override fun stopForeground() {
        val attached = service
        if (attached == null) {
            Timber.w("stopForeground called with no attached service")
            return
        }
        Timber.i("Forwarding stopForeground to attached service")
        attached.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }
}
