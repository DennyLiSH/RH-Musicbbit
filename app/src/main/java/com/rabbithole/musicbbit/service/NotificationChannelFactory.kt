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
 *
 * bypassDnd contract:
 *  - [bypassDnd] is applied ONLY at channel creation. Callers wanting a different
 *    bypassDnd value must use a different channel id — recreating a deleted channel
 *    with the same id resurrects it with its previous settings (AOSP behavior), so the
 *    flag can never be flipped for an existing id. The alarm uses two fixed ids:
 *    a normal channel and a bypass channel created only while DND access is granted.
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
        importance: Int,
        bypassDnd: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            channelId,
            resources.getString(nameRes, nameFallback),
            importance
        ).apply {
            description = resources.getString(descRes, descFallback)
            setShowBadge(false)
            setBypassDnd(bypassDnd)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
        Timber.d("Notification channel created: $channelId")
    }
}
