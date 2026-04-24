package com.rabbithole.musicbbit.data.local.datastore

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
}
