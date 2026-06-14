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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
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

            // Alarm ring section
            AlarmRingSettingsSection(
                alarmRingUiState = alarmRingUiState,
                onVolumeRampChange = { alarmRingSettingsViewModel.setVolumeRampDuration(it) },
                onBreathingEnabledChanged = { alarmRingSettingsViewModel.setBreathingEnabled(it) },
                onBreathingPeriodChanged = { alarmRingSettingsViewModel.setBreathingPeriodMs(it) }
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
    ThemeDropdown(
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        modifier = modifier
    )
}

private val VolumeRampPresets = listOf(0, 5, 10, 15, 30, 60)

private val ThemeOptions = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

// Order intentionally differs from AppLanguage.entries (enum defines ENGLISH before CHINESE;
// UI historically shows CHINESE before ENGLISH).
private val LanguageOptions = listOf(
    AppLanguage.SYSTEM,
    AppLanguage.CHINESE,
    AppLanguage.ENGLISH,
    AppLanguage.JAPANESE
)

@Composable
private fun LanguageSettingsSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLanguage = remember { LocaleHelper.getCurrentLanguage(context) }
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }

    LanguageDropdown(
        selectedLanguage = selectedLanguage,
        onLanguageChange = {
            selectedLanguage = it
            LocaleHelper.setLanguage(context as Activity, it)
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selectedLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = languageLabel(selectedLanguage)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            LanguageOptions.forEach { language ->
                DropdownMenuItem(
                    text = { Text(languageLabel(language)) },
                    onClick = {
                        onLanguageChange(language)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
    AppLanguage.CHINESE -> stringResource(R.string.settings_language_zh)
    AppLanguage.ENGLISH -> stringResource(R.string.settings_language_en)
    AppLanguage.JAPANESE -> stringResource(R.string.settings_language_ja)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRampDropdown(
    currentDuration: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when (currentDuration) {
        0 -> stringResource(R.string.settings_volume_ramp_disabled)
        else -> stringResource(R.string.settings_volume_ramp_seconds, currentDuration)
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = themeLabel(themeMode)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ThemeOptions.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(themeLabel(mode)) },
                    onClick = {
                        onThemeModeChange(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
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

@Composable
private fun BreathingLightControls(
    enabled: Boolean,
    periodMs: Long,
    onEnabledChanged: (Boolean) -> Unit,
    onPeriodChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
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
private fun AlarmRingSettingsSection(
    alarmRingUiState: AlarmRingSettingsViewModel.AlarmRingSettingsUiState,
    onVolumeRampChange: (Int) -> Unit,
    onBreathingEnabledChanged: (Boolean) -> Unit,
    onBreathingPeriodChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_alarm_ring),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        VolumeRampDropdown(
            currentDuration = alarmRingUiState.volumeRampDurationSeconds,
            onDurationChange = onVolumeRampChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        BreathingLightControls(
            enabled = alarmRingUiState.breathingEnabled,
            periodMs = alarmRingUiState.breathingPeriodMs,
            onEnabledChanged = onBreathingEnabledChanged,
            onPeriodChanged = onBreathingPeriodChanged
        )
    }
}
