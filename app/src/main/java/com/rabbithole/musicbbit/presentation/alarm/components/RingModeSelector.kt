package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.domain.model.AlarmRingMode

/**
 * Radio group for choosing how the alarm surfaces when it fires.
 *
 * @param selectedMode Currently selected ring mode
 * @param onModeChanged Callback when the user selects a different mode
 */
@Composable
fun RingModeSelector(
    selectedMode: AlarmRingMode,
    onModeChanged: (AlarmRingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        RingModeOption(
            title = stringResource(R.string.alarm_edit_ring_mode_normal),
            summary = stringResource(R.string.alarm_edit_ring_mode_normal_summary),
            selected = selectedMode == AlarmRingMode.Normal,
            onClick = { onModeChanged(AlarmRingMode.Normal) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        RingModeOption(
            title = stringResource(R.string.alarm_edit_ring_mode_full_screen),
            summary = stringResource(R.string.alarm_edit_ring_mode_full_screen_summary),
            selected = selectedMode == AlarmRingMode.FullScreen,
            onClick = { onModeChanged(AlarmRingMode.FullScreen) }
        )
    }
}

@Composable
private fun RingModeOption(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
