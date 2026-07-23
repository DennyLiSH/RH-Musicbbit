package com.rabbithole.musicbbit.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import timber.log.Timber

/**
 * Helper for the [Manifest.permission.SCHEDULE_EXACT_ALARM] runtime gate
 * introduced in Android 12 (API 31, [Build.VERSION_CODES.S]).
 *
 * Symmetric with [FullScreenIntentPermissionHelper] — encapsulates Intent
 * construction and error handling so that Compose dialogs do not need to
 * touch [Context] or [Intent] directly.
 */
object ExactAlarmPermissionHelper {

    /**
     * Open the system Settings page where the user can grant
     * SCHEDULE_EXACT_ALARM for this app.
     *
     * Errors are caught and logged; the caller's `onConfirm` callback
     * contract must still invoke `onDismiss` regardless of success.
     */
    @JvmStatic
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch exact alarm settings")
        }
    }
}
