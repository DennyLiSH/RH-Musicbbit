package com.rabbithole.musicbbit.presentation.alarm

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.navigation.PlaylistList

import com.rabbithole.musicbbit.service.ExactAlarmPermissionHelper
import com.rabbithole.musicbbit.presentation.permissions.launchSettingsSafely
import com.rabbithole.musicbbit.presentation.alarm.components.AlarmEditContent
import com.rabbithole.musicbbit.presentation.alarm.components.AutostartGuideDialog
import com.rabbithole.musicbbit.presentation.alarm.components.DeleteConfirmDialog
import com.rabbithole.musicbbit.presentation.alarm.components.DiscardDialog
import com.rabbithole.musicbbit.presentation.alarm.components.FullScreenIntentDialog
import com.rabbithole.musicbbit.presentation.alarm.components.PermissionDialog
import com.rabbithole.musicbbit.presentation.alarm.components.SaveButtonBar
import com.rabbithole.musicbbit.presentation.alarm.components.TimePickerDialog
import com.rabbithole.musicbbit.presentation.components.rememberAppToast
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    navController: NavController,
    viewModel: AlarmEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val form = uiState.form
    val context = LocalContext.current
    val toast = rememberAppToast()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessageResId = uiState.saveFailedMessageResId
    val saveFailedMessage = saveFailedMessageResId?.let { stringResource(it) }
    val errorMessageResId = uiState.errorMessageResId
    val errorMessage = errorMessageResId?.let { stringResource(it) }
    val alarmSavedMessage = stringResource(R.string.alarm_saved)
    val alarmDeletedMessage = stringResource(R.string.alarm_deleted)

    // Navigate up when save is completed; briefly toast the success message.
    // (Toast instead of Snackbar because Snackbar is destroyed on navigateUp.)
    LaunchedEffect(uiState.saveCompleted) {
        if (uiState.saveCompleted) {
            Timber.i("Alarm saved, navigating up")
            toast.showShort(alarmSavedMessage)
            navController.navigateUp()
        }
    }

    LaunchedEffect(uiState.deleteCompleted) {
        if (uiState.deleteCompleted) {
            Timber.i("Alarm deleted, navigating up")
            toast.showShort(alarmDeletedMessage)
            navController.navigateUp()
        }
    }

    // Show Snackbar on save failure, then clear the trigger so it doesn't re-show
    // on configuration change. errorMessageResId and saveFailedMessageResId share
    // the same lifecycle and are both cleared via clearError().
    LaunchedEffect(saveFailedMessageResId) {
        saveFailedMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg)
            viewModel.clearError()
        }
    }

    // Validation and page-level errors surface via Snackbar: the inline error used to
    // live at the bottom of the scrollable form where it was invisible after tapping
    // Save from anywhere on the page. Unlike saveFailedMessageResId, errorMessageResId
    // is NOT cleared here — it also drives the isError highlight on the playlist field,
    // which must persist until the user edits the form (any action clears it in the VM).
    LaunchedEffect(errorMessageResId) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg)
        }
    }

    // Clear inline form error as soon as user edits the playlist field
    // (only after a prior submit attempt set the error). The Snackbar above clears
    // errorMessageResId immediately after display; isError on the field persists via
    // this same state until the user changes the selection.
    LaunchedEffect(form.playlistId) {
        if (uiState.errorMessageResId != null) {
            viewModel.clearError()
        }
    }

    // Re-check DND access when returning from system settings, so the inline
    // permission hint next to the ignore-quiet-mode switch clears once granted.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDndAccessStatus()
    }

    // Intercept system back / navigation arrow only when user has unsaved edits.
    BackHandler(enabled = uiState.hasUnsavedChanges) {
        viewModel.showDiscardDialog()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val titleTextRes = when {
                        uiState.isLoading -> R.string.alarm_edit_title_loading
                        uiState.isNewAlarm -> R.string.alarm_edit_title_new
                        else -> R.string.alarm_edit_title_edit
                    }
                    Text(stringResource(titleTextRes))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.hasUnsavedChanges) {
                            viewModel.showDiscardDialog()
                        } else {
                            navController.navigateUp()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (!uiState.isNewAlarm && !uiState.isLoading) {
                        IconButton(onClick = { viewModel.showDeleteDialog() }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.alarm_list_delete)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            SaveButtonBar(
                isSaving = uiState.isSaving,
                onSave = { viewModel.onAction(AlarmEditAction.OnSave) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                AlarmEditContent(
                    uiState = uiState,
                    onTimeClick = { viewModel.showTimePicker() },
                    onAction = viewModel::onAction,
                    onCreatePlaylist = { navController.navigate(PlaylistList) },
                    onRequestDndAccess = {
                    launchSettingsSafely(context, viewModel.createDndAccessSettingsIntent())
                }
                )
            }
        }
    }

    // Single dialog state — mutually exclusive by construction.
    when (val d = uiState.dialogState) {
        null -> {}
        AlarmEditDialogState.TimePicker -> TimePickerDialog(
            initialHour = form.hour,
            initialMinute = form.minute,
            onDismiss = { viewModel.dismissDialog() },
            onConfirm = { hour, minute ->
                viewModel.onAction(AlarmEditAction.OnTimeChanged(hour, minute))
                viewModel.dismissDialog()
            }
        )
        AlarmEditDialogState.Permission -> PermissionDialog(
            onConfirm = {
                ExactAlarmPermissionHelper.openSettings(context)
                viewModel.dismissDialog()
            },
            onDismiss = { viewModel.dismissDialog() },
        )
        AlarmEditDialogState.FullScreenIntent -> FullScreenIntentDialog(
            onConfirm = {
                launchSettingsSafely(context, viewModel.createFullScreenIntentSettingsIntent())
                viewModel.dismissDialog()
            },
            onDismiss = { viewModel.dismissDialog() },
        )
        AlarmEditDialogState.AutostartManualGuide -> AutostartGuideDialog(
            isManualGuide = true,
            onDismiss = {
                viewModel.dismissDialog()
                viewModel.onAutostartGuideDismissed()
            },
            onOpenSettings = {
                try {
                    context.startActivity(AutostartHelper.getManualGuideSettingsIntent())
                } catch (e: Exception) {
                    Timber.e(e, "Failed to launch manual guide settings")
                }
                viewModel.dismissDialog()
                viewModel.onAutostartGuideDismissed()
            }
        )
        is AlarmEditDialogState.AutostartGuide -> {
            val guideIntent: Intent? = d.intent
            AutostartGuideDialog(
                isManualGuide = false,
                onDismiss = {
                    viewModel.dismissDialog()
                    viewModel.onAutostartGuideDismissed()
                },
                onOpenSettings = {
                    guideIntent?.let {
                        try {
                            context.startActivity(it)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to launch OEM autostart settings")
                        }
                    }
                    viewModel.dismissDialog()
                    viewModel.onAutostartGuideDismissed()
                }
            )
        }
        AlarmEditDialogState.Discard -> DiscardDialog(
            onDismiss = { viewModel.dismissDialog() },
            onConfirm = {
                viewModel.dismissDialog()
                navController.navigateUp()
            }
        )
        AlarmEditDialogState.DeleteConfirm -> DeleteConfirmDialog(
            onDismiss = { viewModel.dismissDialog() },
            onConfirm = {
                viewModel.dismissDialog()
                viewModel.deleteAlarm()
            }
        )
    }
}
