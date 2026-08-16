package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import timber.log.Timber

/**
 * Helper for Notification Policy Access ("Do Not Disturb access"), the special-app-access
 * gate behind [android.Manifest.permission.ACCESS_NOTIFICATION_POLICY].
 *
 * Capability boundary: this object intentionally exposes only the two read-only
 * operations [isGranted] and [openSettings]. Do NOT add notification-policy write APIs
 * (e.g. setInterruptionFilter) here — the permission grants them, but this app only uses
 * the access so its alarm notification channel can bypass DND.
 *
 * The manifest permission is an appop-level special permission: declaring it is only a
 * prerequisite; the user must grant access manually in system settings.
 */
object DndAccessPermissionHelper {

    /**
     * Whether the user has granted this app Do Not Disturb access.
     *
     * Available since API 23 (M); minSdk 24 means no version branch is needed.
     */
    @JvmStatic
    fun isGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.isNotificationPolicyAccessGranted()
    }

    /**
     * Open the system Settings page where the user can grant Do Not Disturb access.
     *
     * Failure handling mirrors [FullScreenIntentPermissionHelper]: log and swallow —
     * no crash, no rethrow (the calling dialog dismisses either way).
     */
    @JvmStatic
    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Timber.d("openSettings called on API < 23, noop")
            return
        }
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            Timber.i("Launched notification policy access settings")
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch notification policy access settings")
        }
    }
}
