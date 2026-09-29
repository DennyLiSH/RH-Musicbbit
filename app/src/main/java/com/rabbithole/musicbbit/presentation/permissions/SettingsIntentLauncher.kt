package com.rabbithole.musicbbit.presentation.permissions

import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Launches a settings [Intent] built by [PermissionStatusMonitor].
 *
 * Returns true when [Context.startActivity] completed without throwing, false when the
 * launch failed (exception logged, never rethrown). Callers should surface the failure
 * to the user, e.g. via AppToast.
 */
fun launchSettingsSafely(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Timber.e(e, "Failed to launch settings intent: %s", intent.action)
        false
    }
}
