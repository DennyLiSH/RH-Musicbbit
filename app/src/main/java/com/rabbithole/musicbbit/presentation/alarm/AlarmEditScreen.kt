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
import com.rabbithole.musicbbit.presentation.alarm.components.DayOfWeekSelector
import com.rabbithole.musicbbit.presentation.alarm.components.PlaylistSelector
import com.rabbithole.musicbbit.presentation.alarm.components.TimePickerDialog
import timber.log.Timber

private val AUTO_STOP_OPTIONS = listOf(
    null to "Do not auto-stop",
    5 to "5 minutes",
    10 to "10 minutes",
    15 to "15 minutes",
    30 to "30 minutes",
    60 to "60 minutes"
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
                    val titleText = when {
                        uiState.isLoading -> "Alarm"
                        uiState.isNewAlarm -> "New Alarm"
                        else -> "Edit Alarm"
                    }
                    Text(titleText)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                                text = "Save",
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
                    Text(stringResource(R.string.cancel))
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
        SectionTitle(title = "Repeat")
        Spacer(modifier = Modifier.height(8.dp))
        DayOfWeekSelector(
            selectedDays = uiState.repeatDays,
            onDaysChanged = { days ->
                onAction(AlarmEditAction.OnRepeatDaysChanged(days))
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Playlist selector
        SectionTitle(title = "Playlist")
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
        SectionTitle(title = "Label")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.label,
            onValueChange = { onAction(AlarmEditAction.OnLabelChanged(it)) },
            placeholder = { Text("Optional label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Auto-stop dropdown
        SectionTitle(title = "Auto-stop")
        Spacer(modifier = Modifier.height(8.dp))
        AutoStopDropdown(
            selectedMinutes = uiState.autoStopMinutes,
            onSelectionChange = { minutes ->
                onAction(AlarmEditAction.OnAutoStopChanged(minutes))
            }
        )

        // Error message
        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.errorMessage,
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
                Text("Save Alarm")
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
                text = "Tap to change time",
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
    selectedMinutes: Int?,
    onSelectionChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = AUTO_STOP_OPTIONS.find { it.first == selectedMinutes }?.second
        ?: "Do not auto-stop"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Auto-stop after") },
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
            AUTO_STOP_OPTIONS.forEach { (minutes, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelectionChange(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}
