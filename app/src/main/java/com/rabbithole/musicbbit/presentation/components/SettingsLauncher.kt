package com.rabbithole.musicbbit.presentation.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.presentation.permissions.launchSettingsSafely

/**
 * One-line settings launcher: launches the given settings [Intent]; if the launch
 * fails, shows the shared "failed to open settings" toast (R.string.common_settings_open_failed).
 * Replaces the per-screen `if (!launchSettingsSafely(...)) toast` three-line block.
 */
@Composable
fun rememberSettingsLauncher(
    failedMessageResId: Int = R.string.common_settings_open_failed,
): (Intent) -> Unit {
    val context = LocalContext.current
    val toast = rememberAppToast()
    return remember(context) {
        { intent ->
            if (!launchSettingsSafely(context, intent)) {
                toast.showShort(context.getString(failedMessageResId))
            }
        }
    }
}