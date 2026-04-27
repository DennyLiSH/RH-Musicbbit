package com.rabbithole.musicbbit.presentation.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.navigation.PermissionDiagnostics
import com.rabbithole.musicbbit.presentation.settings.components.ScanDirectoryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDirectorySettingsScreen(
    navController: NavController,
    viewModel: ScanDirectorySettingsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val externalStorageMessage = stringResource(R.string.settings_toast_external_storage)
    val parseFailedMessage = stringResource(R.string.settings_toast_parse_failed)

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            when (val result = context.getPathFromTreeUri(it)) {
                is TreeUriPathResult.Success -> {
                    val name = java.io.File(result.path).name
                    viewModel.onAction(ScanDirectorySettingsAction.OnScanDirectoryPreview(result.path, name))
                }
                is TreeUriPathResult.UnsupportedStorage -> {
                    Toast.makeText(
                        context,
                        externalStorageMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is TreeUriPathResult.ParseFailed -> {
                    Toast.makeText(
                        context,
                        parseFailedMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
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
            when (val state = uiState) {
                is ScanDirectorySettingsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ScanDirectorySettingsUiState.Success -> {
                    SuccessContent(
                        state = state,
                        themeMode = themeMode,
                        navController = navController,
                        onThemeModeChange = { mode ->
                            themeViewModel.setThemeMode(mode)
                        },
                        onAddDirectory = { treeLauncher.launch(null) },
                        onRemoveDirectory = { id ->
                            viewModel.onAction(ScanDirectorySettingsAction.OnRemoveDirectory(id))
                        },
                        onConfirmDirectory = {
                            viewModel.onAction(ScanDirectorySettingsAction.OnConfirmAddDirectory)
                        },
                        onCancelDirectory = {
                            viewModel.onAction(ScanDirectorySettingsAction.OnCancelDirectoryPreview)
                        },
                        onBreathingEnabledChanged = { enabled ->
                            viewModel.onAction(ScanDirectorySettingsAction.OnBreathingEnabledChanged(enabled))
                        },
                        onBreathingPeriodChanged = { periodMs ->
                            viewModel.onAction(ScanDirectorySettingsAction.OnBreathingPeriodChanged(periodMs))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessContent(
    state: ScanDirectorySettingsUiState.Success,
    themeMode: ThemeMode,
    navController: NavController,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAddDirectory: () -> Unit,
    onRemoveDirectory: (Long) -> Unit,
    onConfirmDirectory: () -> Unit,
    onCancelDirectory: () -> Unit,
    onBreathingEnabledChanged: (Boolean) -> Unit,
    onBreathingPeriodChanged: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            item(key = "theme_settings") {
                ThemeSettingsSection(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item(key = "breathing_settings") {
                BreathingSettingsSection(
                    enabled = state.breathingEnabled,
                    periodMs = state.breathingPeriodMs,
                    onEnabledChanged = onBreathingEnabledChanged,
                    onPeriodChanged = onBreathingPeriodChanged,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item(key = "permission_diagnostics") {
                PermissionDiagnosticsCard(
                    onClick = { navController.navigate(PermissionDiagnostics) },
                    modifier = Modifier.padding(16.dp)
                )
            }

            item(key = "scan_directory_header") {
                Text(
                    text = stringResource(R.string.settings_scan_directories),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(
                items = state.directories,
                key = { it.id }
            ) { directory ->
                ScanDirectoryItem(
                    directory = directory,
                    onRemove = { onRemoveDirectory(directory.id) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            FilledTonalButton(
                onClick = onAddDirectory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.settings_add_scan_directory))
            }

            if (state.lastScanTime != null || state.directoryCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildStatusText(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    if (state.pendingDirectory != null) {
        ConfirmAddDirectoryDialog(
            directory = state.pendingDirectory,
            errorResId = state.addErrorResId,
            onConfirm = onConfirmDirectory,
            onDismiss = onCancelDirectory
        )
    }
}

@Composable
private fun ThemeSettingsSection(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeButton(
                label = stringResource(R.string.settings_theme_system),
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ThemeModeButton(
                label = stringResource(R.string.settings_theme_light),
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeModeButton(
                label = stringResource(R.string.settings_theme_dark),
                selected = themeMode == ThemeMode.DARK,
                onClick = { onThemeModeChange(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label)
        }
    }
}

@Composable
private fun ConfirmAddDirectoryDialog(
    directory: PendingDirectory,
    errorResId: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_add_directory_dialog_title)) },
        text = {
            Column {
                Text(
                    text = directory.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = directory.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (errorResId != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(errorResId),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_add_directory_confirm))
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
private fun BreathingSettingsSection(
    enabled: Boolean,
    periodMs: Long,
    onEnabledChanged: (Boolean) -> Unit,
    onPeriodChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_alarm_ring),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_breathing_light),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        val alpha = if (enabled) 1.0f else 0.5f
        Text(
            text = stringResource(R.string.settings_breathing_period, periodMs / 1000f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        )
        Slider(
            value = periodMs.toFloat(),
            onValueChange = { onPeriodChanged(it.toLong()) },
            valueRange = 1500f..6000f,
            steps = 8,
            enabled = enabled,
            modifier = Modifier.alpha(alpha)
        )
    }
}

@Composable
private fun buildStatusText(state: ScanDirectorySettingsUiState.Success): String {
    val parts = mutableListOf<String>()
    if (state.lastScanTime != null) {
        parts.add(stringResource(R.string.settings_last_scan, state.lastScanTime))
    }
    parts.add(stringResource(R.string.settings_directory_count, state.directoryCount))
    return parts.joinToString(" · ")
}

@Composable
private fun PermissionDiagnosticsCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_permission_diagnostics),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_check_permissions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
