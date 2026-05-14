package com.rabbithole.musicbbit.service.alarm.ports

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [PermissionPort] adapter backed by Android system services.
 *
 * Singleton-scoped so that the underlying service lookups are cached for the
 * application lifetime.
 */
@Singleton
class AndroidPermissionAdapter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PermissionPort {

    private val powerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private val alarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    override fun isIgnoringBatteryOptimizations(): Boolean {
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    override fun createBatteryOptimizationIntent(): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    override fun isFullScreenIntentGranted(): Boolean {
        return FullScreenIntentPermissionHelper.isGranted(context)
    }

    override fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
