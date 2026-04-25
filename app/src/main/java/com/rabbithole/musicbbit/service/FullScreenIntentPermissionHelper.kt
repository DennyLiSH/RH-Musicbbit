package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import timber.log.Timber

/**
 * Helper for the [Manifest.permission.USE_FULL_SCREEN_INTENT] runtime gate
 * introduced in Android 14 (API 34, [Build.VERSION_CODES.UPSIDE_DOWN_CAKE]).
 *
 * On API < 34 the permission is granted at install time and [isGranted] always
 * returns true. On API >= 34 we delegate to [NotificationManager.canUseFullScreenIntent].
 */
object FullScreenIntentPermissionHelper {

    /**
     * Whether the app can launch a full-screen intent right now.
     *
     * @return true on API < 34 unconditionally; on API 34+ defers to
     *         [NotificationManager.canUseFullScreenIntent].
     */
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.canUseFullScreenIntent()
    }

    /**
     * Open the system Settings page where the user can grant
     * USE_FULL_SCREEN_INTENT for this app. No-op on API < 34.
     */
    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Timber.d("openSettings called on API < 34, noop")
            return
        }
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            Timber.i("Launched USE_FULL_SCREEN_INTENT settings")
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch full-screen intent settings")
        }
    }
}
