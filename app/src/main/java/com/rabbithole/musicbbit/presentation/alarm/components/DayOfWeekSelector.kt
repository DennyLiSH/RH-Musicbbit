package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rabbithole.musicbbit.R
import java.time.DayOfWeek

private val ALL_DAYS = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY
)

@StringRes
private fun dayShortLabelRes(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.alarm_day_short_mon
    DayOfWeek.TUESDAY -> R.string.alarm_day_short_tue
    DayOfWeek.WEDNESDAY -> R.string.alarm_day_short_wed
    DayOfWeek.THURSDAY -> R.string.alarm_day_short_thu
    DayOfWeek.FRIDAY -> R.string.alarm_day_short_fri
    DayOfWeek.SATURDAY -> R.string.alarm_day_short_sat
    DayOfWeek.SUNDAY -> R.string.alarm_day_short_sun
}

@StringRes
private fun dayFullNameRes(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.alarm_monday
    DayOfWeek.TUESDAY -> R.string.alarm_tuesday
    DayOfWeek.WEDNESDAY -> R.string.alarm_wednesday
    DayOfWeek.THURSDAY -> R.string.alarm_thursday
    DayOfWeek.FRIDAY -> R.string.alarm_friday
    DayOfWeek.SATURDAY -> R.string.alarm_saturday
    DayOfWeek.SUNDAY -> R.string.alarm_sunday
}

private val WEEKDAYS = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)

private val EVERYDAY = ALL_DAYS.toSet()

/**
 * A component for selecting days of the week for alarm repetition.
 * Displays 7 circular day buttons, quick-select shortcuts, and an optional
 * "excluding holidays" checkbox.
 *
 * Shortcuts only change the day set; `excludeHolidays` is owned exclusively by
 * the checkbox so a preset never silently overwrites the user's holiday choice.
 *
 * @param selectedDays Currently selected days
 * @param excludeHolidays Whether to skip statutory holidays and weekends
 * @param onDaysChanged Callback when day selection changes
 * @param onExcludeHolidaysChanged Callback when exclude-holidays toggle changes
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayOfWeekSelector(
    selectedDays: Set<DayOfWeek>,
    excludeHolidays: Boolean,
    onDaysChanged: (Set<DayOfWeek>) -> Unit,
    onExcludeHolidaysChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOneTime = selectedDays.isEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        // Day of week circular buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ALL_DAYS.forEach { day ->
                val isSelected = day in selectedDays
                DayButton(
                    day = day,
                    isSelected = isSelected,
                    onClick = {
                        val newDays = if (isSelected) {
                            selectedDays - day
                        } else {
                            selectedDays + day
                        }
                        onDaysChanged(newDays)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick select shortcuts — FlowRow keeps buttons reachable at large font scales
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            ShortcutButton(
                label = stringResource(R.string.alarm_edit_repeat_daily),
                isSelected = selectedDays == EVERYDAY,
                onClick = { onDaysChanged(EVERYDAY) }
            )
            ShortcutButton(
                label = stringResource(R.string.alarm_edit_repeat_weekdays),
                isSelected = selectedDays == WEEKDAYS,
                onClick = { onDaysChanged(WEEKDAYS) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Exclude holidays checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = excludeHolidays,
                    onValueChange = onExcludeHolidaysChanged,
                    role = Role.Checkbox,
                    enabled = !isOneTime
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = excludeHolidays,
                onCheckedChange = null,
                enabled = !isOneTime
            )
            Text(
                text = stringResource(R.string.alarm_excluding_holidays),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
                color = if (isOneTime) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun DayButton(
    day: DayOfWeek,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dayName = stringResource(dayFullNameRes(day))

    TextButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = dayName
                selected = isSelected
            },
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = stringResource(dayShortLabelRes(day)),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun ShortcutButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    TextButton(
        onClick = onClick,
        modifier = modifier.semantics { selected = isSelected },
        colors = ButtonDefaults.textButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
