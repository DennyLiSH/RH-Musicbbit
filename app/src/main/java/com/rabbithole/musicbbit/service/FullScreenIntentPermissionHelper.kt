package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Helper for the [Manifest.permission.USE_FULL_SCREEN_INTENT] runtime gate
 * introduced in Android 14 (API 34, [Build.VERSION_CODES.UPSIDE_DOWN_CAKE]).
 *
 * On API < 34 the permission is granted at install time and [isGranted] always
 * returns true. On API >= 34 we delegate to [NotificationManager.canUseFullScreenIntent].
 *
 * The settings-page intent for this permission is owned by
 * [com.rabbithole.musicbbit.service.alarm.ports.AndroidPermissionAdapter]
 * (single construction point); this object only answers the query.
 */
object FullScreenIntentPermissionHelper {

    /**
     * Whether the app can launch a full-screen intent right now.
     *
     * @return true on API < 34 unconditionally; on API 34+ defers to
     *         [NotificationManager.canUseFullScreenIntent].
     */
    @JvmStatic
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.canUseFullScreenIntent()
    }
}
