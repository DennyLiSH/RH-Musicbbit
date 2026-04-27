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
 * @property autoStopMinutes Optional duration in minutes after which playback auto-stops. Null means no auto-stop.
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
    val autoStopMinutes: Int?,
    val lastTriggeredAt: Long?
)

/**
 * Converts a set of [DayOfWeek] to an integer bitmask.
 * Monday    -> bit 0
 * Tuesday   -> bit 1
 * Wednesday -> bit 2
 * Thursday  -> bit 3
 * Friday    -> bit 4
 * Saturday  -> bit 5
 * Sunday    -> bit 6
 */
fun Set<DayOfWeek>.toBitmask(): Int {
    var bitmask = 0
    for (day in this) {
        val bit = when (day) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
        }
        bitmask = bitmask or (1 shl bit)
    }
    return bitmask
}

/**
 * Converts an integer bitmask to a set of [DayOfWeek].
 * Bit 0 -> Monday, bit 1 -> Tuesday, ..., bit 6 -> Sunday.
 */
fun Int.toDayOfWeekSet(): Set<DayOfWeek> {
    val days = mutableSetOf<DayOfWeek>()
    if (this and (1 shl 0) != 0) days.add(DayOfWeek.MONDAY)
    if (this and (1 shl 1) != 0) days.add(DayOfWeek.TUESDAY)
    if (this and (1 shl 2) != 0) days.add(DayOfWeek.WEDNESDAY)
    if (this and (1 shl 3) != 0) days.add(DayOfWeek.THURSDAY)
    if (this and (1 shl 4) != 0) days.add(DayOfWeek.FRIDAY)
    if (this and (1 shl 5) != 0) days.add(DayOfWeek.SATURDAY)
    if (this and (1 shl 6) != 0) days.add(DayOfWeek.SUNDAY)
    return days
}
