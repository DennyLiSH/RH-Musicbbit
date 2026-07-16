package com.rabbithole.musicbbit.presentation.alarm

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.presentation.alarm.components.AlarmEditContent
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
    var showTimePicker by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showFullScreenIntentDialog by remember { mutableStateOf(false) }
    var showAutostartGuideDialog by remember { mutableStateOf(false) }
    var showAutostartManualGuideDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var autostartIntent by remember { mutableStateOf<Intent?>(null) }
    val context = LocalContext.current
    val toast = rememberAppToast()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessageResId = uiState.saveFailedMessageResId
    val saveFailedMessage = saveFailedMessageResId?.let { stringResource(it) }
    val alarmSavedMessage = stringResource(R.string.alarm_saved)

    // Track form edits for BackHandler gating. OnSave does not count — once
    // save completes the screen navigates up anyway.
    val onActionWithTracking: (AlarmEditAction) -> Unit = { action ->
        if (action !is AlarmEditAction.OnSave) {
            hasUnsavedChanges = true
        }
        viewModel.onAction(action)
    }

    // Navigate up when save is completed; briefly toast the success message
    // (Toast used instead of Snackbar because Snackbar is destroyed on
    // navigateUp; Toast survives the screen change).
    LaunchedEffect(uiState.saveCompleted) {
        if (uiState.saveCompleted) {
            Timber.i("Alarm saved, navigating up")
            toast.showShort(alarmSavedMessage)
            navController.navigateUp()
        }
    }

    // Show Snackbar on save failure, then clear the trigger so it doesn't
    // re-show on configuration change.
    LaunchedEffect(saveFailedMessageResId) {
        saveFailedMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg)
            viewModel.clearSaveFailedMessage()
        }
    }

    // Clear inline form error as soon as user edits the playlist field
    // (only after a prior submit attempt set the error — initial empty state
    // is not an error).
    LaunchedEffect(form.playlistId) {
        if (uiState.errorMessageResId != null) {
            viewModel.clearError()
        }
    }

    // Intercept system back / navigation arrow only when user has unsaved
    // edits. If OEM ROM predictive-back gesture conflicts, system gesture
    // wins (BackHandler is bypassed when disabled).
    BackHandler(enabled = hasUnsavedChanges) {
        showDiscardDialog = true
    }

    // Collect one-time events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AlarmEditEvent.ShowPermissionDialog -> showPermissionDialog = true
                is AlarmEditEvent.ShowFullScreenIntentDialog -> showFullScreenIntentDialog = true
                is AlarmEditEvent.ShowAutostartGuideDialog -> {
                    autostartIntent = event.intent
                    showAutostartGuideDialog = true
                }
                is AlarmEditEvent.ShowAutostartManualGuideDialog -> showAutostartManualGuideDialog = true
            }
        }
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
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
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
                actions = {}
            )
        },
        bottomBar = {
            SaveButtonBar(
                isSaving = uiState.isSaving,
                onSave = { onActionWithTracking(AlarmEditAction.OnSave) }
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
                    onTimeClick = { showTimePicker = true },
                    onAction = onActionWithTracking
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = form.hour,
            initialMinute = form.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.onAction(AlarmEditAction.OnTimeChanged(hour, minute))
                showTimePicker = false
            }
        )
    }

    if (showPermissionDialog) {
        PermissionDialog(
            onDismiss = { showPermissionDialog = false },
            context = context,
        )
    }

    if (showFullScreenIntentDialog) {
        FullScreenIntentDialog(
            onDismiss = { showFullScreenIntentDialog = false },
            context = context,
        )
    }

    if (showAutostartGuideDialog) {
        AutostartGuideDialog(
            isManualGuide = false,
            onDismiss = {
                showAutostartGuideDialog = false
                viewModel.onAutostartGuideDismissed()
            },
            onOpenSettings = {
                autostartIntent?.let {
                    try {
                        context.startActivity(it)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to launch OEM autostart settings")
                    }
                }
                showAutostartGuideDialog = false
                viewModel.onAutostartGuideDismissed()
            }
        )
    }

    if (showAutostartManualGuideDialog) {
        AutostartGuideDialog(
            isManualGuide = true,
            onDismiss = {
                showAutostartManualGuideDialog = false
                viewModel.onAutostartGuideDismissed()
            },
            onOpenSettings = {
                try {
                    context.startActivity(AutostartHelper.getManualGuideSettingsIntent())
                } catch (e: Exception) {
                    Timber.e(e, "Failed to launch manual guide settings")
                }
                showAutostartManualGuideDialog = false
                viewModel.onAutostartGuideDismissed()
            }
        )
    }

    if (showDiscardDialog) {
        DiscardDialog(
            onDismiss = { showDiscardDialog = false },
            onConfirm = {
                showDiscardDialog = false
                hasUnsavedChanges = false
                navController.navigateUp()
            }
        )
    }
}
