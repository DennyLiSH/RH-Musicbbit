package com.rabbithole.musicbbit.data.local.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Centralized DataStore preference keys.
 *
 * All preference keys used across the app are defined here to avoid
 * duplication and make key discovery explicit.
 */
object SettingsKeys {
    /** DataStore key for the app's theme mode preference (light / dark / system). */
    val THEME_MODE = stringPreferencesKey("theme_mode")

    /** DataStore key for whether the breathing light effect is enabled during alarm ringing. */
    val BREATHING_ENABLED = booleanPreferencesKey("breathing_enabled")

    /** DataStore key for the breathing light period in milliseconds. */
    val BREATHING_PERIOD_MS = longPreferencesKey("breathing_period_ms")

    /** DataStore key for the last month (YYYY-MM) the holiday API was called. */
    val LAST_HOLIDAY_API_CALL_MONTH = stringPreferencesKey("last_holiday_api_month")
}
