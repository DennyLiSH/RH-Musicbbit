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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.rabbithole.musicbbit.presentation.components.SingleChoiceDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    alarmRingSettingsViewModel: AlarmRingSettingsViewModel = hiltViewModel()
) {
    val themeUiState by themeViewModel.uiState.collectAsStateWithLifecycle()
    val alarmRingUiState by alarmRingSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(
                title = stringResource(R.string.settings_appearance)
            ) {
                ThemeDropdown(
                    themeMode = themeUiState.themeMode,
                    onThemeModeChange = { themeViewModel.setThemeMode(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                LanguageDropdown(
                    selectedLanguage = remember { mutableStateOf(LocaleHelper.getCurrentLanguage(context)).value },
                    onLanguageChange = { LocaleHelper.setLanguage(context, it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(
                title = stringResource(R.string.settings_alarm_ring)
            ) {
                VolumeRampDropdown(
                    currentDuration = alarmRingUiState.volumeRampDurationSeconds,
                    onDurationChange = { alarmRingSettingsViewModel.setVolumeRampDuration(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                BreathingLightControls(
                    enabled = alarmRingUiState.breathingEnabled,
                    periodMs = alarmRingUiState.breathingPeriodMs,
                    onEnabledChanged = { alarmRingSettingsViewModel.setBreathingEnabled(it) },
                    onPeriodChanged = { alarmRingSettingsViewModel.setBreathingPeriodMs(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(
                title = stringResource(R.string.settings_management)
            ) {
                SettingsNavCard(
                    title = stringResource(R.string.settings_scan_directories),
                    description = stringResource(R.string.settings_manage_scan_paths),
                    onClick = { navController.navigate(ScanDirectorySettings) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsNavCard(
                    title = stringResource(R.string.settings_permission_diagnostics),
                    description = stringResource(R.string.settings_check_permissions),
                    onClick = { navController.navigate(PermissionDiagnostics) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsNavCard(
                    title = stringResource(R.string.settings_about_title),
                    description = stringResource(R.string.settings_about_description),
                    onClick = { navController.navigate(About) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
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
private fun LanguageDropdown(
    selectedLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceDropdown(
        label = stringResource(R.string.settings_language),
        options = LanguageOptions,
        selectedOption = selectedLanguage,
        optionLabel = { languageLabel(it) },
        onOptionSelect = onLanguageChange,
        modifier = modifier
    )
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
    AppLanguage.CHINESE -> stringResource(R.string.settings_language_zh)
    AppLanguage.ENGLISH -> stringResource(R.string.settings_language_en)
    AppLanguage.JAPANESE -> stringResource(R.string.settings_language_ja)
}

@Composable
private fun VolumeRampDropdown(
    currentDuration: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceDropdown(
        label = stringResource(R.string.settings_volume_ramp_duration),
        options = VolumeRampPresets,
        selectedOption = currentDuration,
        optionLabel = {
            if (it == 0) stringResource(R.string.settings_volume_ramp_disabled)
            else stringResource(R.string.settings_volume_ramp_seconds, it)
        },
        onOptionSelect = onDurationChange,
        modifier = modifier
    )
}

@Composable
private fun ThemeDropdown(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceDropdown(
        label = stringResource(R.string.settings_theme),
        options = ThemeOptions,
        selectedOption = themeMode,
        optionLabel = { themeLabel(it) },
        onOptionSelect = onThemeModeChange,
        modifier = modifier
    )
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
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

@Composable
private fun BreathingLightControls(
    enabled: Boolean,
    periodMs: Long,
    onEnabledChanged: (Boolean) -> Unit,
    onPeriodChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = enabled,
                        role = Role.Switch,
                        onValueChange = onEnabledChanged
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_breathing_light),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = null
                )
            }
            Text(
                text = stringResource(R.string.settings_breathing_period, periodMs / 1000f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = periodMs.toFloat(),
                onValueChange = { onPeriodChanged(it.toLong()) },
                valueRange = 1500f..6000f,
                steps = 8,
                enabled = enabled,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
