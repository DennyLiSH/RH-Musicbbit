package com.rabbithole.musicbbit.presentation.permissions

import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Launches a settings [Intent] built by [PermissionStatusMonitor]. Failure handling
 * mirrors the former static permission helpers: log and swallow — no crash (the
 * calling banner / dialog dismisses either way).
 */
fun launchSettingsSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Timber.e(e, "Failed to launch settings intent: %s", intent.action)
    }
}
