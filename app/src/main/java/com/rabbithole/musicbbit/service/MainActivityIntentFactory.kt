package com.rabbithole.musicbbit.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rabbithole.musicbbit.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the PendingIntent used as notification contentIntent — tapping the notification
 * opens MainActivity in SINGLE_TOP mode.
 *
 * Security invariants:
 *  - FLAG_IMMUTABLE is mandatory (Android 12+). Must not be replaced with FLAG_MUTABLE.
 *  - Intentionally carries no extras — caller passes data via in-process state, not via
 *    the PendingIntent, to prevent Intent substitution by other components.
 */
@Singleton
class MainActivityIntentFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun create(requestCode: Int = 0): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
