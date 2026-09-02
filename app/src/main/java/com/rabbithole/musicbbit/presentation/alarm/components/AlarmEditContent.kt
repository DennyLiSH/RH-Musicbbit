package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.AlarmRingMode
import com.rabbithole.musicbbit.domain.model.AutoStop
import com.rabbithole.musicbbit.presentation.alarm.AlarmEditAction
import com.rabbithole.musicbbit.presentation.alarm.AlarmEditUiState
import java.time.DayOfWeek

@Composable
internal fun AlarmEditContent(
    uiState: AlarmEditUiState,
    onTimeClick: () -> Unit,
    onAction: (AlarmEditAction) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRequestDndAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val form = uiState.form
    // Memoize action-mapping lambdas so they don't reallocate on every recomposition,
    // keeping child components stable. Remember chain must start from the parent
    // (AlarmEditScreen.onActionWithTracking is also remembered).
    val onRepeatDaysChanged = remember(onAction) {
        { days: Set<DayOfWeek> -> onAction(AlarmEditAction.OnRepeatDaysChanged(days)) }
    }
    val onExcludeHolidaysChanged = remember(onAction) {
        { exclude: Boolean -> onAction(AlarmEditAction.OnExcludeHolidaysChanged(exclude)) }
    }
    val onPlaylistSelected = remember(onAction) {
        { playlistId: Long -> onAction(AlarmEditAction.OnPlaylistSelected(playlistId)) }
    }
    val onResumePlaybackChanged = remember(onAction) {
        { resume: Boolean -> onAction(AlarmEditAction.OnResumePlaybackChanged(resume)) }
    }
    val onLabelChanged = remember(onAction) {
        { label: String -> onAction(AlarmEditAction.OnLabelChanged(label)) }
    }
    val onRingModeChanged = remember(onAction) {
        { mode: AlarmRingMode -> onAction(AlarmEditAction.OnRingModeChanged(mode)) }
    }
    val onIgnoreQuietModeChanged = remember(onAction) {
        { ignore: Boolean -> onAction(AlarmEditAction.OnIgnoreQuietModeChanged(ignore)) }
    }
    val onAutoStopChanged = remember(onAction) {
        { autoStop: AutoStop? -> onAction(AlarmEditAction.OnAutoStopChanged(autoStop)) }
    }
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
                onDaysChanged = onRepeatDaysChanged,
                onExcludeHolidaysChanged = onExcludeHolidaysChanged
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(title = stringResource(R.string.alarm_edit_section_playlist))
            PlaylistSelector(
                playlists = uiState.playlists,
                selectedPlaylistId = form.playlistId,
                isLoading = uiState.playlistsLoading,
                isError = uiState.errorMessageResId == R.string.alarm_edit_error_select_playlist,
                onPlaylistSelected = onPlaylistSelected,
                onCreatePlaylist = onCreatePlaylist
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(title = stringResource(R.string.alarm_edit_section_resume_playback))
            ResumePlaybackSwitch(
                checked = form.resumePlayback,
                onCheckedChange = onResumePlaybackChanged
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        CollapsibleSettingsGroup(
            title = stringResource(R.string.alarm_edit_section_advanced)
        ) {
            SectionTitle(title = stringResource(R.string.alarm_edit_section_label))
            OutlinedTextField(
                value = form.label,
                onValueChange = onLabelChanged,
                placeholder = { Text(stringResource(R.string.alarm_edit_label_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(title = stringResource(R.string.alarm_edit_section_ring_mode))
            RingModeSelector(
                selectedMode = form.ringMode,
                onModeChanged = onRingModeChanged
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(title = stringResource(R.string.alarm_edit_section_ignore_quiet_mode))
            IgnoreQuietModeSwitch(
                checked = form.ignoreQuietMode,
                onCheckedChange = onIgnoreQuietModeChanged,
                showDndAccessHint = !uiState.isDndAccessGranted,
                onRequestDndAccess = onRequestDndAccess
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(title = stringResource(R.string.alarm_edit_section_auto_stop))
            AutoStopDropdown(
                selectedAutoStop = form.autoStop,
                onSelectionChange = onAutoStopChanged
            )

            Spacer(modifier = Modifier.height(8.dp))

            VolumeRampHint(
                durationSeconds = uiState.volumeRampDurationSeconds
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
