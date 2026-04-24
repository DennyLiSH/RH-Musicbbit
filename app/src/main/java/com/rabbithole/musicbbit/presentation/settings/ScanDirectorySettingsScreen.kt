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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.domain.model.ThemeMode
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
                        "External storage devices are not supported. Please select a folder from internal storage.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is TreeUriPathResult.ParseFailed -> {
                    Toast.makeText(
                        context,
                        "Failed to parse folder path. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
    onThemeModeChange: (ThemeMode) -> Unit,
    onAddDirectory: () -> Unit,
    onRemoveDirectory: (Long) -> Unit,
    onConfirmDirectory: () -> Unit,
    onCancelDirectory: () -> Unit
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

            item(key = "scan_directory_header") {
                Text(
                    text = "Scan Directories",
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
                Text("Add Scan Directory")
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
            error = state.addError,
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
            text = "Theme",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeButton(
                label = "System",
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ThemeModeButton(
                label = "Light",
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeModeButton(
                label = "Dark",
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
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Directory") },
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
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun buildStatusText(state: ScanDirectorySettingsUiState.Success): String {
    val parts = mutableListOf<String>()
    if (state.lastScanTime != null) {
        parts.add("Last scan: ${state.lastScanTime}")
    }
    parts.add("${state.directoryCount} directories")
    return parts.joinToString(" · ")
}
