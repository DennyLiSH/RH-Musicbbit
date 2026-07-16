package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.presentation.alarm.AlarmEditAction
import com.rabbithole.musicbbit.presentation.alarm.AlarmEditUiState

@Composable
internal fun AlarmEditContent(
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
