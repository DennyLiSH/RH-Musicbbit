package com.rabbithole.musicbbit.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the notification channel if running on API 26+ (O). No-op on lower APIs.
 *
 * Security invariant: [importance] is caller-controlled — the factory must not default
 * to IMPORTANCE_HIGH. The caller is responsible for choosing the user-disruption level.
 *
 * Logging invariant: never log channel description or any notification body content.
 */
@Singleton
class NotificationChannelFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resources: NotificationResources
) {
    fun ensureChannel(
        channelId: String,
        @StringRes nameRes: Int,
        nameFallback: String,
        @StringRes descRes: Int,
        descFallback: String,
        importance: Int
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            channelId,
            resources.getString(nameRes, nameFallback),
            importance
        ).apply {
            description = resources.getString(descRes, descFallback)
            setShowBadge(false)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
        Timber.d("Notification channel created: $channelId")
    }
}
