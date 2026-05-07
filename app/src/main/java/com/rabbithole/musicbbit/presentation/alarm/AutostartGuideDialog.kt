package com.rabbithole.musicbbit.presentation.alarm

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rabbithole.musicbbit.R

@Composable
fun AutostartGuideDialog(
    isManualGuide: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.autostart_guide_title)) },
        text = {
            Text(
                if (isManualGuide) {
                    stringResource(R.string.autostart_manual_guide_message)
                } else {
                    stringResource(R.string.autostart_guide_message)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(
                    if (isManualGuide) {
                        stringResource(R.string.autostart_manual_guide_open_settings)
                    } else {
                        stringResource(R.string.autostart_guide_open_settings)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.autostart_guide_skip))
            }
        }
    )
}
