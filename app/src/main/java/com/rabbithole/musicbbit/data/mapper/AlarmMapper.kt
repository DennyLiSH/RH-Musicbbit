package com.rabbithole.musicbbit.data.mapper

import com.rabbithole.musicbbit.data.model.AlarmEntity
import com.rabbithole.musicbbit.data.model.AutoStopConverter
import com.rabbithole.musicbbit.domain.model.Alarm
import java.time.DayOfWeek

internal fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    repeatDays = repeatDaysBitmask.toDayOfWeekSet(),
    excludeHolidays = excludeHolidays,
    playlistId = playlistId,
    isEnabled = isEnabled,
    label = label,
    autoStop = AutoStopConverter.toAutoStop(autoStop),
    lastTriggeredAt = lastTriggeredAt
)

internal fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    repeatDaysBitmask = repeatDays.toBitmask(),
    excludeHolidays = excludeHolidays,
    playlistId = playlistId,
    isEnabled = isEnabled,
    label = label,
    autoStop = AutoStopConverter.fromAutoStop(autoStop),
    lastTriggeredAt = lastTriggeredAt
)

private fun Set<DayOfWeek>.toBitmask(): Int {
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

private fun Int.toDayOfWeekSet(): Set<DayOfWeek> {
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
