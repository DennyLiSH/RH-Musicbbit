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
import com.rabbithole.musicbbit.presentation.alarm.components.DayOfWeekSelector
import com.rabbithole.musicbbit.presentation.alarm.components.PlaylistSelector
import com.rabbithole.musicbbit.presentation.alarm.components.RingModeSelector
import com.rabbithole.musicbbit.presentation.alarm.components.TimePickerDialog
import com.rabbithole.musicbbit.presentation.components.rememberAppToast
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import com.rabbithole.musicbbit.ui.theme.timeDisplayStandard
import timber.log.Timber

private sealed interface AutoStopOption {
    data object None : AutoStopOption
    data class Minutes(val value: Int) : AutoStopOption
    data class Songs(val value: Int) : AutoStopOption
}

private fun AutoStopOption.toAutoStop(): AutoStop? = when (this) {
    is AutoStopOption.Minutes -> AutoStop.ByMinutes(value)
    is AutoStopOption.Songs -> AutoStop.BySongCount(value)
    AutoStopOption.None -> null
}

private fun AutoStop?.toOption(): AutoStopOption = when (this) {
    is AutoStop.ByMinutes -> AutoStopOption.Minutes(minutes)
    is AutoStop.BySongCount -> AutoStopOption.Songs(count)
    null -> AutoStopOption.None
}

@StringRes
private fun AutoStopOption.labelRes(): Int = when (this) {
    AutoStopOption.None -> R.string.alarm_edit_auto_stop_none
    is AutoStopOption.Minutes -> when (value) {
        5 -> R.string.alarm_edit_auto_stop_5min
        10 -> R.string.alarm_edit_auto_stop_10min
        15 -> R.string.alarm_edit_auto_stop_15min
        30 -> R.string.alarm_edit_auto_stop_30min
        60 -> R.string.alarm_edit_auto_stop_60min
        else -> R.string.alarm_edit_auto_stop_none
    }
    is AutoStopOption.Songs -> when (value) {
        1 -> R.string.alarm_edit_auto_stop_1song
        2 -> R.string.alarm_edit_auto_stop_2songs
        3 -> R.string.alarm_edit_auto_stop_3songs
        4 -> R.string.alarm_edit_auto_stop_4songs
        5 -> R.string.alarm_edit_auto_stop_5songs
        10 -> R.string.alarm_edit_auto_stop_10songs
        else -> R.string.alarm_edit_auto_stop_none
    }
}

private val AUTO_STOP_OPTIONS = listOf(
    AutoStopOption.None,
    AutoStopOption.Minutes(5),
    AutoStopOption.Minutes(10),
    AutoStopOption.Minutes(15),
    AutoStopOption.Minutes(30),
    AutoStopOption.Minutes(60),
    AutoStopOption.Songs(1),
    AutoStopOption.Songs(2),
    AutoStopOption.Songs(3),
    AutoStopOption.Songs(4),
    AutoStopOption.Songs(5),
    AutoStopOption.Songs(10),
)

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

@Composable
private fun SaveButtonBar(
    isSaving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.alarm_edit_save_button))
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = title)
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ResumePlaybackSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.alarm_edit_resume_playback_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
private fun TimeDisplay(
    hour: Int,
    minute: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%02d:%02d", hour, minute),
                style = timeDisplayStandard,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.alarm_edit_tap_to_change_time),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoStopDropdown(
    selectedAutoStop: AutoStop?,
    onSelectionChange: (AutoStop?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = selectedAutoStop.toOption()
    val selectedLabelRes = selectedOption.labelRes()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = stringResource(selectedLabelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.alarm_edit_auto_stop_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AUTO_STOP_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        onSelectionChange(option.toAutoStop())
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun VolumeRampHint(
    durationSeconds: Int,
    modifier: Modifier = Modifier
) {
    val text = if (durationSeconds == 0) {
        stringResource(R.string.alarm_edit_volume_ramp_disabled_hint)
    } else {
        stringResource(R.string.alarm_edit_volume_ramp_hint, durationSeconds)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}
