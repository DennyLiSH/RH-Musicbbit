package com.rabbithole.musicbbit.service

import android.app.NotificationManager
import android.content.Context
/**
 * Helper for Notification Policy Access ("Do Not Disturb access"), the special-app-access
 * gate behind [android.Manifest.permission.ACCESS_NOTIFICATION_POLICY].
 *
 * Capability boundary: this object intentionally exposes only the read-only operation
 * [isGranted]. Do NOT add notification-policy write APIs (e.g. setInterruptionFilter)
 * here — the permission grants them, but this app only uses the access so its alarm
 * notification channel can bypass DND. Settings-page launching lives in
 * [PermissionStatusMonitor] intent factories + launchSettingsSafely.
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
}
