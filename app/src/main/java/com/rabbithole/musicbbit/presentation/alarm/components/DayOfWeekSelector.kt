package com.rabbithole.musicbbit.presentation.alarm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

private val DAY_LABELS = mapOf(
    DayOfWeek.MONDAY to "M",
    DayOfWeek.TUESDAY to "T",
    DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY to "T",
    DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "S"
)

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
 * @param selectedDays Currently selected days
 * @param excludeHolidays Whether to skip statutory holidays and weekends
 * @param onDaysChanged Callback when day selection changes
 * @param onExcludeHolidaysChanged Callback when exclude-holidays toggle changes
 */
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

        // Quick select shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            ShortcutButton(
                label = "Daily",
                selected = selectedDays == EVERYDAY && !excludeHolidays,
                onClick = {
                    onDaysChanged(EVERYDAY)
                    onExcludeHolidaysChanged(false)
                }
            )
            ShortcutButton(
                label = "Weekdays",
                selected = selectedDays == WEEKDAYS && !excludeHolidays,
                onClick = {
                    onDaysChanged(WEEKDAYS)
                    onExcludeHolidaysChanged(false)
                }
            )
            ShortcutButton(
                label = "Excl. Holidays",
                selected = selectedDays == EVERYDAY && excludeHolidays,
                onClick = {
                    onDaysChanged(EVERYDAY)
                    onExcludeHolidaysChanged(true)
                }
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
                text = "Exclude holidays",
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

    TextButton(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = DAY_LABELS[day] ?: "",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ShortcutButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    TextButton(
        onClick = onClick,
        modifier = modifier,
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
