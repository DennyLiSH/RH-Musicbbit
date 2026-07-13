package com.rabbithole.musicbbit.service.playback

import com.rabbithole.musicbbit.di.MainDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Coordinates the music playback foreground notification.
 *
 * This controller is unscoped: each [com.rabbithole.musicbbit.service.MusicPlaybackService]
 * instance receives its own controller via field injection. It observes
 * [PlaybackSession.playbackState] and drives [MusicNotificationPort] so that the
 * adapter remains the only place that touches Android notifications.
 *
 * On any unexpected error in the state collection loop the controller hides the
 * notification and stops the service, then re-throws the error so it is not
 * silently swallowed.
 */
class ForegroundNotificationController @Inject constructor(
    private val playbackSession: PlaybackSession,
    private val musicNotificationPort: MusicNotificationPort,
    private val serviceStarter: ServiceStarter,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {

    private val controllerJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        Timber.e(t, "ForegroundNotificationController unhandled error")
    }
    private val controllerScope = CoroutineScope(controllerJob + exceptionHandler + mainDispatcher)
    private var stateCollectionJob: Job? = null

    fun onCreate() {
        Timber.i("ForegroundNotificationController created")
        musicNotificationPort.ensureChannelExists()
        stateCollectionJob = controllerScope.launch {
            playbackSession.playbackState.collect { state ->
                try {
                    if (state.currentSong != null) {
                        musicNotificationPort.buildAndNotify(state)
                    } else {
                        musicNotificationPort.hideForegroundNotification()
                    }
                } catch (t: Throwable) {
                    Timber.e(t, "ForegroundNotificationController state collection failed")
                    musicNotificationPort.hideForegroundNotification()
                    serviceStarter.stopService()
                    throw t
                }
            }
        }
    }

    fun onStartCommand() {
        Timber.i("ForegroundNotificationController onStartCommand")
        musicNotificationPort.buildAndNotify(playbackSession.playbackState.value)
    }

    fun onDestroy() {
        Timber.i("ForegroundNotificationController destroyed")
        musicNotificationPort.hideForegroundNotification()
        stateCollectionJob?.cancel()
        controllerJob.cancel()
    }
}
