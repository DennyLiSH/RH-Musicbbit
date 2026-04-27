package com.rabbithole.musicbbit.presentation.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.navigation.NavController
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.service.FullScreenIntentPermissionHelper
import com.rabbithole.musicbbit.presentation.alarm.components.DayOfWeekSelector
import com.rabbithole.musicbbit.presentation.alarm.components.PlaylistSelector
import com.rabbithole.musicbbit.presentation.alarm.components.TimePickerDialog
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
    var showTimePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Navigate up when save is completed
    LaunchedEffect(uiState.saveCompleted) {
        if (uiState.saveCompleted) {
            Timber.i("Alarm saved, navigating up")
            navController.navigateUp()
        }
    }

    Scaffold(
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
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onAction(AlarmEditAction.OnSave) },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(8.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.common_save),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
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
                    onAction = viewModel::onAction
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = uiState.hour,
            initialMinute = uiState.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.onAction(AlarmEditAction.OnTimeChanged(hour, minute))
                showTimePicker = false
            }
        )
    }

    if (uiState.showPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(AlarmEditAction.OnPermissionDialogDismissed)
            },
            title = { Text(stringResource(R.string.exact_alarm_permission_title)) },
            text = { Text(stringResource(R.string.exact_alarm_permission_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                        viewModel.onAction(AlarmEditAction.OnPermissionDialogDismissed)
                    }
                ) {
                    Text(stringResource(R.string.go_to_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(AlarmEditAction.OnPermissionDialogDismissed)
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (uiState.showFullScreenIntentDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(AlarmEditAction.OnFullScreenIntentDialogDismissed)
            },
            title = { Text(stringResource(R.string.fsi_permission_title)) },
            text = { Text(stringResource(R.string.fsi_permission_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        FullScreenIntentPermissionHelper.openSettings(context)
                        viewModel.onAction(AlarmEditAction.OnFullScreenIntentDialogDismissed)
                    }
                ) {
                    Text(stringResource(R.string.fsi_permission_grant))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(AlarmEditAction.OnFullScreenIntentDialogDismissed)
                    }
                ) {
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Time picker area
        TimeDisplay(
            hour = uiState.hour,
            minute = uiState.minute,
            onClick = onTimeClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Repeat days
        SectionTitle(title = stringResource(R.string.alarm_edit_section_repeat))
        Spacer(modifier = Modifier.height(8.dp))
        DayOfWeekSelector(
            selectedDays = uiState.repeatDays,
            excludeHolidays = uiState.excludeHolidays,
            onDaysChanged = { days ->
                onAction(AlarmEditAction.OnRepeatDaysChanged(days))
            },
            onExcludeHolidaysChanged = { exclude ->
                onAction(AlarmEditAction.OnExcludeHolidaysChanged(exclude))
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Playlist selector
        SectionTitle(title = stringResource(R.string.alarm_edit_section_playlist))
        Spacer(modifier = Modifier.height(8.dp))
        PlaylistSelector(
            playlists = uiState.playlists,
            selectedPlaylistId = uiState.playlistId,
            onPlaylistSelected = { playlistId ->
                onAction(AlarmEditAction.OnPlaylistSelected(playlistId))
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Label input
        SectionTitle(title = stringResource(R.string.alarm_edit_section_label))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.label,
            onValueChange = { onAction(AlarmEditAction.OnLabelChanged(it)) },
            placeholder = { Text(stringResource(R.string.alarm_edit_label_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Auto-stop dropdown
        SectionTitle(title = stringResource(R.string.alarm_edit_section_auto_stop))
        Spacer(modifier = Modifier.height(8.dp))
        AutoStopDropdown(
            selectedAutoStop = uiState.autoStop,
            onSelectionChange = { autoStop ->
                onAction(AlarmEditAction.OnAutoStopChanged(autoStop))
            }
        )

        // Error message
        if (uiState.errorMessageResId != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(uiState.errorMessageResId),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Save button
        Button(
            onClick = { onAction(AlarmEditAction.OnSave) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.alarm_edit_save_button))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TimeDisplay(
    hour: Int,
    minute: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%02d:%02d", hour, minute),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
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
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
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
