package com.rabbithole.musicbbit.service

import android.app.Notification

/**
 * Narrow Android seam for foreground service operations.
 *
 * The real implementation lives in [MusicPlaybackService], but because Services
 * are created by Android rather than Hilt, the singleton
 * [MusicPlaybackServiceForegroundBridge] forwards calls to the currently
 * attached Service instance.
 *
 * This interface is intentionally `internal` so it cannot be injected outside
 * the `service` package.
 */
internal interface ForegroundServicePort {
    fun startForeground(notification: Notification)
    fun stopForeground()
}
