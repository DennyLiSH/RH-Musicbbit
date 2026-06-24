package com.rabbithole.musicbbit

import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting

/**
 * Empty ComponentActivity used as a host for Compose UI tests under Robolectric.
 *
 * Declared in the main AndroidManifest (exported=false) because Robolectric reads
 * the main merged manifest; src/test/AndroidManifest.xml is not merged by AGP for
 * unit tests. Kept in main rather than test to avoid manifest resolution failures.
 */
@VisibleForTesting
class TestActivity : ComponentActivity()
