package com.rabbithole.musicbbit

import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.AndroidEntryPoint

/**
 * @AndroidEntryPoint variant of [TestActivity] for Compose UI tests whose tree
 * calls hiltViewModel() — e.g. the AppNavigation root composition. Kept separate
 * so plain [TestActivity] hosts stay Hilt-free and work without HiltAndroidRule.
 *
 * Same manifest rationale as [TestActivity]: main manifest, exported=false,
 * never launched in production.
 */
@VisibleForTesting
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
