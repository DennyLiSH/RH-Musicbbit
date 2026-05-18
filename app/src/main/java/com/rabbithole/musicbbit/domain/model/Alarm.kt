package com.rabbithole.musicbbit.domain.model

import java.time.DayOfWeek

/**
 * Domain model representing an alarm that triggers music playback at a scheduled time.
 *
 * @property id Unique identifier (0 for new alarms)
 * @property hour Hour of day in 24-hour format (0-23)
 * @property minute Minute of hour (0-59)
 * @property repeatDays Set of days on which the alarm repeats. Empty set means one-time alarm.
 * @property excludeHolidays Whether to skip this alarm on public holidays
 * @property playlistId ID of the playlist to play when the alarm triggers
 * @property isEnabled Whether the alarm is currently active
 * @property label Optional user-defined label for the alarm
 * @property autoStop Optional auto-stop configuration. Null means no auto-stop.
 * @property lastTriggeredAt Unix timestamp (ms) of the last trigger, or null
 */
data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<DayOfWeek>,
    val excludeHolidays: Boolean = false,
    val playlistId: Long,
    val isEnabled: Boolean,
    val label: String?,
    val autoStop: AutoStop?,
    val lastTriggeredAt: Long?
)

