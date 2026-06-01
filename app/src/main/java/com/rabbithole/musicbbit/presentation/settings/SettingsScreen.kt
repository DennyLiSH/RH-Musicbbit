package com.rabbithole.musicbbit.presentation.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rabbithole.musicbbit.LocaleHelper
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.navigation.About
import com.rabbithole.musicbbit.navigation.PermissionDiagnostics
import com.rabbithole.musicbbit.navigation.ScanDirectorySettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    alarmRingSettingsViewModel: AlarmRingSettingsViewModel = hiltViewModel()
) {
    val themeUiState by themeViewModel.uiState.collectAsStateWithLifecycle()
    val alarmRingUiState by alarmRingSettingsViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Theme section
            ThemeSettingsSection(
                themeMode = themeUiState.themeMode,
                onThemeModeChange = { themeViewModel.setThemeMode(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Language section
            LanguageSettingsSection()

            Spacer(modifier = Modifier.height(16.dp))

            // Alarm playback section
            VolumeRampSection(
                currentDuration = alarmRingUiState.volumeRampDurationSeconds,
                onDurationChange = { alarmRingSettingsViewModel.setVolumeRampDuration(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scan Directories card
            SettingsNavCard(
                title = stringResource(R.string.settings_scan_directories),
                description = stringResource(R.string.settings_manage_scan_paths),
                onClick = { navController.navigate(ScanDirectorySettings) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Permission Diagnostics card
            SettingsNavCard(
                title = stringResource(R.string.settings_permission_diagnostics),
                description = stringResource(R.string.settings_check_permissions),
                onClick = { navController.navigate(PermissionDiagnostics) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // About card
            SettingsNavCard(
                title = stringResource(R.string.settings_about_title),
                description = stringResource(R.string.settings_about_description),
                onClick = { navController.navigate(About) }
            )
        }
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

private val VolumeRampPresets = listOf(0, 5, 10, 15, 30, 60)

@Composable
private fun LanguageSettingsSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLanguage = remember { LocaleHelper.getCurrentLanguage(context) }
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageButton(
                label = stringResource(R.string.settings_language_system),
                selected = selectedLanguage == AppLanguage.SYSTEM,
                onClick = {
                    selectedLanguage = AppLanguage.SYSTEM
                    LocaleHelper.setLanguage(context as Activity, AppLanguage.SYSTEM)
                },
                modifier = Modifier.weight(1f)
            )
            LanguageButton(
                label = stringResource(R.string.settings_language_zh),
                selected = selectedLanguage == AppLanguage.CHINESE,
                onClick = {
                    selectedLanguage = AppLanguage.CHINESE
                    LocaleHelper.setLanguage(context as Activity, AppLanguage.CHINESE)
                },
                modifier = Modifier.weight(1f)
            )
            LanguageButton(
                label = stringResource(R.string.settings_language_en),
                selected = selectedLanguage == AppLanguage.ENGLISH,
                onClick = {
                    selectedLanguage = AppLanguage.ENGLISH
                    LocaleHelper.setLanguage(context as Activity, AppLanguage.ENGLISH)
                },
                modifier = Modifier.weight(1f)
            )
            LanguageButton(
                label = stringResource(R.string.settings_language_ja),
                selected = selectedLanguage == AppLanguage.JAPANESE,
                onClick = {
                    selectedLanguage = AppLanguage.JAPANESE
                    LocaleHelper.setLanguage(context as Activity, AppLanguage.JAPANESE)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LanguageButton(
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
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRampSection(
    currentDuration: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_alarm_playback),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        var expanded by remember { mutableStateOf(false) }
        val selectedLabel = when (currentDuration) {
            0 -> stringResource(R.string.settings_volume_ramp_disabled)
            else -> stringResource(R.string.settings_volume_ramp_seconds, currentDuration)
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_volume_ramp_duration)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                VolumeRampPresets.forEach { seconds ->
                    val label = when (seconds) {
                        0 -> stringResource(R.string.settings_volume_ramp_disabled)
                        else -> stringResource(R.string.settings_volume_ramp_seconds, seconds)
                    }
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onDurationChange(seconds)
                            expanded = false
                        }
                    )
                }
            }
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
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsNavCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
