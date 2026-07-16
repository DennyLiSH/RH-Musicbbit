package com.rabbithole.musicbbit.presentation.alarm.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import timber.log.Timber

@Composable
internal fun PermissionDialog(
    onDismiss: () -> Unit,
    context: Context,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exact_alarm_permission_title)) },
        text = { Text(stringResource(R.string.exact_alarm_permission_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to launch exact alarm settings")
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.go_to_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
internal fun FullScreenIntentDialog(
    onDismiss: () -> Unit,
    context: Context,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fsi_permission_title)) },
        text = { Text(stringResource(R.string.fsi_permission_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    FullScreenIntentPermissionHelper.openSettings(context)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.fsi_permission_grant))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
internal fun DiscardDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discard_changes_title)) },
        text = { Text(stringResource(R.string.discard_changes_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
