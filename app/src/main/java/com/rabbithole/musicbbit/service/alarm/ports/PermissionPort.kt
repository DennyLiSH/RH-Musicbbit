package com.rabbithole.musicbbit.service.alarm.ports

import android.content.Intent

/**
 * Abstraction over Android permission and system-setting checks.
 *
 * Production adapter: [AndroidPermissionAdapter] — wraps PowerManager, AlarmManager,
 * NotificationManager, and ContextCompat.
 * Test adapter: a fake that returns canned permission states.
 *
 * All methods are side-effect-free queries except [createBatteryOptimizationIntent],
 * which constructs an [Intent] for the UI to launch.
 */
interface PermissionPort {

    /**
     * Whether the app is currently exempt from battery optimizations (Doze / App Standby).
     */
    fun isIgnoringBatteryOptimizations(): Boolean

    /**
     * Build an [Intent] that opens the system "ignore battery optimizations" dialog
     * for this app. The caller (UI layer) is responsible for launching it.
     */
    fun createBatteryOptimizationIntent(): Intent

    /**
     * Whether the app can use full-screen intents (API 34+ runtime gate via
     * NotificationManager, always true on older versions).
     */
    fun isFullScreenIntentGranted(): Boolean

    /**
     * Whether the given Android runtime permission string is currently granted.
     *
     * @param permission An Android permission constant, e.g. [android.Manifest.permission.POST_NOTIFICATIONS].
     */
    fun checkPermission(permission: String): Boolean

    /**
     * Whether the app can schedule exact alarms (API 31+). Always true on older versions.
     */
    fun canScheduleExactAlarms(): Boolean
}
