package com.rabbithole.musicbbit.presentation.alarm

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.presentation.alarm.components.AUTO_STOP_OPTIONS
import com.rabbithole.musicbbit.presentation.alarm.components.AutoStopDropdown
import com.rabbithole.musicbbit.presentation.alarm.components.DayOfWeekSelector
import com.rabbithole.musicbbit.presentation.alarm.components.PlaylistSelector
import com.rabbithole.musicbbit.presentation.alarm.components.ResumePlaybackSwitch
import com.rabbithole.musicbbit.presentation.alarm.components.RingModeSelector
import com.rabbithole.musicbbit.presentation.alarm.components.SaveButtonBar
import com.rabbithole.musicbbit.presentation.alarm.components.SectionTitle
import com.rabbithole.musicbbit.presentation.alarm.components.SettingsGroup
import com.rabbithole.musicbbit.presentation.alarm.components.TimeDisplay
import com.rabbithole.musicbbit.presentation.alarm.components.TimePickerDialog
import com.rabbithole.musicbbit.presentation.alarm.components.VolumeRampHint
import com.rabbithole.musicbbit.presentation.alarm.components.labelRes
import com.rabbithole.musicbbit.presentation.alarm.components.toAutoStop
import com.rabbithole.musicbbit.presentation.alarm.components.toOption
import com.rabbithole.musicbbit.presentation.components.rememberAppToast
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import com.rabbithole.musicbbit.ui.theme.timeDisplayStandard
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
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
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
                        showPermissionDialog = false
                    }
                ) {
                    Text(stringResource(R.string.go_to_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showFullScreenIntentDialog) {
        AlertDialog(
            onDismissRequest = { showFullScreenIntentDialog = false },
            title = { Text(stringResource(R.string.fsi_permission_title)) },
            text = { Text(stringResource(R.string.fsi_permission_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        FullScreenIntentPermissionHelper.openSettings(context)
                        showFullScreenIntentDialog = false
                    }
                ) {
                    Text(stringResource(R.string.fsi_permission_grant))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullScreenIntentDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
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
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        hasUnsavedChanges = false
                        navController.navigateUp()
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun AlarmEditContent(
    uiState: AlarmEditUiState,
    onTimeClick: () -> Unit,
    onAction: (AlarmEditAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val form = uiState.form
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        TimeDisplay(
            hour = form.hour,
            minute = form.minute,
            onClick = onTimeClick
        )

        Spacer(modifier = Modifier.height(32.dp))

        SettingsGroup(
            title = stringResource(R.string.alarm_edit_section_basic)
        ) {
            SectionTitle(title = stringResource(R.string.alarm_edit_section_repeat))
            DayOfWeekSelector(
                selectedDays = form.repeatDays,
                excludeHolidays = form.excludeHolidays,
                onDaysChanged = { days ->
                    onAction(AlarmEditAction.OnRepeatDaysChanged(days))
                },
                onExcludeHolidaysChanged = { exclude ->
                    onAction(AlarmEditAction.OnExcludeHolidaysChanged(exclude))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(title = stringResource(R.string.alarm_edit_section_playlist))
            PlaylistSelector(
                playlists = uiState.playlists,
                selectedPlaylistId = form.playlistId,
                onPlaylistSelected = { playlistId ->
                    onAction(AlarmEditAction.OnPlaylistSelected(playlistId))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(title = stringResource(R.string.alarm_edit_section_resume_playback))
            ResumePlaybackSwitch(
                checked = form.resumePlayback,
                onCheckedChange = { resume ->
                    onAction(AlarmEditAction.OnResumePlaybackChanged(resume))
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsGroup(
            title = stringResource(R.string.alarm_edit_section_advanced)
        ) {
            SectionTitle(title = stringResource(R.string.alarm_edit_section_label))
            OutlinedTextField(
                value = form.label,
                onValueChange = { onAction(AlarmEditAction.OnLabelChanged(it)) },
                placeholder = { Text(stringResource(R.string.alarm_edit_label_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(title = stringResource(R.string.alarm_edit_section_ring_mode))
            RingModeSelector(
                selectedMode = form.ringMode,
                onModeChanged = { mode ->
                    onAction(AlarmEditAction.OnRingModeChanged(mode))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(title = stringResource(R.string.alarm_edit_section_auto_stop))
            AutoStopDropdown(
                selectedAutoStop = form.autoStop,
                onSelectionChange = { autoStop ->
                    onAction(AlarmEditAction.OnAutoStopChanged(autoStop))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            VolumeRampHint(
                durationSeconds = uiState.volumeRampDurationSeconds
            )
        }

        if (uiState.errorMessageResId != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(uiState.errorMessageResId),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
